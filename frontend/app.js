const CATALOG_API = "https://catalog-service-hlbrzrqapa-uc.a.run.app/api/catalog/products";
const INVENTORY_API = "https://inventory-service-hlbrzrqapa-uc.a.run.app/api/inventory";
const ORDERS_API = "https://orders-service-hlbrzrqapa-uc.a.run.app/api/orders";
const PAYMENTS_API = "https://payments-service-hlbrzrqapa-uc.a.run.app/api/payments";

const grid = document.getElementById("products-grid");
const ordersList = document.getElementById("orders-list");
const paymentsList = document.getElementById("payments-list");

const catalogDot = document.getElementById("catalog-dot");
const catalogStatus = document.getElementById("catalog-status");
const inventoryDot = document.getElementById("inventory-dot");
const inventoryStatus = document.getElementById("inventory-status");
const ordersDot = document.getElementById("orders-dot");
const ordersStatus = document.getElementById("orders-status");
const paymentsDot = document.getElementById("payments-dot");
const paymentsStatus = document.getElementById("payments-status");

const customerEmailInput = document.getElementById("customer-email");
const paymentMethodSelect = document.getElementById("payment-method");
const actionFeedback = document.getElementById("action-feedback");
const form = document.getElementById("product-form");
const submitBtn = document.getElementById("submit-btn");
const feedback = document.getElementById("form-feedback");

function setBadge(dotEl, textEl, online, message) {
  dotEl.className = "dot " + (online ? "online" : "offline");
  textEl.textContent = message;
}

function setActionFeedback(type, message) {
  actionFeedback.className = "feedback" + (type ? " " + type : "");
  actionFeedback.textContent = message;
}

function createStockBadge(stockItem) {
  const badge = document.createElement("span");
  badge.className = "badge ";
  if (!stockItem) {
    badge.className += "badge-none";
    badge.textContent = "No stock record";
    return badge;
  }
  if (stockItem.availableQuantity === 0) {
    badge.className += "badge-out";
    badge.textContent = "Out of stock";
  } else if (stockItem.availableQuantity <= 5) {
    badge.className += "badge-low";
    badge.textContent = `Low stock: ${stockItem.availableQuantity}`;
  } else {
    badge.className += "badge-ok";
    badge.textContent = `In stock: ${stockItem.availableQuantity}`;
  }
  return badge;
}

function createProductCard(product, stockItem) {
  const card = document.createElement("article");
  card.className = "card";

  const top = document.createElement("div");
  top.className = "card-top";

  const sku = document.createElement("span");
  sku.className = "sku";
  sku.textContent = product.sku;

  top.append(sku, createStockBadge(stockItem));

  const title = document.createElement("h3");
  title.textContent = product.name;

  const desc = document.createElement("p");
  desc.textContent = product.description || "No description provided.";

  const stockRow = document.createElement("div");
  stockRow.className = "stock-controls";

  const reservedText = document.createElement("span");
  reservedText.textContent = stockItem
    ? `Reserved: ${stockItem.reservedQuantity}`
    : "Stock uninitialized";

  const actions = document.createElement("div");
  actions.className = "stock-actions";

  if (stockItem) {
    const reserveBtn = document.createElement("button");
    reserveBtn.className = "btn-subtle";
    reserveBtn.type = "button";
    reserveBtn.textContent = "+1 Res";
    reserveBtn.title = "Reserve 1 unit directly in inventory-service";
    reserveBtn.disabled = stockItem.availableQuantity < 1;
    reserveBtn.addEventListener("click", () => updateReservation(product.sku, "reserve", reserveBtn));

    const releaseBtn = document.createElement("button");
    releaseBtn.className = "btn-subtle";
    releaseBtn.type = "button";
    releaseBtn.textContent = "-1 Rel";
    releaseBtn.title = "Release 1 unit directly in inventory-service";
    releaseBtn.disabled = stockItem.reservedQuantity < 1;
    releaseBtn.addEventListener("click", () => updateReservation(product.sku, "release", releaseBtn));

    actions.append(reserveBtn, releaseBtn);
  } else {
    const initBtn = document.createElement("button");
    initBtn.className = "btn-subtle";
    initBtn.type = "button";
    initBtn.textContent = "Init 10 units";
    initBtn.addEventListener("click", () => initializeStock(product.id, 10, initBtn));
    actions.append(initBtn);
  }

  stockRow.append(reservedText, actions);

  const orderRow = document.createElement("div");
  orderRow.className = "order-row";

  const qtyInput = document.createElement("input");
  qtyInput.className = "qty-input";
  qtyInput.type = "number";
  qtyInput.min = "1";
  qtyInput.max = stockItem ? String(Math.max(1, stockItem.availableQuantity)) : "1";
  qtyInput.value = "1";
  qtyInput.disabled = !stockItem || stockItem.availableQuantity < 1;

  const orderBtn = document.createElement("button");
  orderBtn.className = "btn-order";
  orderBtn.type = "button";
  orderBtn.textContent = "Place Order";
  orderBtn.disabled = !stockItem || stockItem.availableQuantity < 1;
  orderBtn.addEventListener("click", () => placeOrder(product.id, Number(qtyInput.value), orderBtn));

  orderRow.append(qtyInput, orderBtn);

  const footer = document.createElement("div");
  footer.className = "card-footer";

  const price = document.createElement("span");
  price.className = "price";
  price.textContent = `$${Number(product.price).toFixed(2)}`;

  const delBtn = document.createElement("button");
  delBtn.className = "btn-danger";
  delBtn.type = "button";
  delBtn.textContent = "Delete";
  delBtn.addEventListener("click", () => deleteProduct(product.id, delBtn));

  footer.append(price, delBtn);
  card.append(top, title, desc, stockRow, orderRow, footer);
  return card;
}

function createOrderRow(order) {
  const row = document.createElement("div");
  row.className = "record-item";

  const meta = document.createElement("div");
  meta.className = "record-meta";

  const title = document.createElement("div");
  title.className = "record-title";

  const idSpan = document.createElement("span");
  idSpan.textContent = `Order #${order.id}`;

  const skuSpan = document.createElement("span");
  skuSpan.className = "sku";
  skuSpan.textContent = order.sku;

  const statusBadge = document.createElement("span");
  if (order.status === "PAID") {
    statusBadge.className = "badge badge-ok";
  } else if (order.status === "CONFIRMED") {
    statusBadge.className = "badge badge-info";
  } else {
    statusBadge.className = "badge badge-out";
  }
  statusBadge.textContent = order.status;

  title.append(idSpan, skuSpan, statusBadge);

  const sub = document.createElement("div");
  sub.className = "record-sub";
  sub.textContent = `${order.productName} × ${order.quantity} ($${Number(order.unitPrice).toFixed(2)} ea) • ${order.customerEmail}`;

  meta.append(title, sub);

  const right = document.createElement("div");
  right.className = "record-right";

  const total = document.createElement("span");
  total.className = "price";
  total.textContent = `$${Number(order.totalAmount).toFixed(2)}`;

  right.append(total);

  if (order.status === "CONFIRMED") {
    const payBtn = document.createElement("button");
    payBtn.className = "btn-pay";
    payBtn.type = "button";
    payBtn.textContent = "Pay";
    payBtn.addEventListener("click", () => payOrder(order.id, payBtn));

    const cancelBtn = document.createElement("button");
    cancelBtn.className = "btn-danger";
    cancelBtn.type = "button";
    cancelBtn.textContent = "Cancel";
    cancelBtn.addEventListener("click", () => cancelOrder(order.id, cancelBtn));

    right.append(payBtn, cancelBtn);
  }

  row.append(meta, right);
  return row;
}

function createPaymentRow(payment) {
  const row = document.createElement("div");
  row.className = "record-item";

  const meta = document.createElement("div");
  meta.className = "record-meta";

  const title = document.createElement("div");
  title.className = "record-title";

  const refSpan = document.createElement("span");
  refSpan.className = "sku";
  refSpan.textContent = payment.transactionRef;

  const orderSpan = document.createElement("span");
  orderSpan.textContent = `Order #${payment.orderId}`;

  const statusBadge = document.createElement("span");
  if (payment.status === "CAPTURED") {
    statusBadge.className = "badge badge-ok";
  } else if (payment.status === "REFUNDED") {
    statusBadge.className = "badge badge-low";
  } else {
    statusBadge.className = "badge badge-out";
  }
  statusBadge.textContent = payment.status;

  title.append(refSpan, orderSpan, statusBadge);

  const sub = document.createElement("div");
  sub.className = "record-sub";
  sub.textContent = `${payment.paymentMethod} • ${payment.currency} • ${payment.customerEmail}`;

  meta.append(title, sub);

  const right = document.createElement("div");
  right.className = "record-right";

  const amount = document.createElement("span");
  amount.className = "price";
  amount.textContent = `$${Number(payment.amount).toFixed(2)}`;

  right.append(amount);

  if (payment.status === "CAPTURED") {
    const refundBtn = document.createElement("button");
    refundBtn.className = "btn-warn";
    refundBtn.type = "button";
    refundBtn.textContent = "Refund";
    refundBtn.addEventListener("click", () => refundPayment(payment.id, refundBtn));
    right.append(refundBtn);
  }

  row.append(meta, right);
  return row;
}

async function fetchJson(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function loadStorefront() {
  const [catalogResult, inventoryResult, ordersResult, paymentsResult] = await Promise.allSettled([
    fetchJson(CATALOG_API),
    fetchJson(INVENTORY_API),
    fetchJson(ORDERS_API),
    fetchJson(PAYMENTS_API)
  ]);

  const inventoryBySku = new Map();
  if (inventoryResult.status === "fulfilled") {
    inventoryResult.value.forEach((item) => inventoryBySku.set(item.sku, item));
    setBadge(inventoryDot, inventoryStatus, true, `Inventory: ${inventoryResult.value.length} SKUs`);
  } else {
    setBadge(inventoryDot, inventoryStatus, false, "Inventory: Offline");
  }

  if (catalogResult.status === "rejected") {
    setBadge(catalogDot, catalogStatus, false, "Catalog: Offline");
    grid.replaceChildren();
    const empty = document.createElement("div");
    empty.className = "empty";
    empty.textContent = `Could not load catalog: ${catalogResult.reason.message}`;
    grid.appendChild(empty);
  } else {
    const products = catalogResult.value;
    setBadge(catalogDot, catalogStatus, true, `Catalog: ${products.length}`);
    grid.replaceChildren();

    if (products.length === 0) {
      const empty = document.createElement("div");
      empty.className = "empty";
      empty.textContent = "No active products found. Add one using the form.";
      grid.appendChild(empty);
    } else {
      products.forEach((p) => grid.appendChild(createProductCard(p, inventoryBySku.get(p.sku))));
    }
  }

  if (ordersResult.status === "rejected") {
    setBadge(ordersDot, ordersStatus, false, "Orders: Offline");
    ordersList.replaceChildren();
    const empty = document.createElement("div");
    empty.className = "empty";
    empty.textContent = `Could not load orders: ${ordersResult.reason.message}`;
    ordersList.appendChild(empty);
  } else {
    const orders = ordersResult.value;
    setBadge(ordersDot, ordersStatus, true, `Orders: ${orders.length}`);
    ordersList.replaceChildren();

    if (orders.length === 0) {
      const empty = document.createElement("div");
      empty.className = "empty";
      empty.textContent = "No orders yet. Click 'Place Order' on any in-stock product.";
      ordersList.appendChild(empty);
    } else {
      orders.forEach((o) => ordersList.appendChild(createOrderRow(o)));
    }
  }

  if (paymentsResult.status === "rejected") {
    setBadge(paymentsDot, paymentsStatus, false, "Payments: Offline");
    paymentsList.replaceChildren();
    const empty = document.createElement("div");
    empty.className = "empty";
    empty.textContent = `Could not load payments: ${paymentsResult.reason.message}`;
    paymentsList.appendChild(empty);
  } else {
    const payments = paymentsResult.value;
    setBadge(paymentsDot, paymentsStatus, true, `Payments: ${payments.length}`);
    paymentsList.replaceChildren();

    if (payments.length === 0) {
      const empty = document.createElement("div");
      empty.className = "empty";
      empty.textContent = "No payments yet. Click 'Pay' on any CONFIRMED order.";
      paymentsList.appendChild(empty);
    } else {
      payments.forEach((p) => paymentsList.appendChild(createPaymentRow(p)));
    }
  }
}

async function placeOrder(productId, quantity, btn) {
  const customerEmail = customerEmailInput.value.trim();
  if (!customerEmail || !customerEmail.includes("@")) {
    setActionFeedback("error", "Please enter a valid customer email first.");
    customerEmailInput.focus();
    return;
  }
  if (!Number.isInteger(quantity) || quantity < 1) {
    setActionFeedback("error", "Quantity must be at least 1.");
    return;
  }

  btn.disabled = true;
  btn.textContent = "Ordering...";
  setActionFeedback("", "Orchestrating Catalog → Inventory → Orders...");

  try {
    const res = await fetch(ORDERS_API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ customerEmail, productId, quantity })
    });
    if (!res.ok) {
      const msg = await res.text();
      throw new Error(msg || `HTTP ${res.status}`);
    }
    const created = await res.json();
    setActionFeedback("ok", `Order #${created.id} confirmed ($${Number(created.totalAmount).toFixed(2)}) — ready to pay!`);
    await loadStorefront();
  } catch (err) {
    setActionFeedback("error", `Order failed: ${err.message}`);
    btn.disabled = false;
    btn.textContent = "Place Order";
  }
}

async function payOrder(orderId, btn) {
  const paymentMethod = paymentMethodSelect.value;
  btn.disabled = true;
  btn.textContent = "Paying...";
  setActionFeedback("", `Processing ${paymentMethod} payment for Order #${orderId}...`);

  try {
    const res = await fetch(PAYMENTS_API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ orderId, paymentMethod })
    });

    if (res.status === 402) {
      setActionFeedback("error", `Payment DECLINED (HTTP 402) for Order #${orderId}. Recorded as FAILED.`);
      await loadStorefront();
      return;
    }
    if (!res.ok) {
      const msg = await res.text();
      throw new Error(msg || `HTTP ${res.status}`);
    }
    const payment = await res.json();
    setActionFeedback("ok", `Payment ${payment.transactionRef} captured — Order #${orderId} is now PAID!`);
    await loadStorefront();
  } catch (err) {
    setActionFeedback("error", `Payment failed: ${err.message}`);
    alert(`Payment failed: ${err.message}`);
    btn.disabled = false;
    btn.textContent = "Pay";
  }
}

async function refundPayment(paymentId, btn) {
  btn.disabled = true;
  btn.textContent = "Refunding...";
  setActionFeedback("", `Refunding Payment #${paymentId} (Payments → Orders → Inventory)...`);

  try {
    const res = await fetch(`${PAYMENTS_API}/${paymentId}/refund`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "{}"
    });
    if (!res.ok) {
      const msg = await res.text();
      throw new Error(msg || `HTTP ${res.status}`);
    }
    const refunded = await res.json();
    setActionFeedback("ok", `Payment ${refunded.transactionRef} refunded — Order #${refunded.orderId} cancelled & stock released.`);
    await loadStorefront();
  } catch (err) {
    setActionFeedback("error", `Refund failed: ${err.message}`);
    alert(`Refund failed: ${err.message}`);
    btn.disabled = false;
    btn.textContent = "Refund";
  }
}

async function cancelOrder(orderId, btn) {
  btn.disabled = true;
  btn.textContent = "Cancelling...";
  setActionFeedback("", `Cancelling Order #${orderId} and releasing stock...`);

  try {
    const res = await fetch(`${ORDERS_API}/${orderId}/cancel`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "{}"
    });
    if (!res.ok) {
      const msg = await res.text();
      throw new Error(msg || `HTTP ${res.status}`);
    }
    setActionFeedback("ok", `Order #${orderId} cancelled — stock released back to inventory.`);
    await loadStorefront();
  } catch (err) {
    setActionFeedback("error", `Cancel failed: ${err.message}`);
    alert(`Cancel failed: ${err.message}`);
    btn.disabled = false;
    btn.textContent = "Cancel";
  }
}

async function updateReservation(sku, action, btn) {
  btn.disabled = true;
  try {
    const res = await fetch(`${INVENTORY_API}/${encodeURIComponent(sku)}/${action}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ quantity: 1 })
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    await loadStorefront();
  } catch (err) {
    setActionFeedback("error", `Failed to ${action} stock: ${err.message}`);
    btn.disabled = false;
  }
}

async function initializeStock(productId, quantity, btn) {
  btn.disabled = true;
  try {
    const res = await fetch(INVENTORY_API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ productId, availableQuantity: quantity })
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    await loadStorefront();
  } catch (err) {
    setActionFeedback("error", `Failed to initialize stock: ${err.message}`);
    btn.disabled = false;
  }
}

async function deleteProduct(id, btn) {
  btn.disabled = true;
  btn.textContent = "Removing...";
  try {
    const res = await fetch(`${CATALOG_API}/${id}`, { method: "DELETE" });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    await loadStorefront();
  } catch (err) {
    btn.disabled = false;
    btn.textContent = "Delete";
    setActionFeedback("error", `Failed to delete product: ${err.message}`);
  }
}

form.addEventListener("submit", async (e) => {
  e.preventDefault();
  submitBtn.disabled = true;
  feedback.className = "feedback";
  feedback.textContent = "Saving product & stock...";

  const data = new FormData(form);
  const payload = {
    sku: data.get("sku").trim(),
    name: data.get("name").trim(),
    description: data.get("description").trim(),
    price: Number(data.get("price"))
  };
  const initialStock = Number(data.get("initialStock"));

  try {
    const res = await fetch(CATALOG_API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error(`HTTP ${res.status} (check duplicate SKU)`);
    const created = await res.json();

    const invRes = await fetch(INVENTORY_API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ productId: created.id, availableQuantity: initialStock })
    });
    if (!invRes.ok) throw new Error(`Product saved, but stock failed (HTTP ${invRes.status})`);

    form.reset();
    feedback.className = "feedback ok";
    feedback.textContent = "Product and inventory created!";
    await loadStorefront();
  } catch (err) {
    feedback.className = "feedback error";
    feedback.textContent = `Error: ${err.message}`;
  } finally {
    submitBtn.disabled = false;
  }
});

loadStorefront();