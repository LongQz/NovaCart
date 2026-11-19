"""NovaCart end-to-end smoke test using only the Python standard library."""

import json
import sys
import time
import urllib.request
import uuid


MALL = "http://127.0.0.1:8090"
DELIVERY = "http://127.0.0.1:8091"
USER_ID = "1"
TOKEN = ""


def request(method: str, url: str, body: dict | None = None) -> dict:
    """Call a NovaCart JSON endpoint."""
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "Content-Type": "application/json",
            "X-User-Id": USER_ID,
            **({"Authorization": f"Bearer {TOKEN}"} if TOKEN else {}),
        },
    )
    # 大模型调用包含多轮工具执行，预留比普通 CRUD 更长的端到端时间。
    with urllib.request.urlopen(req, timeout=180) as response:
        payload = json.loads(response.read().decode("utf-8"))
    if not payload.get("success"):
        raise RuntimeError(payload.get("message"))
    return payload.get("data")


def main() -> None:
    """Exercise catalog, Redis cart, checkout, delivery and Spring AI."""
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    global TOKEN
    login = request("POST", f"{MALL}/api/auth/login", {"username": "demo", "password": "123456"})
    TOKEN = login["token"]

    mall_products = request("GET", f"{MALL}/api/products?type=MALL")
    mall_product = mall_products[0]
    request("DELETE", f"{MALL}/api/cart")
    cart = request("POST", f"{MALL}/api/cart/items?productId={mall_product['id']}&quantity=1")
    mall_order = request(
        "POST",
        f"{MALL}/api/orders",
        {
            "userId": 1,
            "bizType": "MALL",
            "recipient": "林小满",
            "phone": "13800000001",
            "address": "重庆邮电大学 24 栋",
            "note": "商城冒烟测试",
            "idempotencyKey": "smoke-mall-" + uuid.uuid4().hex,
            "items": [{"productId": mall_product["id"], "quantity": 1}],
        },
    )
    request("POST", f"{MALL}/api/orders/{mall_order['order']['id']}/pay")

    delivery_products = request("GET", f"{MALL}/api/products?type=DELIVERY")
    selected = next(product for product in delivery_products if "番茄" in product["name"])

    order_view = request(
        "POST",
        f"{MALL}/api/orders",
        {
            "userId": 1,
            "bizType": "DELIVERY",
            "recipient": "林小满",
            "phone": "13800000001",
            "address": "重庆邮电大学 24 栋",
            "note": "自动化冒烟测试",
            "idempotencyKey": "smoke-" + uuid.uuid4().hex,
            "items": [{"productId": selected["id"], "quantity": 2}],
        },
    )
    order_id = order_view["order"]["id"]
    paid = request("POST", f"{MALL}/api/orders/{order_id}/pay")
    time.sleep(1)
    delivery = request("GET", f"{DELIVERY}/api/delivery/tasks/{order_id}")
    advanced = request("POST", f"{DELIVERY}/api/delivery/tasks/{order_id}/advance")
    delivery_ai = request(
        "POST",
        f"{MALL}/api/ai/chat",
        {
            "message": f"请调用实时配送状态工具查询外卖订单{order_id}，只告诉我当前配送节点和预计时间，不要做其他操作。"
        },
    )
    support = request(
        "POST",
        f"{MALL}/api/support/messages",
        {"orderId": order_id, "content": "请问当前订单什么时候送达？"},
    )

    ai = request(
        "POST",
        f"{MALL}/api/ai/chat",
        {
            "message": "预算80元，两个人晚餐，不想吃辣，地址是重庆邮电大学24栋。请推荐菜品并估算配送时间，不要下单。"
        },
    )

    print(json.dumps({
        "loginUser": login["nickname"],
        "mallOrderStatus": "PAID",
        "selectedProduct": selected["name"],
        "cartItemCount": len(cart["items"]),
        "orderId": order_id,
        "orderStatus": paid["order"]["status"],
        "deliveryStatus": delivery["status"],
        "advancedDeliveryStatus": advanced["status"],
        "aiDeliveryAnswer": delivery_ai["answer"],
        "supportMessageId": support["id"],
        "aiAnswer": ai["answer"],
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
