export const TENANT_STORAGE_KEY = "ig-tenant-id";
export const TENANT_COOKIE = "ig-tenant";

export const DEFAULT_TENANT_ID =
  process.env.NEXT_PUBLIC_DEFAULT_TENANT_ID ??
  "00000000-0000-0000-0000-000000000001";

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function isValidTenantId(value: string): boolean {
  return UUID_RE.test(value.trim());
}

export function readTenantFromStorage(): string {
  if (typeof window === "undefined") return DEFAULT_TENANT_ID;
  const stored = window.localStorage.getItem(TENANT_STORAGE_KEY);
  if (stored && isValidTenantId(stored)) return stored;
  return DEFAULT_TENANT_ID;
}

export function writeTenantToStorage(tenantId: string): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(TENANT_STORAGE_KEY, tenantId);
  // Cookie so future RSC/BFF can read it
  document.cookie = `${TENANT_COOKIE}=${encodeURIComponent(tenantId)}; path=/; SameSite=Lax`;
}