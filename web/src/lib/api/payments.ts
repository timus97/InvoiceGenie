import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import { newIdempotencyKey } from "@/lib/idempotency";
import type {
  AllocationResultDto,
  CreatePaymentRequest,
  InvoiceAllocationsDto,
  PaymentCreatedDto,
  PaymentDto,
  PaymentListDto,
  PaymentReversalDto,
} from "@/types/ar";

export function createPayment(tenantId: string, body: CreatePaymentRequest) {
  return apiFetch<PaymentCreatedDto>(apiPaths.payments, {
    method: "POST",
    tenantId,
    body,
  });
}

export function listPayments(
  tenantId: string,
  opts?: {
    customerId?: string;
    status?: string;
    unallocatedOnly?: boolean;
    limit?: number;
    signal?: AbortSignal;
  },
) {
  const q = new URLSearchParams();
  if (opts?.customerId) q.set("customerId", opts.customerId);
  if (opts?.status) q.set("status", opts.status);
  if (opts?.unallocatedOnly) q.set("unallocatedOnly", "true");
  if (opts?.limit) q.set("limit", String(opts.limit));
  const qs = q.toString();
  return apiFetch<PaymentListDto>(
    `${apiPaths.payments}${qs ? `?${qs}` : ""}`,
    { tenantId, signal: opts?.signal },
  );
}

export function getPayment(
  tenantId: string,
  paymentId: string,
  signal?: AbortSignal,
) {
  return apiFetch<PaymentDto>(apiPaths.payment(paymentId), {
    tenantId,
    signal,
  });
}

export function reversePayment(
  tenantId: string,
  paymentId: string,
  reason?: string,
  idempotencyKey?: string,
) {
  return apiFetch<PaymentReversalDto>(apiPaths.paymentReverse(paymentId), {
    method: "POST",
    tenantId,
    body: { reason: reason ?? "Reversed via UI" },
    idempotencyKey: idempotencyKey ?? newIdempotencyKey(),
  });
}

export function refundPayment(
  tenantId: string,
  paymentId: string,
  reason?: string,
  idempotencyKey?: string,
) {
  return apiFetch<PaymentReversalDto>(apiPaths.paymentRefund(paymentId), {
    method: "POST",
    tenantId,
    body: { reason: reason ?? "Refunded via UI" },
    idempotencyKey: idempotencyKey ?? newIdempotencyKey(),
  });
}

export function allocateFifo(
  tenantId: string,
  paymentId: string,
  allocatedBy?: string,
  idempotencyKey?: string,
) {
  return apiFetch<AllocationResultDto>(apiPaths.paymentAllocateFifo(paymentId), {
    method: "POST",
    tenantId,
    body: { allocatedBy: allocatedBy ?? undefined },
    idempotencyKey: idempotencyKey ?? newIdempotencyKey(),
  });
}

export function allocateManual(
  tenantId: string,
  paymentId: string,
  allocations: { invoiceId: string; amount: number; notes?: string }[],
  allocatedBy?: string,
  idempotencyKey?: string,
) {
  return apiFetch<AllocationResultDto>(
    apiPaths.paymentAllocateManual(paymentId),
    {
      method: "POST",
      tenantId,
      body: { allocatedBy: allocatedBy ?? undefined, allocations },
      idempotencyKey: idempotencyKey ?? newIdempotencyKey(),
    },
  );
}

export function getPaymentAllocations(
  tenantId: string,
  paymentId: string,
  signal?: AbortSignal,
) {
  return apiFetch<AllocationResultDto>(apiPaths.paymentAllocations(paymentId), {
    tenantId,
    signal,
  });
}

export function getInvoiceAllocations(
  tenantId: string,
  invoiceId: string,
  signal?: AbortSignal,
) {
  return apiFetch<InvoiceAllocationsDto>(
    apiPaths.invoiceAllocations(invoiceId),
    { tenantId, signal },
  );
}
