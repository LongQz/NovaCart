"""Extended NovaCart checks for reviews, admin and cancellation rules."""

import json
import sys
import time
import urllib.error
import urllib.request
import uuid


MALL = "http://127.0.0.1:8090"
DELIVERY = "http://127.0.0.1:8091"
ADMIN = "http://127.0.0.1:8092"


def call(base, method, path, token=None, body=None):
    """Call a JSON endpoint."""
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(base + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        payload = json.loads(error.read().decode("utf-8"))
    if isinstance(payload, dict) and payload.get("success") is False:
        raise RuntimeError(payload.get("message"))
    return payload.get("data") if isinstance(payload, dict) and "data" in payload else payload


def main():
    """Run developer and after-sales smoke checks."""
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    user = call(MALL, "POST", "/api/auth/login", body={"username": "demo", "password": "123456"})
    admin = call(MALL, "POST", "/api/auth/login", body={"username": "developer", "password": "admin123"})

    dashboard = call(ADMIN, "GET", "/api/admin/dashboard", admin["token"])
    mall_product = call(MALL, "GET", "/api/products?type=MALL")[0]
    call(ADMIN, "PUT", f"/api/admin/products/{mall_product['id']}", admin["token"], {
        "stock": 30,
        "active": True,
        "price": mall_product["price"],
    })

    mall_order = call(MALL, "POST", "/api/orders", user["token"], {
        "userId": user["userId"],
        "bizType": "MALL",
        "recipient": "林小满",
        "phone": "13800000001",
        "address": "重庆邮电大学 24 栋",
        "note": "评价测试",
        "idempotencyKey": "ext-review-" + uuid.uuid4().hex,
        "items": [{"productId": mall_product["id"], "quantity": 1}],
    })
    call(MALL, "POST", f"/api/orders/{mall_order['order']['id']}/pay", user["token"])
    review = call(MALL, "POST", "/api/reviews", user["token"], {
        "orderId": mall_order["order"]["id"],
        "rating": 5,
        "content": "商品符合描述，包装完整。",
    })

    food = next(p for p in call(MALL, "GET", "/api/products?type=DELIVERY") if "番茄" in p["name"])
    shipped_order = call(MALL, "POST", "/api/orders", user["token"], {
        "userId": user["userId"],
        "bizType": "DELIVERY",
        "recipient": "林小满",
        "phone": "13800000001",
        "address": "重庆邮电大学 24 栋",
        "note": "发货后取消限制测试",
        "idempotencyKey": "ext-shipped-" + uuid.uuid4().hex,
        "items": [{"productId": food["id"], "quantity": 1}],
    })
    call(MALL, "POST", f"/api/orders/{shipped_order['order']['id']}/pay", user["token"])
    order_id = shipped_order["order"]["id"]
    time.sleep(1)
    call(ADMIN, "POST", f"/api/admin/delivery/{order_id}/advance", admin["token"])
    call(ADMIN, "POST", f"/api/admin/delivery/{order_id}/advance", admin["token"])
    forbidden = False
    try:
        call(MALL, "POST", f"/api/orders/{order_id}/cancel", user["token"], {"reason": "尝试取消"})
    except RuntimeError:
        forbidden = True

    cancel_order = call(MALL, "POST", "/api/orders", user["token"], {
        "userId": user["userId"],
        "bizType": "DELIVERY",
        "recipient": "林小满",
        "phone": "13800000001",
        "address": "重庆邮电大学 24 栋",
        "note": "客服取消测试",
        "idempotencyKey": "ext-cancel-" + uuid.uuid4().hex,
        "items": [{"productId": food["id"], "quantity": 1}],
    })
    cancel_id = cancel_order["order"]["id"]
    call(MALL, "POST", f"/api/orders/{cancel_id}/pay", user["token"])
    time.sleep(1)
    call(ADMIN, "POST", f"/api/admin/orders/{cancel_id}/cancel", admin["token"], {
        "reason": "用户联系系统客服取消",
    })

    call(MALL, "POST", "/api/support/messages", user["token"], {
        "orderId": cancel_id,
        "content": "请帮我确认取消进度",
    })
    call(ADMIN, "POST", f"/api/admin/support/{user['userId']}/reply", admin["token"], {
        "orderId": cancel_id,
        "content": "订单已由系统客服取消，库存已回补。",
    })
    support = call(MALL, "GET", "/api/support/messages", user["token"])

    print(json.dumps({
        "dashboardProducts": dashboard["products"],
        "reviewRating": review["rating"],
        "shippedCancellationBlocked": forbidden,
        "customerServiceCancellation": True,
        "supportHasAdminReply": any(item["senderRole"] == "ADMIN" for item in support),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
