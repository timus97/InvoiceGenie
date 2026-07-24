import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import type { AuditDto } from "@/types/ar";

export function listAudit(tenantId: string, limit = 100, signal?: AbortSignal) {
  return apiFetch<AuditDto[]>(apiPaths.audit, {
    tenantId,
    signal,
    query: { limit },
  });
}

export async function exportAuditCsv(tenantId: string, limit = 500): Promise<string> {
  const headers: Record<string, string> = {
    Accept: "text/csv",
    "X-Tenant-Id": tenantId,
  };
  const apiKey = process.env.NEXT_PUBLIC_API_KEY?.trim();
  if (apiKey) headers["X-API-Key"] = apiKey;
  const res = await fetch(`${apiPaths.auditExport}?limit=${limit}`, {
    headers,
    cache: "no-store",
  });
  if (!res.ok) throw new Error(`Audit export failed: ${res.status}`);
  return res.text();
}