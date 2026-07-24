import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import type {
  AgingBucketDto,
  AgingReportDto,
  DiscountRequest,
  DiscountResponseDto,
} from "@/types/ar";

export function getAgingReport(
  tenantId: string,
  asOfDate?: string,
  signal?: AbortSignal,
) {
  return apiFetch<AgingReportDto>(apiPaths.aging, {
    tenantId,
    signal,
    query: { asOfDate },
  });
}

export function getAgingBuckets(tenantId: string, signal?: AbortSignal) {
  return apiFetch<AgingBucketDto[]>(apiPaths.agingBuckets, {
    tenantId,
    signal,
  });
}

export function calculateDiscount(tenantId: string, body: DiscountRequest) {
  return apiFetch<DiscountResponseDto>(apiPaths.agingDiscount, {
    method: "POST",
    tenantId,
    body,
  });
}
