import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import type { ExchangeRateDto } from "@/types/ar";

export function listExchangeRates(tenantId: string, signal?: AbortSignal) {
  return apiFetch<ExchangeRateDto[]>(apiPaths.exchangeRates, { tenantId, signal });
}

export function createExchangeRate(
  tenantId: string,
  body: {
    fromCurrency: string;
    toCurrency: string;
    rate: number;
    effectiveDate?: string;
    source?: string;
  },
) {
  return apiFetch<ExchangeRateDto>(apiPaths.exchangeRates, {
    method: "POST",
    tenantId,
    body,
  });
}

export function convertCurrency(
  tenantId: string,
  body: { amount: number; fromCurrency: string; toCurrency: string; asOf?: string },
) {
  return apiFetch<{ amount: number; currencyCode: string }>(apiPaths.exchangeConvert, {
    method: "POST",
    tenantId,
    body,
  });
}