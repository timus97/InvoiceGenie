import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import type {
  CreditNoteDto,
  GenerateCreditNoteRequest,
} from "@/types/ar";

export function listCreditNotes(
  tenantId: string,
  opts?: {
    status?: string;
    customerId?: string;
    availableOnly?: boolean;
    signal?: AbortSignal;
  },
) {
  return apiFetch<CreditNoteDto[]>(apiPaths.creditNotes, {
    tenantId,
    signal: opts?.signal,
    query: {
      status: opts?.status,
      customerId: opts?.customerId,
      availableOnly: opts?.availableOnly ? "true" : undefined,
    },
  });
}

/** Available ISSUED credit notes for a customer (payment UI / STORY-007). */
export function listAvailableCredits(
  tenantId: string,
  customerId: string,
  signal?: AbortSignal,
) {
  return listCreditNotes(tenantId, {
    customerId,
    availableOnly: true,
    signal,
  });
}

export function getCreditNote(
  tenantId: string,
  id: string,
  signal?: AbortSignal,
) {
  return apiFetch<CreditNoteDto>(apiPaths.creditNote(id), {
    tenantId,
    signal,
  });
}

export function generateCreditNote(
  tenantId: string,
  body: GenerateCreditNoteRequest,
) {
  return apiFetch<CreditNoteDto>(apiPaths.creditNotes, {
    method: "POST",
    tenantId,
    body,
  });
}

export function applyCreditNote(
  tenantId: string,
  id: string,
  paymentId: string,
) {
  return apiFetch<{ creditNote: CreditNoteDto; message: string }>(
    apiPaths.creditNoteApply(id),
    {
      method: "POST",
      tenantId,
      body: { paymentId },
    },
  );
}
