let token = localStorage.getItem("novacartAdminToken") || "";
let selectedUserId = null;

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];

async function adminApi(path, options = {}) {
    const response = await fetch(path, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
            ...(options.headers || {})
        }
    });
    const payload = await response.json();
    if (!response.ok || payload.success === false) throw new Error(payload.message || "请求失败");
    return payload.success === true ? payload.data : payload;
}

function toast(message) {
    const el = $("#toast");
    el.textContent = message;
    el.classList.add("show");
    clearTimeout(el.timer);
    el.timer = setTimeout(() => el.classList.remove("show"), 2200);
}

function showConsole() {
    $("#loginScreen").classList.add("hidden");
    $("#console").classList.remove("hidden");
    $("#adminName").textContent = "开发者模式";
    loadDashboard();
}

function showLogin() {
    $("#console").classList.add("hidden");
    $("#loginScreen").classList.remove("hidden");
}

async function validateAdminSession() {
    if (!token) {
        showLogin();
        return;
    }
    try {
        const response = await fetch("http://127.0.0.1:8090/api/auth/me", {
            headers: { Authorization: `Bearer ${token}` }
        });
        const payload = await response.json();
        if (!response.ok || !payload.success || payload.data.role !== "ADMIN") {
            throw new Error("开发者登录已失效");
        }
        $("#adminName").textContent = payload.data.nickname;
        showConsole();
    } catch (error) {
        localStorage.removeItem("novacartAdminToken");
        token = "";
        showLogin();
        $("#adminLoginError").textContent = error.message;
    }
}

async function login(event) {
    event.preventDefault();
    try {
        const auth = await fetch("http://127.0.0.1:8090/api/auth/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username: $("#adminUsername").value, password: $("#adminPassword").value })
        }).then((r) => r.json()).then((payload) => {
            if (!payload.success) throw new Error(payload.message);
            return payload.data;
        });
        if (auth.role !== "ADMIN") throw new Error("该账号没有开发者权限");
        token = auth.token;
        localStorage.setItem("novacartAdminToken", token);
        $("#adminName").textContent = auth.nickname;
        showConsole();
    } catch (error) {
        $("#adminLoginError").textContent = error.message;
    }
}

async function loadDashboard() {
    const data = await adminApi("/api/admin/dashboard");
    $("#stats").innerHTML = Object.entries(data).map(([key, value]) => `
        <div class="stat"><span>${label(key)}</span><b>${value}</b></div>
    `).join("");
}

function label(key) {
    return ({ products: "全部商品", activeProducts: "在售商品", orders: "全部订单", pendingCancellations: "处理中订单" })[key] || key;
}

async function loadProducts() {
    const keyword = encodeURIComponent($("#productSearch").value.trim());
    const products = await adminApi(`/api/admin/products?keyword=${keyword}`);
    $("#productRows").innerHTML = products.map((product) => `
        <article class="data-card">
            <div>
                <h3>${product.name}</h3>
                <p>ID ${product.id} · ${product.biz_type} · ${product.category} · 销量 ${product.sales}</p>
                <p>${product.description || ""}</p>
            </div>
            <div class="form-inline">
                <input id="price-${product.id}" type="number" step="0.01" value="${product.price}">
                <input id="stock-${product.id}" type="number" value="${product.stock}">
                <label><input id="active-${product.id}" type="checkbox" ${product.active ? "checked" : ""}> 上架</label>
            </div>
            <div class="form-inline">
                <button onclick="saveProduct(${product.id})">保存</button>
            </div>
        </article>
    `).join("");
}

async function saveProduct(id) {
    try {
        await adminApi(`/api/admin/products/${id}`, {
            method: "PUT",
            body: JSON.stringify({
                price: $(`#price-${id}`).value,
                stock: $(`#stock-${id}`).value,
                active: $(`#active-${id}`).checked
            })
        });
        toast("商品信息已更新");
        loadProducts();
    } catch (error) { toast(error.message); }
}

async function loadOrders() {
    const orders = await adminApi("/api/admin/orders");
    $("#orderRows").innerHTML = orders.map((order) => `
        <article class="data-card">
            <div>
                <h3>${order.order_no}</h3>
                <p>用户 ${order.user_id} · ${order.biz_type} · ${order.status}</p>
                <p>${order.recipient || ""} ${order.phone || ""} ${order.address || ""}</p>
            </div>
            <div><b>¥${Number(order.total_amount).toFixed(2)}</b><p>${order.created_at}</p></div>
            <div class="form-inline">
                ${["SHIPPED", "DELIVERING", "ARRIVED", "CANCELLED", "REVIEWED"].includes(order.status) ? "" : `<button class="danger" onclick="cancelOrder(${order.id})">客服取消</button>`}
                ${order.biz_type === "DELIVERY" ? `<button onclick="advanceDelivery(${order.id})">推进物流</button>` : ""}
            </div>
        </article>
    `).join("");
}

async function cancelOrder(id) {
    if (!window.confirm("确认由系统客服取消此订单？")) return;
    try {
        await adminApi(`/api/admin/orders/${id}/cancel`, {
            method: "POST", body: JSON.stringify({ reason: "系统客服协调取消" })
        });
        toast("订单已取消并回补库存");
        loadOrders();
    } catch (error) { toast(error.message); }
}

async function advanceDelivery(id) {
    try {
        await adminApi(`/api/admin/delivery/${id}/advance`, { method: "POST" });
        toast("配送状态已推进");
        loadOrders();
    } catch (error) { toast(error.message); }
}

async function loadThreads() {
    const threads = await adminApi("/api/admin/support/threads");
    $("#threadRows").innerHTML = threads.map((thread) => `
        <button class="thread" onclick="openThread(${thread.user_id}, '${escapeJs(thread.nickname)}')">
            <b>${thread.nickname} · #${thread.user_id}</b>
            <small>${thread.last_message || ""}</small>
        </button>
    `).join("") || '<p class="hint">暂无用户咨询</p>';
}

function escapeJs(value) {
    return String(value).replaceAll("\\", "\\\\").replaceAll("'", "\\'");
}

async function openThread(userId, name) {
    selectedUserId = userId;
    $("#threadTitle").textContent = `与 ${name} 会话`;
    const messages = await adminApi(`/api/admin/support/${userId}`);
    $("#adminMessages").innerHTML = messages.map((message) => `
        <div class="admin-message ${message.sender_role}">${message.content}<br><small>${message.created_at}</small></div>
    `).join("");
}

async function reply(event) {
    event.preventDefault();
    if (!selectedUserId) return toast("请先选择会话");
    try {
        await adminApi(`/api/admin/support/${selectedUserId}/reply`, {
            method: "POST",
            body: JSON.stringify({
                orderId: $("#replyOrderId").value || null,
                content: $("#replyContent").value
            })
        });
        $("#replyContent").value = "";
        openThread(selectedUserId, $("#threadTitle").textContent.replace("与 ", "").replace(" 会话", ""));
        toast("回复已发送");
    } catch (error) { toast(error.message); }
}

function switchTab(name) {
    $$("[data-tab]").forEach((button) => button.classList.toggle("active", button.dataset.tab === name));
    $$(".tab").forEach((tab) => tab.classList.remove("active"));
    $(`#${name}Tab`).classList.add("active");
    $("#pageTitle").textContent = ({ dashboard: "运营总览", products: "商品管理", orders: "订单处理", support: "客服消息" })[name];
    if (name === "dashboard") loadDashboard();
    if (name === "products") loadProducts();
    if (name === "orders") loadOrders();
    if (name === "support") loadThreads();
}

$("#adminLoginForm").addEventListener("submit", login);
$("#adminLogout").addEventListener("click", () => {
    localStorage.removeItem("novacartAdminToken");
    token = "";
    location.reload();
});
$$("[data-tab]").forEach((button) => button.addEventListener("click", () => switchTab(button.dataset.tab)));
$("#searchProducts").addEventListener("click", loadProducts);
$("#reloadOrders").addEventListener("click", loadOrders);
$("#replyForm").addEventListener("submit", reply);

validateAdminSession();
