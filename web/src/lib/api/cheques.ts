import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import type { ChequeDto, CreateChequeRequest } from "@/types/ar";

export function listCheques(
  tenantId: string,
  opts?: { status?: string; signal?: AbortSignal },
) {
  return apiFetch<ChequeDto[]>(apiPaths.cheques, {
    tenantId,
    signal: opts?.signal,
    query: { status: opts?.status },
  });
}

export function getCheque(tenantId: string, id: string, signal?: AbortSignal) {
  return apiFetch<ChequeDto>(apiPaths.cheque(id), { tenantId, signal });
}

export function createCheque(tenantId: string, body: CreateChequeRequest) {
  return apiFetch<ChequeDto>(apiPaths.cheques, {
    method: "POST",
    tenantId,
    body,
  });
}

export function depositCheque(tenantId: string, id: string) {
  return apiFetch<ChequeDto>(apiPaths.chequeDeposit(id), {
    method: "POST",
    tenantId,
  });
}

export function clearCheque(tenantId: string, id: string) {
  return apiFetch<{ cheque: ChequeDto; ledgerEntries?: unknown[] }>(
    apiPaths.chequeClear(id),
    { method: "POST", tenantId },
  );
}

export function bounceCheque(tenantId: string, id: string, reason: string) {
  return apiFetch<{
    cheque: ChequeDto;
    reverseEntries?: unknown[];
    affectedInvoices?: string[];
  }>(apiPaths.chequeBounce(id), {
    method: "POST",
    tenantId,
    body: { reason },
  });
}
