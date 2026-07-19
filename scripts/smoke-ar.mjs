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

  console.log("SMOKE OK");
}

main().catch((e) => {
  console.error("SMOKE FAIL", e.message || e);
  process.exit(1);
});