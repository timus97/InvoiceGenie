/**
 * API smoke against running Quarkus (:8080).
 * Usage: node scripts/smoke-ar.mjs
 */
const BASE = process.env.API_BASE || "http://localhost:8080";
const TENANT =
  process.env.TENANT_ID || "00000000-0000-0000-0000-000000000001";

async function req(method, path, body) {
  const headers = {
    Accept: "application/json",
    "X-Tenant-Id": TENANT,
  };
  if (body) headers["Content-Type"] = "application/json";
  if (method === "POST" && path.includes("/invoices") && !path.includes("/")) {
    headers["Idempotency-Key"] = crypto.randomUUID();
  }
  if (path.includes("/allocate/")) {
    headers["Idempotency-Key"] = crypto.randomUUID();
  }
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }
  if (!res.ok) {
    throw new Error(`${method} ${path} -> ${res.status} ${text}`);
  }
  return data;
}

async function main() {
  console.log("Smoke tenant", TENANT, "base", BASE);
  const code = `SMOKE-${Date.now().toString(36).toUpperCase()}`;
  const customer = await req("POST", "/api/v1/customers", {
    customerCode: code,
    legalName: "Smoke Test Co",
    currency: "USD",
  });
  console.log("customer", customer.id || customer);

  const invNum = `INV-${Date.now().toString(36).toUpperCase()}`;
  const invCreated = await req("POST", "/api/v1/invoices", {
    invoiceNumber: invNum,
    customerId: customer.id,
    currencyCode: "USD",
    dueDate: new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10),
    lines: [{ sequence: 1, description: "Smoke line", amount: 100 }],
  });
  console.log("invoice", invCreated.id);

  const payNum = `PAY-${Date.now().toString(36).toUpperCase()}`;
  const pay = await req("POST", "/api/v1/payments", {
    paymentNumber: payNum,
    customerId: customer.id,
    amount: 100,
    currencyCode: "USD",
    paymentDate: new Date().toISOString().slice(0, 10),
    method: "BANK_TRANSFER",
  });
  console.log("payment", pay.id);

  const alloc = await req("POST", `/api/v1/payments/${pay.id}/allocate/fifo`, {
    allocatedBy: null,
  });
  console.log("allocated", alloc.totalAllocated, "full?", alloc.fullyAllocated);

  const aging = await req("GET", "/api/v1/aging");
  console.log("aging total", aging.grandTotal);

  // Wave A probes
  const payments = await req("GET", "/api/v1/payments?limit=5");
  console.log("payments list", Array.isArray(payments) ? payments.length : payments?.items?.length ?? "ok");

  const payGet = await req("GET", `/api/v1/payments/${pay.id}`);
  console.log("payment get", payGet.id || payGet.paymentNumber || "ok");

  // reverse a fresh unallocated payment
  const revPay = await req("POST", "/api/v1/payments", {
    paymentNumber: `PAY-REV-${Date.now().toString(36).toUpperCase()}`,
    customerId: customer.id,
    amount: 25,
    currencyCode: "USD",
    paymentDate: new Date().toISOString().slice(0, 10),
    method: "BANK_TRANSFER",
  });
  const rev = await req("POST", `/api/v1/payments/${revPay.id}/reverse`, {
    reason: "smoke reverse",
  });
  console.log("reverse status", rev.status || rev);

  // cheque clear → paymentId
  const chq = await req("POST", "/api/v1/cheques", {
    chequeNumber: `CHQ-${Date.now().toString(36).toUpperCase()}`,
    customerId: customer.id,
    amount: 40,
    currencyCode: "USD",
    bankName: "Smoke Bank",
    chequeDate: "2026-03-20",
  });
  await req("POST", `/api/v1/cheques/${chq.id}/deposit`);
  const cleared = await req("POST", `/api/v1/cheques/${chq.id}/clear`);
  const linkedPay =
    cleared.paymentId || cleared.cheque?.paymentId || null;
  if (!linkedPay) {
    throw new Error("cheque clear paymentId was null");
  }
  console.log("cheque clear paymentId", linkedPay);

  // blocked customer 409
  await req("POST", `/api/v1/customers/${customer.id}/block`);
  const blockedRes = await fetch(`${BASE}/api/v1/invoices`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      "X-Tenant-Id": TENANT,
      "Idempotency-Key": crypto.randomUUID(),
    },
    body: JSON.stringify({
      invoiceNumber: `INV-BLK-${Date.now().toString(36).toUpperCase()}`,
      customerId: customer.id,
      currencyCode: "USD",
      dueDate: new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10),
      lines: [{ sequence: 1, description: "blocked", amount: 10 }],
    }),
  });
  if (blockedRes.status !== 409) {
    throw new Error(`expected 409 for blocked customer, got ${blockedRes.status}`);
  }
  const blockedBody = await blockedRes.text();
  if (!blockedBody.includes("CUSTOMER_NOT_INVOICEABLE")) {
    throw new Error(`expected CUSTOMER_NOT_INVOICEABLE, got ${blockedBody}`);
  }
  console.log("blocked customer 409 CUSTOMER_NOT_INVOICEABLE ok");
  await req("POST", `/api/v1/customers/${customer.id}/unblock`);

  console.log("SMOKE OK");
}

main().catch((e) => {
  console.error("SMOKE FAIL", e.message || e);
  process.exit(1);
});