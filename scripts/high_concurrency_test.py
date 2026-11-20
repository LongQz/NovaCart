"""Concurrency verification for NovaCart inventory and idempotency paths."""

import argparse
import json
import statistics
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path


def call(base, method, path, token=None, body=None):
    """Call a NovaCart endpoint and return status, payload, and elapsed milliseconds."""
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(base + path, data=data, method=method, headers=headers)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = json.loads(response.read().decode("utf-8"))
            status = response.status
    except urllib.error.HTTPError as error:
        status = error.code
        payload = json.loads(error.read().decode("utf-8"))
    return status, payload, (time.perf_counter() - started) * 1000


def require_success(base, method, path, token=None, body=None):
    """Call an endpoint and return its data, raising on application failure."""
    status, payload, _ = call(base, method, path, token, body)
    if status >= 400 or (isinstance(payload, dict) and payload.get("success") is False):
        raise RuntimeError(payload.get("message") or f"HTTP {status}")
    return payload.get("data") if isinstance(payload, dict) and "data" in payload else payload


def make_order(mall, token, product_id, request_key, note):
    """Create one DELIVERY order payload for concurrency tests."""
    return {
        "userId": 1,
        "bizType": "DELIVERY",
        "recipient": "并发测试",
        "phone": "13800000001",
        "address": "重庆邮电大学 24 栋",
        "note": note,
        "idempotencyKey": request_key,
        "items": [{"productId": product_id, "quantity": 1}],
    }


def set_stock(admin, admin_token, product_id, stock):
    """Update stock through the real admin API so Redis cache is synchronized."""
    require_success(admin, "PUT", f"/api/admin/products/{product_id}", admin_token, {
        "stock": stock,
        "active": True,
    })


def main():
    """Run inventory contention, idempotency, callback and timeout checks."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--mall", default="http://127.0.0.1:8090")
    parser.add_argument("--admin", default="http://127.0.0.1:8092")
    parser.add_argument("--stock", type=int, default=40)
    parser.add_argument("--requests", type=int, default=80)
    parser.add_argument("--product-id", type=int, default=0)
    parser.add_argument("--timeout-wait", type=int, default=15)
    parser.add_argument("--output", default="docs/concurrency-test-result.json")
    args = parser.parse_args()

    user = require_success(args.mall, "POST", "/api/auth/login", body={
        "username": "demo",
        "password": "123456",
    })
    admin = require_success(args.mall, "POST", "/api/auth/login", body={
        "username": "developer",
        "password": "admin123",
    })

    products = require_success(args.admin, "GET", "/api/admin/products", admin["token"])
    product = next(
        item for item in products
        if (args.product_id == 0 or int(item["id"]) == args.product_id)
        and item["biz_type"] == "DELIVERY"
    )
    product_id = int(product["id"])
    original_stock = int(product["stock"])
    set_stock(args.admin, admin["token"], product_id, args.stock)

    contention_key_prefix = "stress-" + uuid.uuid4().hex + "-"

    def attempt(index):
        return call(
            args.mall,
            "POST",
            "/api/orders",
            user["token"],
            make_order(
                args.mall,
                user["token"],
                product_id,
                contention_key_prefix + str(index),
                "库存争抢测试",
            ),
        )

    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=max(1, args.requests)) as executor:
        results = list(executor.map(attempt, range(args.requests)))
    wall_seconds = max(0.001, time.perf_counter() - started)

    successful_orders = []
    successful = 0
    stockouts = 0
    latencies = []
    for status, payload, elapsed in results:
        latencies.append(elapsed)
        if status < 400 and payload.get("success"):
            successful += 1
            successful_orders.append(payload["data"]["order"]["id"])
        else:
            message = str(payload.get("message", ""))
            if "库存不足" in message or "并发更新冲突" in message:
                stockouts += 1

    idempotency_key = "idempotency-" + uuid.uuid4().hex
    set_stock(args.admin, admin["token"], product_id, 20)

    def duplicate_attempt(_):
        return call(
            args.mall,
            "POST",
            "/api/orders",
            user["token"],
            make_order(args.mall, user["token"], product_id, idempotency_key, "幂等并发测试"),
        )

    with ThreadPoolExecutor(max_workers=16) as executor:
        duplicate_results = list(executor.map(duplicate_attempt, range(16)))
    duplicate_ids = {
        payload.get("data", {}).get("order", {}).get("id")
        for status, payload, _ in duplicate_results
        if status < 400 and payload.get("success")
    }
    duplicate_ids.discard(None)
    duplicate_order_id = next(iter(duplicate_ids), None)

    callback_order_id = duplicate_order_id
    callback_id = "callback-" + uuid.uuid4().hex

    def callback_attempt(_):
        return call(
            args.mall,
            "POST",
            "/api/orders/payment-callback",
            body={"callbackId": callback_id, "orderId": callback_order_id, "success": True},
        )

    with ThreadPoolExecutor(max_workers=16) as executor:
        callback_results = list(executor.map(callback_attempt, range(16)))
    callback_successes = sum(
        1 for status, payload, _ in callback_results
        if status < 400 and payload.get("success")
    )
    callback_statuses = {
        str(payload.get("data", {}).get("order", {}).get("status"))
        for status, payload, _ in callback_results
        if status < 400 and payload.get("success")
    }

    # Give the timeout test a dedicated unit after the stock-contention round.
    set_stock(args.admin, admin["token"], product_id, 1)
    timeout_order = require_success(
        args.mall,
        "POST",
        "/api/orders",
        user["token"],
        make_order(args.mall, user["token"], product_id, "timeout-" + uuid.uuid4().hex, "延迟关单测试"),
    )
    timeout_order_id = timeout_order["order"]["id"]
    deadline = time.time() + args.timeout_wait
    timeout_status = "PENDING_PAYMENT"
    while time.time() < deadline:
        order = require_success(args.mall, "GET", f"/api/orders/{timeout_order_id}", user["token"])
        timeout_status = order["order"]["status"]
        if timeout_status == "CANCELLED":
            break
        time.sleep(1)

    for order_id in successful_orders:
        call(
            args.mall,
            "POST",
            f"/api/orders/{order_id}/cancel",
            user["token"],
            {"reason": "并发测试清理"},
        )
    if duplicate_order_id:
        call(
            args.mall,
            "POST",
            f"/api/orders/{duplicate_order_id}/cancel",
            user["token"],
            {"reason": "幂等测试清理"},
        )
    set_stock(args.admin, admin["token"], product_id, original_stock)

    ordered = sorted(latencies)
    p95_index = max(0, int(len(ordered) * 0.95) - 1)
    result = {
        "timestamp": datetime.now().isoformat(timespec="seconds"),
        "productId": product_id,
        "initialStock": args.stock,
        "requests": args.requests,
        "successfulOrders": successful,
        "stockoutOrConflictResponses": stockouts,
        "unexpectedFailures": args.requests - successful - stockouts,
        "throughputPerSecond": round(args.requests / wall_seconds, 2),
        "latencyMs": {
            "mean": round(statistics.fmean(latencies), 2),
            "p95": round(ordered[p95_index], 2),
            "max": round(max(latencies), 2),
        },
        "duplicateRequest": {
            "requests": 16,
            "distinctOrderIds": len(duplicate_ids),
            "orderId": duplicate_order_id,
        },
        "paymentCallback": {
            "requests": 16,
            "successResponses": callback_successes,
            "statuses": sorted(callback_statuses),
        },
        "rabbitTimeout": {
            "orderId": timeout_order_id,
            "statusAfterWait": timeout_status,
        },
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
