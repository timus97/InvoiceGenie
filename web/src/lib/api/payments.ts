import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import { newIdempotencyKey } from "@/lib/idempotency";
import type {
  AllocationResultDto,
  CreatePaymentRequest,
  InvoiceAllocationsDto,
  PaymentCreatedDto,
} from "@/types/ar";

export function createPayment(tenantId: string, body: CreatePaymentRequest) {
  return apiFetch<PaymentCreatedDto>(apiPaths.payments, {
    method: "POST",
    tenantId,
    body,
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
