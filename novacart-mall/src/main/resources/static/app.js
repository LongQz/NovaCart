const state = {
    type: "MALL",
    view: "home",
    products: [],
    hot: [],
    cart: { items: [], totalAmount: 0, recipient: "", phone: "" },
    checkout: null,
    payingOrder: null,
    reviewOrder: null,
    detailProduct: null,
    detailQuantity: 1,
    detailSpecs: {},
    auth: JSON.parse(localStorage.getItem("novacartAuth") || "null")
};

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];

async function api(path, options = {}, needAuth = false) {
    const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
    if (needAuth) {
        if (!state.auth?.token) {
            openLogin();
            throw new Error("请先登录");
        }
        headers.Authorization = `Bearer ${state.auth.token}`;
    }
    const response = await fetch(path, { ...options, headers });
    const payload = await response.json();
    if (!response.ok || payload.success === false) {
        throw new Error(payload.message || `请求失败 ${response.status}`);
    }
    return payload.data;
}

function toast(message) {
    const el = $("#toast");
    el.textContent = message;
    el.classList.add("show");
    clearTimeout(el.timer);
    el.timer = setTimeout(() => el.classList.remove("show"), 2200);
}

function openLogin() {
    $("#loginModal").classList.add("open");
    $("#loginError").textContent = "";
}

function closeModal(id) {
    $(`#${id}`).classList.remove("open");
}

function setAuth(auth) {
    state.auth = auth;
    if (auth) localStorage.setItem("novacartAuth", JSON.stringify(auth));
    else localStorage.removeItem("novacartAuth");
    renderLoginEntry();
}

function renderLoginEntry() {
    $("#loginEntry").textContent = state.auth ? state.auth.nickname : "登录 / 注册";
}

function switchAuthTab(tab) {
    const register = tab === "register";
    $("#loginTab").classList.toggle("active", !register);
    $("#registerTab").classList.toggle("active", register);
    $("#loginForm").classList.toggle("hidden", register);
    $("#registerForm").classList.toggle("hidden", !register);
    $("#authTitle").textContent = register ? "注册 NovaCart" : "登录 NovaCart";
    $("#loginError").textContent = "";
}

async function sendRegisterCode() {
    const phone = $("#registerPhone").value.trim();
    try {
        const data = await api(`/api/auth/code?phone=${encodeURIComponent(phone)}`, { method: "POST" });
        $("#registerCodeHint").textContent = `本地验证码：${data.debugCode}（5分钟内有效）`;
        $("#registerCode").value = data.debugCode;
        toast("验证码已生成");
    } catch (error) {
        $("#registerCodeHint").textContent = error.message;
    }
}

async function registerAccount() {
    try {
        const auth = await api("/api/auth/register", {
            method: "POST",
            body: JSON.stringify({
                username: $("#registerUsername").value.trim(),
                password: $("#registerPassword").value,
                nickname: $("#registerNickname").value.trim(),
                phone: $("#registerPhone").value.trim(),
                code: $("#registerCode").value.trim()
            })
        });
        setAuth(auth);
        closeModal("loginModal");
        toast(`注册成功，欢迎 ${auth.nickname}`);
        loadCart();
    } catch (error) {
        $("#loginError").textContent = error.message;
    }
}

function productEmoji(product) {
    if (product.bizType === "DELIVERY") {
        if (product.category.includes("饮品")) return "🥤";
        if (product.category.includes("甜品")) return "🍰";
        if (product.category.includes("轻食")) return "🥗";
        if (product.category.includes("汤")) return "🥣";
        if (product.category.includes("烧烤")) return "🍗";
        return "🍜";
    }
    if (product.category.includes("数码")) return "🎧";
    if (product.category.includes("出行")) return "🎒";
    if (product.category.includes("生活")) return "🕯️";
    return "🛍️";
}

const productPhotoMap = {
    "降噪蓝牙耳机 AirLite": "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80",
    "柔光阅读台灯": "https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=900&q=80",
    "山野香薰蜡烛": "https://images.unsplash.com/photo-1603006905003-be475563bc59?auto=format&fit=crop&w=900&q=80",
    "城市通勤双肩包": "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=900&q=80",
    "磁吸无线充电器": "https://images.unsplash.com/photo-1587033411391-5d9e51cce126?auto=format&fit=crop&w=900&q=80",
    "宿舍迷你加湿器": "https://images.unsplash.com/photo-1585771724684-38269d6639fd?auto=format&fit=crop&w=900&q=80",
    "轻量运动水杯": "https://images.unsplash.com/photo-1602143407151-7111542de6e8?auto=format&fit=crop&w=900&q=80",
    "机械键盘 K67": "https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=900&q=80",
    "桌面理线收纳盒": "https://images.unsplash.com/photo-1494438639946-1ebd1d20bf85?auto=format&fit=crop&w=900&q=80",
    "城市雨伞": "https://images.unsplash.com/photo-1535916707207-35f97e715e1c?auto=format&fit=crop&w=900&q=80",
    "轻薄移动电源": "https://images.unsplash.com/photo-1583863788434-e58a36330cf0?auto=format&fit=crop&w=900&q=80",
    "舒适颈枕": "https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?auto=format&fit=crop&w=900&q=80",
    "招牌山城小面": "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=900&q=80",
    "藤椒鸡腿饭": "https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&w=900&q=80",
    "清爽番茄肥牛汤": "https://images.unsplash.com/photo-1547592166-23ac45744acd?auto=format&fit=crop&w=900&q=80",
    "酸辣土豆丝盖饭": "https://images.unsplash.com/photo-1512058564366-18510be2db19?auto=format&fit=crop&w=900&q=80",
    "重庆毛血旺": "https://images.unsplash.com/photo-1563245372-f21724e3856d?auto=format&fit=crop&w=900&q=80",
    "低温鸡胸沙拉": "https://images.unsplash.com/photo-1540420773420-3366772f4999?auto=format&fit=crop&w=900&q=80",
    "牛油果鸡蛋卷": "https://images.unsplash.com/photo-1626700051175-6818013e1d4f?auto=format&fit=crop&w=900&q=80",
    "南瓜浓汤": "https://images.unsplash.com/photo-1476718406336-bb5a9690ee2a?auto=format&fit=crop&w=900&q=80",
    "桂花酒酿小圆子": "https://images.unsplash.com/photo-1551024506-0bccd828d307?auto=format&fit=crop&w=900&q=80",
    "生椰拿铁": "https://images.unsplash.com/photo-1461023058943-07fcbe16d735?auto=format&fit=crop&w=900&q=80",
    "轻乳酪蛋糕": "https://images.unsplash.com/photo-1578985545062-69928b1d9587?auto=format&fit=crop&w=900&q=80",
    "炭火烤鸡腿": "https://images.unsplash.com/photo-1532550907401-a500c9a57435?auto=format&fit=crop&w=900&q=80"
};

function productPhoto(product) {
    return product.imageUrl || productPhotoMap[product.name]
        || "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=900&q=80";
}

function productVisual(product) {
    return `<img src="${productPhoto(product)}" alt="${product.name}" loading="lazy">`;
}

function statusText(product) {
    if (product.active === false) return "已下架";
    if (product.stock <= 0) return "缺货中";
    return null;
}

function view(name) {
    if (["cart", "orders", "profile", "messages"].includes(name) && !state.auth) {
        openLogin();
        return;
    }
    state.view = name;
    $$(".view").forEach((section) => section.classList.remove("active"));
    $(`#${name}View`).classList.add("active");
    if (name === "home") loadHome();
    if (name === "cart") loadCart();
    if (name === "orders") loadOrders();
    if (name === "profile") renderProfile();
    if (name === "messages") loadSupport();
    window.scrollTo({ top: 0, behavior: "smooth" });
}

let activeHomeRequest = 0;

async function loadHome() {
    const requestId = ++activeHomeRequest;
    const type = state.type;
    const keyword = encodeURIComponent($("#searchInput").value.trim());
    try {
        const [products, hot] = await Promise.all([
            api(`/api/products?type=${type}&keyword=${keyword}`),
            api(`/api/hot?type=${type}`)
        ]);
        // 快速切换商城/外卖时，只允许最后一次请求更新页面，避免旧响应覆盖新列表。
        if (requestId !== activeHomeRequest || type !== state.type) return;
        state.products = Array.isArray(products) ? products : [];
        state.hot = Array.isArray(hot) ? hot : [];
        renderHome();
    } catch (error) {
        if (requestId !== activeHomeRequest) return;
        toast(error.message);
    }
}

function renderHome() {
    const isDelivery = state.type === "DELIVERY";
    $("#bannerTitle").textContent = isDelivery ? "附近好味道，直接送到你身边" : "精选好物，直接送到你身边";
    $("#bannerText").textContent = isDelivery
        ? "按菜品单独购买，支付后进入商家接单与配送流程。"
        : "数码、生活、出行商品统一选购，支持购物车结算与订单售后。";
    $("#hotTitle").textContent = isDelivery ? "热门外卖" : "热门商品";
    $("#gridTitle").textContent = isDelivery ? "附近菜品" : "全部商品";
    $("#gridKicker").textContent = isDelivery ? "附近好味" : "精选专区";
    $("#bannerCount").textContent = state.hot.length;
    $("#resultCount").textContent = `共 ${state.products.length} 件`;
    $("#hotList").innerHTML = state.hot.map((product, index) => `
        <article class="hot-item" data-product="${product.id}">
            <span class="hot-rank">${index + 1}</span>
            <div class="hot-art">${productVisual(product)}</div>
            <b>${product.name}</b>
            <small>月售 ${product.sales || 0}</small>
        </article>
    `).join("");
    $("#productGrid").innerHTML = state.products.map(productCard).join("");
    $$(".hot-item").forEach((item) => item.addEventListener("click", () => openProductDetail(item.dataset.product)));
    bindProductButtons();
}

function productCard(product) {
    const unavailable = statusText(product);
    const action = unavailable
        ? `<button class="sold-out" disabled>${unavailable}</button>`
        : product.bizType === "DELIVERY"
            ? `<button class="buy-action" data-buy="${product.id}">立即购买</button>`
            : `<button class="cart-action" data-cart="${product.id}">加入购物车</button>
               <button class="buy-action" data-buy="${product.id}">立即购买</button>`;
    return `
        <article class="product-card">
            <div class="product-art ${product.bizType}" data-product="${product.id}">${productVisual(product)}</div>
            <div class="product-body">
                <span class="product-category">${product.category}</span>
                <h3>${product.name}</h3>
                <p class="product-desc">${product.description || ""}</p>
                <div class="product-bottom">
                    <span class="price">¥${Number(product.price).toFixed(2)}</span>
                    <span class="sales">月售 ${product.sales || 0}</span>
                </div>
                <div class="product-actions">${action}</div>
            </div>
        </article>`;
}

function bindProductButtons() {
    $$("[data-product]").forEach((item) => item.addEventListener("click", (event) => {
        if (!event.target.closest("button")) openProductDetail(item.dataset.product);
    }));
    $$("[data-cart]").forEach((button) => button.addEventListener("click", () => openProductDetail(button.dataset.cart)));
    $$("[data-buy]").forEach((button) => button.addEventListener("click", () => openProductDetail(button.dataset.buy)));
}

function specGroups(product) {
    const groups = [];
    const name = product.name || "";
    const text = `${product.name} ${product.tags} ${product.description}`;
    if (product.bizType === "MALL") {
        if (name.includes("机械键盘")) groups.push({ name: "轴体", options: ["青轴", "茶轴", "红轴"] });
        if (name.includes("移动电源")) groups.push({ name: "容量", options: ["10000mAh", "20000mAh"] });
        if (name.includes("水杯")) groups.push({ name: "容量", options: ["500ml", "750ml"] });
        if (name.includes("双肩包")) groups.push({ name: "尺寸", options: ["15L", "18L"] });
        if (name.includes("耳机")) groups.push({ name: "颜色", options: ["雾黑", "云白"] });
    } else {
        if (/辣|藤椒|毛血旺/.test(text)) groups.push({ name: "辣度", options: ["不辣", "微辣", "中辣", "特辣"] });
        if (["主食", "特色菜", "汤品", "烧烤", "甜品"].includes(product.category)) {
            groups.push({ name: "份量", options: ["单人份", "双人份"] });
        }
        if (product.category === "饮品") groups.push({ name: "杯型", options: ["中杯", "大杯"] }, { name: "冰量", options: ["正常冰", "少冰", "去冰"] });
        if (name.includes("鸡腿")) groups.push({ name: "做法", options: ["正常烤制", "少油"] });
    }
    return groups;
}

function saveSpecSelection(productId, text) {
    const selections = JSON.parse(localStorage.getItem("novacartSpecSelections") || "{}");
    selections[productId] = text;
    localStorage.setItem("novacartSpecSelections", JSON.stringify(selections));
}

function selectedSpecText() {
    return Object.values(state.detailSpecs).filter(Boolean).join(" / ");
}

async function openProductDetail(productId) {
    const product = [...state.products, ...state.hot].find((item) => item.id === Number(productId));
    if (!product) return;
    state.detailProduct = product;
    state.detailQuantity = 1;
    state.detailSpecs = {};
    specGroups(product).forEach((group) => { state.detailSpecs[group.name] = group.options[0]; });
    let reviews = [];
    try {
        reviews = await api(`/api/products/${product.id}/reviews`);
    } catch {
        reviews = [];
    }
    renderProductDetail(reviews);
    $("#productDetailModal").classList.add("open");
}

function renderProductDetail(reviews = []) {
    const product = state.detailProduct;
    const groups = specGroups(product);
    $("#productDetailContent").innerHTML = `
        <div class="product-detail">
            <img class="product-detail-photo" src="${productPhoto(product)}" alt="${product.name}">
            <div>
                <span class="detail-category">${product.category} · 月售 ${product.sales || 0}</span>
                <h2>${product.name}</h2>
                <p class="detail-desc">${product.description || "暂无商品描述"}</p>
                <p class="detail-desc">标签：${product.tags || "暂无"}</p>
                <div class="spec-groups">
                    ${groups.map((group) => `
                        <div class="spec-group">
                            <span>${group.name}</span>
                            <div class="spec-options">
                                ${group.options.map((option) => `
                                    <button data-spec-group="${group.name}" data-spec-value="${option}"
                                      class="${state.detailSpecs[group.name] === option ? "active" : ""}">${option}</button>
                                `).join("")}
                            </div>
                        </div>
                    `).join("")}
                </div>
                <div class="product-bottom">
                    <span class="price">${money(product.price)}</span>
                    <span class="sales">库存 ${product.stock}</span>
                </div>
                <div class="detail-buy-row">
                    <div class="quantity">
                        <button data-detail-minus>−</button><span>${state.detailQuantity}</span><button data-detail-plus>＋</button>
                    </div>
                    ${product.stock <= 0
                        ? '<button class="sold-out" disabled>缺货中</button>'
                        : product.bizType === "DELIVERY"
                            ? '<button id="detailBuy" class="buy-action">立即购买</button>'
                            : '<button id="detailAddCart" class="cart-action">加入购物车</button><button id="detailBuy" class="buy-action">立即购买</button>'}
                </div>
                <section class="review-preview">
                    <b>用户评价</b>
                    ${reviews.length
                        ? reviews.slice(0, 3).map((review) => `<p>${"★".repeat(review.rating)}${"☆".repeat(5 - review.rating)} ${review.content || "用户未填写评价"}</p>`).join("")
                        : "<p>暂时还没有评价</p>"}
                </section>
            </div>
        </div>`;
    $$("#productDetailContent [data-spec-group]").forEach((button) => {
        button.addEventListener("click", () => {
            state.detailSpecs[button.dataset.specGroup] = button.dataset.specValue;
            renderProductDetail(reviews);
        });
    });
    $("[data-detail-minus]")?.addEventListener("click", () => {
        state.detailQuantity = Math.max(1, state.detailQuantity - 1);
        renderProductDetail(reviews);
    });
    $("[data-detail-plus]")?.addEventListener("click", () => {
        state.detailQuantity = Math.min(state.detailProduct.stock, state.detailQuantity + 1);
        renderProductDetail(reviews);
    });
    $("#detailAddCart")?.addEventListener("click", async () => {
        saveSpecSelection(product.id, selectedSpecText());
        await addCart(product.id, state.detailQuantity);
        closeModal("productDetailModal");
    });
    $("#detailBuy")?.addEventListener("click", () => {
        closeModal("productDetailModal");
        buyNow(product.id, state.detailQuantity, selectedSpecText());
    });
}

async function loadCart() {
    if (!state.auth) return;
    try {
        state.cart = await api("/api/cart", {}, true);
        $("#cartBadge").textContent = state.cart.items.reduce((sum, item) => sum + item.quantity, 0);
        $("#cartRecipient").value = state.cart.recipient || state.auth.nickname || "";
        $("#cartPhone").value = state.cart.phone || "";
        $("#cartQuantity").textContent = state.cart.items.reduce((sum, item) => sum + item.quantity, 0);
        $("#cartTotal").textContent = money(state.cart.totalAmount);
        $("#cartList").innerHTML = state.cart.items.length ? state.cart.items.map((item) => `
            <div class="cart-item">
                <div class="cart-thumb">${productVisual({ name: item.name, imageUrl: item.imageUrl, category: "", bizType: state.type })}</div>
                <div>
                    <b>${item.name}</b>
                    <p class="muted">${(JSON.parse(localStorage.getItem("novacartSpecSelections") || "{}")[item.productId] || "默认规格")}</p>
                    <p class="muted">${money(item.price)} / 件</p>
                    <div class="quantity">
                        <button data-cart-minus="${item.productId}">−</button>
                        <span>${item.quantity}</span>
                        <button data-cart-plus="${item.productId}">＋</button>
                    </div>
                </div>
                <div style="text-align:right">
                    <b>${money(item.subtotal)}</b><br>
                    <button class="remove-item" data-cart-remove="${item.productId}">删除</button>
                </div>
            </div>`).join("") : '<p class="muted">购物车暂时没有商品。</p>';
        $$("[data-cart-minus]").forEach((b) => b.addEventListener("click", () => addCart(b.dataset.cartMinus, -1)));
        $$("[data-cart-plus]").forEach((b) => b.addEventListener("click", () => addCart(b.dataset.cartPlus, 1)));
        $$("[data-cart-remove]").forEach((b) => b.addEventListener("click", () => removeCart(b.dataset.cartRemove)));
    } catch (error) {
        toast(error.message);
    }
}

async function addCart(productId, quantity) {
    if (!state.auth) return openLogin();
    try {
        state.cart = await api(`/api/cart/items?productId=${productId}&quantity=${quantity}`, { method: "POST" }, true);
        await loadCart();
        toast(quantity > 0 ? "已加入购物车" : "数量已减少");
    } catch (error) {
        toast(error.message);
    }
}

async function removeCart(productId) {
    try {
        state.cart = await api(`/api/cart/items/${productId}`, { method: "DELETE" }, true);
        await loadCart();
        toast("商品已从购物车删除");
    } catch (error) {
        toast(error.message);
    }
}

async function saveCartContact() {
    if (!state.auth) return;
    try {
        state.cart = await api("/api/cart/contact", {
            method: "POST",
            body: JSON.stringify({ recipient: $("#cartRecipient").value, phone: $("#cartPhone").value })
        }, true);
    } catch (error) {
        toast(error.message);
    }
}

async function buyNow(productId, quantity = 1, specText = "") {
    if (!state.auth) return openLogin();
    const product = state.products.find((item) => item.id === Number(productId));
    if (!product || product.stock <= 0) return toast("商品已缺货");
    openCheckout({
        bizType: product.bizType,
        items: [{ productId: product.id, name: product.name, price: product.price, quantity, subtotal: Number(product.price) * quantity, specText }],
        totalAmount: Number(product.price) * quantity,
        specText,
        recipient: state.auth.nickname || "",
        phone: "",
        address: ""
    });
}

async function cartCheckout() {
    if (!state.cart.items.length) return toast("购物车为空");
    await saveCartContact();
    const address = $("#cartAddress").value.trim();
    const recipient = $("#cartRecipient").value.trim();
    const phone = $("#cartPhone").value.trim();
    if (state.type === "DELIVERY" && (!address || !recipient || !phone)) {
        return toast("请填写收货人、电话和配送地址");
    }
        const specSelections = JSON.parse(localStorage.getItem("novacartSpecSelections") || "{}");
        openCheckout({
            ...state.cart,
            bizType: "MALL",
            recipient,
            phone,
            address,
            items: state.cart.items.map((item) => ({ ...item, specText: specSelections[item.productId] || "" })),
            specText: state.cart.items.map((item) => `${item.name}：${specSelections[item.productId] || "默认规格"}`).join("；")
        });
}

function openCheckout(draft) {
    state.checkout = draft;
    $("#checkoutRecipient").value = draft.recipient || state.auth.nickname || "";
    $("#checkoutPhone").value = draft.phone || "";
    $("#checkoutAddress").value = draft.address || "";
    $("#checkoutItems").innerHTML = draft.items.map((item) => `
        <div class="checkout-item"><span>${item.name} × ${item.quantity}${item.specText ? `<small class="muted">（${item.specText}）</small>` : ""}</span><b>${money(item.subtotal)}</b></div>
    `).join("");
    $("#checkoutTotal").textContent = money(draft.totalAmount);
    $("#checkoutPayable").textContent = money(draft.totalAmount);
    view("checkout");
}

function showOrderConfirmation() {
    if (!state.checkout) return;
    state.checkout.recipient = $("#checkoutRecipient").value.trim();
    state.checkout.phone = $("#checkoutPhone").value.trim();
    state.checkout.address = $("#checkoutAddress").value.trim();
    const draft = state.checkout;
    $("#confirmContent").innerHTML = `
        <b>${draft.bizType === "DELIVERY" ? "闪购外卖" : "品质商城"}</b><br>
        收货人：${draft.recipient}<br>
        电话：${draft.phone || "未填写"}<br>
        地址：${draft.address || "无需配送"}<br>
        商品：${draft.items.map((item) => `${item.name}${item.specText ? `（${item.specText}）` : ""} × ${item.quantity}`).join("、")}<br>
        应付金额：<strong>${money(draft.totalAmount)}</strong>
    `;
    $("#confirmModal").classList.add("open");
}

async function createOrder() {
    try {
        const data = await api("/api/orders", {
            method: "POST",
            body: JSON.stringify({
                userId: state.auth.userId,
                bizType: state.checkout.bizType,
                recipient: state.checkout.recipient,
                phone: state.checkout.phone,
                address: state.checkout.address,
                note: state.checkout.specText || "",
                idempotencyKey: `${state.auth.userId}-${Date.now()}`,
                items: state.checkout.items.map((item) => ({ productId: item.productId, quantity: item.quantity }))
            })
        }, true);
        state.payingOrder = data.order;
        if (state.checkout.bizType === "MALL") {
            const selections = JSON.parse(localStorage.getItem("novacartSpecSelections") || "{}");
            state.checkout.items.forEach((item) => delete selections[item.productId]);
            localStorage.setItem("novacartSpecSelections", JSON.stringify(selections));
        }
        closeModal("confirmModal");
        $("#payOrderNo").textContent = `订单号：${data.order.orderNo}`;
        $("#payAmount").textContent = money(data.order.totalAmount);
        view("pay");
        if (state.checkout.bizType === "MALL") loadCart();
    } catch (error) {
        toast(error.message);
    }
}

async function payOrder() {
    if (!state.payingOrder) return;
    try {
        const viewData = await api(`/api/orders/${state.payingOrder.id}/pay`, { method: "POST" }, true);
        $("#successMessage").textContent = viewData.order.bizType === "DELIVERY"
            ? "商家正在接单，可在订单页查看配送进度。"
            : "支付成功，订单已进入处理流程。";
        view("success");
    } catch (error) {
        toast(error.message);
    }
}

async function loadOrders() {
    try {
        const orders = await api("/api/orders", {}, true);
        const cards = [];
        for (const order of orders) {
            let delivery = null;
            if (order.bizType === "DELIVERY") {
                try {
                    delivery = await api(`http://127.0.0.1:8091/api/delivery/tasks/${order.id}`);
                } catch { delivery = null; }
            }
            cards.push(orderCard(order, delivery));
        }
        $("#orderList").innerHTML = cards.join("") || '<div class="order-card muted">暂无订单</div>';
        $$("[data-cancel-order]").forEach((button) => button.addEventListener("click", () => cancelOrder(button.dataset.cancelOrder)));
        $$("[data-review-order]").forEach((button) => button.addEventListener("click", () => openReview(button.dataset.reviewOrder)));
        $$("#orderList [data-view]").forEach((button) => button.addEventListener("click", () => view(button.dataset.view)));
    } catch (error) {
        toast(error.message);
    }
}

function orderCard(order, delivery) {
    const orderStatus = delivery ? delivery.status : order.status;
    const canCancel = ["PENDING_PAYMENT", "PAID", "PENDING_MERCHANT", "PENDING_SHIPMENT"].includes(orderStatus);
    const canReview = order.status !== "REVIEWED"
        && (orderStatus === "ARRIVED" || (order.bizType === "MALL" && orderStatus === "PAID"));
    return `
        <article class="order-card">
            <div class="order-top">
                <div><b>${order.orderNo}</b><br><small class="muted">${order.createdAt?.replace("T", " ")}</small></div>
                <span class="status-pill">${statusTextOrder(orderStatus)}</span>
            </div>
            <div class="order-lines">
                ${order.recipient || order.username || "NovaCart 用户"} ${order.phone || ""}<br>
                ${order.address || "商城订单"}; 合计 <b>${money(order.totalAmount)}</b>
                ${delivery ? `<br>配送进度：${statusTextDelivery(delivery.status)} · ${delivery.courierName || "等待商家接单"} · 预计 ${delivery.etaMinutes} 分钟` : ""}
                ${order.cancelReason ? `<br>取消原因：${order.cancelReason}` : ""}
            </div>
            <div class="order-bottom">
                <span class="muted">${order.bizType === "DELIVERY" ? "闪购外卖" : "品质商城"}</span>
                <div class="order-actions">
                    ${canReview ? `<button data-review-order="${order.id}">评价</button>` : ""}
                    ${canCancel ? `<button class="danger" data-cancel-order="${order.id}">取消订单</button>` : ""}
                    ${delivery ? `<button data-view="messages">联系客服</button>` : ""}
                </div>
            </div>
        </article>`;
}

function statusTextOrder(status) {
    return ({
        PENDING_PAYMENT: "待支付", PAID: "待发货", PENDING_MERCHANT: "待商家接单",
        PENDING_SHIPMENT: "待商家发货", SHIPPED: "已发货", DELIVERING: "送货中",
        ARRIVED: "已到达", REVIEWED: "已评价", CANCELLED: "已取消", COMPLETED: "已完成"
    })[status] || status;
}

function statusTextDelivery(status) {
    return ({ PENDING_MERCHANT: "待商家接单", PENDING_SHIPMENT: "待商家发货",
        SHIPPED: "已发货", DELIVERING: "送货中", ARRIVED: "已到达", CANCELLED: "已取消" })[status] || status;
}

async function cancelOrder(orderId) {
    if (!window.confirm("确认取消该订单吗？取消后库存会自动回补。")) return;
    try {
        await api(`/api/orders/${orderId}/cancel`, { method: "POST", body: JSON.stringify({ reason: "用户取消" }) }, true);
        toast("订单已取消");
        loadOrders();
    } catch (error) {
        toast(error.message);
    }
}

function openReview(orderId) {
    state.reviewOrder = orderId;
    $("#reviewContent").value = "";
    $("#reviewRating").value = "5";
    $("#reviewModal").classList.add("open");
}

async function submitReview() {
    try {
        await api("/api/reviews", {
            method: "POST",
            body: JSON.stringify({
                orderId: state.reviewOrder,
                rating: Number($("#reviewRating").value),
                content: $("#reviewContent").value
            })
        }, true);
        closeModal("reviewModal");
        toast("评价已提交");
        loadOrders();
    } catch (error) {
        toast(error.message);
    }
}

async function showReviews(productId) {
    try {
        const reviews = await api(`/api/products/${productId}/reviews`);
        const text = reviews.length
            ? reviews.map((review) => `${"★".repeat(review.rating)}${"☆".repeat(5 - review.rating)} ${review.content}`).join("\n")
            : "该商品暂时还没有评价";
        window.alert(text);
    } catch (error) {
        toast(error.message);
    }
}

function renderProfile() {
    if (!state.auth) return;
    $("#profileContent").innerHTML = `
        <div><div class="avatar">${state.auth.nickname.slice(0, 1)}</div></div>
        <div>
            <h2>${state.auth.nickname}</h2>
            <p class="muted">账号：${state.auth.username} · 身份：${state.auth.role}</p>
            <div class="profile-stats">
                <div class="stat-box"><span>我的订单</span><b>${$("#orderList").children.length || "--"}</b></div>
                <div class="stat-box"><span>购物车</span><b>${$("#cartBadge").textContent}</b></div>
                <div class="stat-box"><span>联系客服</span><b>24h</b></div>
            </div>
            <p style="margin-top:20px"><button id="logoutButton" class="plain-button">退出登录</button></p>
        </div>`;
    $("#logoutButton").addEventListener("click", () => {
        setAuth(null);
        view("home");
    });
}

async function loadSupport() {
    try {
        const messages = await api("/api/support/messages", {}, true);
        $("#supportMessages").innerHTML = messages.length
            ? messages.map((message) => `<div class="support-message ${message.senderRole}">${message.content}<br><small>${message.createdAt?.replace("T", " ")}</small></div>`).join("")
            : '<p class="muted">暂无消息，可以向系统客服描述订单问题。</p>';
    } catch (error) {
        toast(error.message);
    }
}

async function sendSupport() {
    const content = $("#supportInput").value.trim();
    if (!content) return;
    try {
        await api("/api/support/messages", { method: "POST", body: JSON.stringify({ content }) }, true);
        $("#supportInput").value = "";
        loadSupport();
    } catch (error) {
        toast(error.message);
    }
}

async function askAssistant() {
    const input = $("#assistantInput");
    const message = input.value.trim();
    if (!message) return;
    $("#assistantMessages").insertAdjacentHTML("beforeend", `<div class="message user">${message}</div>`);
    input.value = "";
    try {
        const answer = await api("/api/ai/chat", { method: "POST", body: JSON.stringify({ message }) }, true).then((r) => r.answer);
        $("#assistantMessages").insertAdjacentHTML("beforeend", `<div class="message assistant">${answer}</div>`);
    } catch (error) {
        $("#assistantMessages").insertAdjacentHTML("beforeend", `<div class="message assistant">${error.message}</div>`);
    }
}

function openAssistantPanel() {
    if (!state.auth) return openLogin();
    const launcher = $("#assistantLauncher");
    const panel = $("#assistantPanel");
    if (!$("#assistantMessages").children.length) {
        $("#assistantMessages").insertAdjacentHTML(
            "beforeend",
            '<div class="message assistant">你好，我是 NovaCart 选购助手。告诉我预算、人数、口味或想要的商品，我来帮你挑。</div>'
        );
    }
    launcher.classList.add("is-open");
    panel.classList.add("open");
}

function closeAssistantPanel() {
    $("#assistantPanel").classList.remove("open");
    $("#assistantLauncher").classList.remove("is-open");
    $("#assistantSpeech").innerHTML = "<b>还需要我吗？</b><small>随时可以叫我</small>";
    $("#assistantLauncher").classList.remove("prompt-hidden");
}

function restoreAssistantPosition() {
    const launcher = $("#assistantLauncher");
    const saved = JSON.parse(localStorage.getItem("novacartAssistantPosition") || "null");
    if (!saved || !Number.isFinite(saved.left) || !Number.isFinite(saved.top)) return;
    const rect = launcher.getBoundingClientRect();
    const left = Math.min(Math.max(8, saved.left), window.innerWidth - rect.width - 8);
    const top = Math.min(Math.max(8, saved.top), window.innerHeight - rect.height - 8);
    launcher.style.left = `${left}px`;
    launcher.style.top = `${top}px`;
    launcher.style.right = "auto";
    launcher.style.bottom = "auto";
}

function initAssistantLauncher() {
    const launcher = $("#assistantLauncher");
    let promptTimer = setTimeout(() => launcher.classList.add("prompt-hidden"), 6500);
    launcher.addEventListener("mouseenter", () => {
        clearTimeout(promptTimer);
        launcher.classList.remove("prompt-hidden");
    });
    launcher.addEventListener("mouseleave", () => {
        promptTimer = setTimeout(() => launcher.classList.add("prompt-hidden"), 1800);
    });

    let drag = null;
    let suppressClick = false;
    launcher.addEventListener("pointerdown", (event) => {
        if (event.button !== 0) return;
        const rect = launcher.getBoundingClientRect();
        drag = {
            pointerId: event.pointerId,
            startX: event.clientX,
            startY: event.clientY,
            offsetX: event.clientX - rect.left,
            offsetY: event.clientY - rect.top,
            moved: false
        };
        launcher.setPointerCapture(event.pointerId);
    });
    launcher.addEventListener("pointermove", (event) => {
        if (!drag || drag.pointerId !== event.pointerId) return;
        const dx = event.clientX - drag.startX;
        const dy = event.clientY - drag.startY;
        if (!drag.moved && Math.hypot(dx, dy) < 6) return;
        drag.moved = true;
        launcher.classList.add("is-dragging", "prompt-hidden");
        const rect = launcher.getBoundingClientRect();
        const left = Math.min(Math.max(8, event.clientX - drag.offsetX), window.innerWidth - rect.width - 8);
        const top = Math.min(Math.max(8, event.clientY - drag.offsetY), window.innerHeight - rect.height - 8);
        launcher.style.left = `${left}px`;
        launcher.style.top = `${top}px`;
        launcher.style.right = "auto";
        launcher.style.bottom = "auto";
    });
    const finishDrag = () => {
        if (!drag) return;
        const moved = drag.moved;
        drag = null;
        launcher.classList.remove("is-dragging");
        if (moved) {
            suppressClick = true;
            const rect = launcher.getBoundingClientRect();
            localStorage.setItem(
                "novacartAssistantPosition",
                JSON.stringify({ left: Math.round(rect.left), top: Math.round(rect.top) })
            );
            setTimeout(() => { suppressClick = false; }, 80);
        }
    };
    launcher.addEventListener("pointerup", finishDrag);
    launcher.addEventListener("pointercancel", finishDrag);
    launcher.addEventListener("click", () => {
        if (suppressClick) return;
        openAssistantPanel();
    });
    launcher.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            openAssistantPanel();
        }
    });
    window.addEventListener("resize", restoreAssistantPosition);
    restoreAssistantPosition();
}

function money(value) { return `¥${Number(value || 0).toFixed(2)}`; }

function initEvents() {
    $$("[data-view]").forEach((button) => button.addEventListener("click", () => view(button.dataset.view)));
    $$(".mode-button").forEach((button) => button.addEventListener("click", () => {
        $$(".mode-button").forEach((item) => item.classList.remove("active"));
        button.classList.add("active");
        // 搜索框可能被浏览器自动填入登录账号；切换模式时恢复为完整商品列表。
        $("#searchInput").value = "";
        state.type = button.dataset.type;
        loadHome();
        if (state.view === "cart" && state.cart.items.length) {
            view("home");
            toast("切换模式后请重新选择商品");
        }
    }));
    $("#searchButton").addEventListener("click", loadHome);
    $("#searchInput").addEventListener("keydown", (event) => event.key === "Enter" && loadHome());
    $("#loginEntry").addEventListener("click", () => state.auth ? view("profile") : openLogin());
    $("#loginTab").addEventListener("click", () => switchAuthTab("login"));
    $("#registerTab").addEventListener("click", () => switchAuthTab("register"));
    $("#sendRegisterCode").addEventListener("click", sendRegisterCode);
    $("#registerButton").addEventListener("click", registerAccount);
    $("#loginButton").addEventListener("click", async () => {
        try {
            const auth = await api("/api/auth/login", {
                method: "POST",
                body: JSON.stringify({ username: $("#loginUsername").value, password: $("#loginPassword").value })
            });
            setAuth(auth);
            closeModal("loginModal");
            toast(`欢迎，${auth.nickname}`);
            loadCart();
        } catch (error) {
            $("#loginError").textContent = error.message;
        }
    });
    $("#cartCheckout").addEventListener("click", cartCheckout);
    $("#createOrderButton").addEventListener("click", showOrderConfirmation);
    $("#confirmOrderButton").addEventListener("click", createOrder);
    $("#confirmPayButton").addEventListener("click", payOrder);
    $("#submitReviewButton").addEventListener("click", submitReview);
    $("#supportForm").addEventListener("submit", (event) => { event.preventDefault(); sendSupport(); });
    $("#closeAssistant").addEventListener("click", closeAssistantPanel);
    $("#assistantForm").addEventListener("submit", (event) => { event.preventDefault(); askAssistant(); });
    initAssistantLauncher();
    $$("[data-close]").forEach((button) => button.addEventListener("click", () => closeModal(button.dataset.close)));
    $$("[data-scroll]").forEach((button) => button.addEventListener("click", () => {
        document.getElementById(button.dataset.scroll).scrollIntoView({ behavior: "smooth" });
    }));
}

renderLoginEntry();
initEvents();
loadHome();
if (state.auth) loadCart();
