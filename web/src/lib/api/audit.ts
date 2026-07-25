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
  // Auth via httpOnly cookies (BFF) — no Authorization / X-API-Key from the browser.
  const headers: Record<string, string> = {
    Accept: "text/csv",
    "X-Tenant-Id": tenantId,
  };
  const res = await fetch(`${apiPaths.auditExport}?limit=${limit}`, {
    headers,
    cache: "no-store",
    credentials: "include",
  });
  if (res.status === 401 && typeof window !== "undefined") {
    window.location.assign(
      `/login?next=${encodeURIComponent(window.location.pathname)}`,
    );
  }
  if (!res.ok) throw new Error(`Audit export failed: ${res.status}`);
  return res.text();
}