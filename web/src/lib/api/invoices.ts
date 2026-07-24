import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import { newIdempotencyKey } from "@/lib/idempotency";
import type {
  CreateInvoiceRequest,
  InvoiceDto,
  InvoiceIdDto,
  InvoicePageDto,
  InvoiceStatus,
} from "@/types/ar";

export function listInvoices(
  tenantId: string,
  opts?: {
    limit?: number;
    cursor?: string;
    status?: InvoiceStatus | string;
    signal?: AbortSignal;
  },
) {
  return apiFetch<InvoicePageDto>(apiPaths.invoices, {
    tenantId,
    signal: opts?.signal,
    query: {
      limit: opts?.limit ?? 20,
      cursor: opts?.cursor,
      status: opts?.status,
    },
  });
}

export function getInvoice(tenantId: string, id: string, signal?: AbortSignal) {
  return apiFetch<InvoiceDto>(apiPaths.invoice(id), { tenantId, signal });
}

export function createInvoice(
  tenantId: string,
  body: CreateInvoiceRequest,
  idempotencyKey?: string,
) {
  return apiFetch<InvoiceIdDto>(apiPaths.invoices, {
    method: "POST",
    tenantId,
    body,
    idempotencyKey: idempotencyKey ?? newIdempotencyKey(),
  });
}

export function issueInvoice(tenantId: string, id: string) {
  return apiFetch<InvoiceDto>(apiPaths.invoiceIssue(id), {
    method: "POST",
    tenantId,
  });
}

export function markInvoiceOverdue(
  tenantId: string,
  id: string,
  today?: string,
) {
  return apiFetch<InvoiceDto>(apiPaths.invoiceOverdue(id), {
    method: "POST",
    tenantId,
    query: today ? { today } : undefined,
  });
}

export function writeOffInvoice(
  tenantId: string,
  id: string,
  reason: string,
) {
  return apiFetch<InvoiceDto>(apiPaths.invoiceWriteOff(id), {
    method: "POST",
    tenantId,
    body: { reason },
  });
}

export function applyInvoicePayment(
  tenantId: string,
  id: string,
  opts?: { fullyPaid?: boolean; amount?: number },
) {
  return apiFetch<InvoiceDto>(apiPaths.invoicePayment(id), {
    method: "POST",
    tenantId,
    body: {
      fullyPaid: opts?.fullyPaid ?? opts?.amount == null,
      amount: opts?.amount,
    },
  });
}

export function updateInvoiceDueDate(
  tenantId: string,
  id: string,
  dueDate: string,
) {
  return apiFetch<InvoiceDto>(apiPaths.invoiceDueDate(id), {
    method: "PATCH",
    tenantId,
    body: { dueDate },
  });
}
