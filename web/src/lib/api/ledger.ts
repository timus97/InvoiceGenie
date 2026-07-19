import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import type {
  LedgerAccountDto,
  LedgerBalanceDto,
  LedgerEntryDto,
} from "@/types/ar";

export function listLedgerAccounts(tenantId: string, signal?: AbortSignal) {
  return apiFetch<LedgerAccountDto[]>(apiPaths.ledgerAccounts, {
    tenantId,
    signal,
  });
}

export function getLedgerBalance(
  tenantId: string,
  account: string,
  currency = "USD",
  signal?: AbortSignal,
) {
  return apiFetch<LedgerBalanceDto>(apiPaths.ledgerBalance(account), {
    tenantId,
    signal,
    query: { currency },
  });
}

export function getLedgerByReference(
  tenantId: string,
  type: string,
  id: string,
  signal?: AbortSignal,
) {
  return apiFetch<LedgerEntryDto[]>(apiPaths.ledgerReference(type, id), {
    tenantId,
    signal,
  });
}

export function getLedgerTransaction(
  tenantId: string,
  transactionId: string,
  signal?: AbortSignal,
) {
  return apiFetch<{
    transactionId: string;
    entries: LedgerEntryDto[];
    balanced: boolean;
    createdAt?: string;
  }>(apiPaths.ledgerTransaction(transactionId), { tenantId, signal });
}
