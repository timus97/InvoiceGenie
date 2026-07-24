import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import type { TenantDto } from "@/types/ar";

export function listTenants(tenantId: string, status?: string, signal?: AbortSignal) {
  return apiFetch<TenantDto[]>(apiPaths.tenants, {
    tenantId,
    signal,
    query: { status },
  });
}

export function createTenant(
  tenantId: string,
  body: { code: string; name: string; baseCurrency?: string },
) {
  return apiFetch<TenantDto>(apiPaths.tenants, {
    method: "POST",
    tenantId,
    body,
  });
}

export function activateTenant(tenantId: string, id: string) {
  return apiFetch<TenantDto>(apiPaths.tenantActivate(id), { method: "POST", tenantId });
}

export function suspendTenant(tenantId: string, id: string) {
  return apiFetch<TenantDto>(apiPaths.tenantSuspend(id), { method: "POST", tenantId });
}