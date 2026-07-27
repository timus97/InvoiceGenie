/**
 * Pure auth cookie/session helpers (no Next.js imports — safe for unit tests).
 */

export const COOKIE_SESSION_ID = "ig_sid";
export const COOKIE_ACCESS_TOKEN = "ig_at";
export const COOKIE_API_KEY = "ig_ak";
export const COOKIE_SESSION = "ig_sess";

export type PublicSession = {
  tenantId: string;
  subject: string;
  method: string;
  roles: string[];
  expiresAt?: number | null;
  email?: string | null;
  displayName?: string | null;
  userId?: string | null;
};

export type BackendLoginResponse = {
  accessToken?: string | null;
  refreshToken?: string | null;
  tokenType?: string | null;
  tenantId: string;
  subject: string;
  method: string;
  roles: string[];
  expiresInSeconds?: number | null;
  refreshExpiresInSeconds?: number | null;
  userId?: string | null;
  displayName?: string | null;
  email?: string | null;
};

export function backendBaseUrl(): string {
  return (process.env.BACKEND_URL ?? "http://localhost:8082").replace(/\/$/, "");
}

export function isSecureCookie(): boolean {
  return process.env.NODE_ENV === "production";
}

export function cookieMaxAgeSeconds(expiresInSeconds?: number | null): number {
  if (expiresInSeconds != null && expiresInSeconds > 0) {
    return Math.min(Math.floor(expiresInSeconds), 60 * 60 * 24 * 7);
  }
  // Default cookie lifetime aligns with refresh token (7d)
  return 60 * 60 * 24 * 7;
}

export function publicSessionFromLogin(
  res: BackendLoginResponse,
): PublicSession {
  const expiresAt =
    res.refreshExpiresInSeconds != null
      ? Date.now() + res.refreshExpiresInSeconds * 1000
      : res.expiresInSeconds != null
        ? Date.now() + res.expiresInSeconds * 1000
        : null;
  return {
    tenantId: res.tenantId,
    subject: res.subject,
    method: res.method,
    roles: Array.isArray(res.roles) ? res.roles : [],
    expiresAt,
    email: res.email ?? null,
    displayName: res.displayName ?? null,
    userId: res.userId ?? null,
  };
}

export function stripSecrets<T extends Record<string, unknown>>(
  body: T,
): Record<string, unknown> {
  const out: Record<string, unknown> = { ...body };
  delete out.accessToken;
  delete out.refreshToken;
  delete out.apiKey;
  delete out.password;
  delete out.tokenType;
  return out;
}

export type AuthCookieBundle = {
  sessionId?: string;
  accessToken?: string;
  refreshToken?: string;
  apiKey?: string;
  session: PublicSession | null;
};

export function parseSessionCookie(raw: string | undefined): PublicSession | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as PublicSession;
    if (!parsed?.tenantId || !parsed?.subject) return null;
    if (parsed.expiresAt && Date.now() > parsed.expiresAt) return null;
    return {
      tenantId: parsed.tenantId,
      subject: parsed.subject,
      method: parsed.method || "unknown",
      roles: Array.isArray(parsed.roles) ? parsed.roles : [],
      expiresAt: parsed.expiresAt ?? null,
      email: parsed.email ?? null,
      displayName: parsed.displayName ?? null,
      userId: parsed.userId ?? null,
    };
  } catch {
    return null;
  }
}

export function hasCredentialCookies(bundle: AuthCookieBundle): boolean {
  return Boolean(bundle.accessToken || bundle.apiKey);
}

export function buildUpstreamAuthHeaders(
  bundle: AuthCookieBundle,
): Record<string, string> {
  const headers: Record<string, string> = {};
  if (bundle.accessToken) {
    headers.Authorization = `Bearer ${bundle.accessToken}`;
  } else if (bundle.apiKey) {
    headers["X-API-Key"] = bundle.apiKey;
  }
  if (bundle.session?.tenantId) {
    headers["X-Tenant-Id"] = bundle.session.tenantId;
  }
  return headers;
}

export type CookieWriteOptions = {
  sessionId: string;
  maxAge: number;
};

export function buildSetCookieHeaders(opts: CookieWriteOptions): string[] {
  const secure = isSecureCookie();
  const base = `Path=/; HttpOnly; SameSite=Lax; Max-Age=${opts.maxAge}${
    secure ? "; Secure" : ""
  }`;
  return [
    `${COOKIE_SESSION_ID}=${encodeURIComponent(opts.sessionId)}; ${base}`,
  ];
}

export function buildClearCookieHeaders(): string[] {
  const secure = isSecureCookie();
  const base = `Path=/; HttpOnly; SameSite=Lax; Max-Age=0${secure ? "; Secure" : ""}`;
  return [
    `${COOKIE_SESSION_ID}=; ${base}`,
    `${COOKIE_ACCESS_TOKEN}=; ${base}`,
    `${COOKIE_API_KEY}=; ${base}`,
    `${COOKIE_SESSION}=; ${base}`,
  ];
}