/** Browser session for STORY-003 Phase 2 login (sessionStorage — not free tenant spoofing). */

export const AUTH_SESSION_KEY = "ig-auth-session";

export type AuthSession = {
  accessToken?: string | null;
  apiKey?: string | null;
  tenantId: string;
  subject: string;
  method: string;
  roles: string[];
  expiresAt?: number | null;
};

export type LoginResponse = {
  accessToken?: string | null;
  tokenType?: string | null;
  tenantId: string;
  subject: string;
  method: string;
  roles: string[];
  expiresInSeconds?: number | null;
};

export function readAuthSession(): AuthSession | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.sessionStorage.getItem(AUTH_SESSION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as AuthSession;
    if (!parsed?.tenantId) return null;
    if (parsed.expiresAt && Date.now() > parsed.expiresAt) {
      clearAuthSession();
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function writeAuthSession(session: AuthSession): void {
  if (typeof window === "undefined") return;
  window.sessionStorage.setItem(AUTH_SESSION_KEY, JSON.stringify(session));
}

export function clearAuthSession(): void {
  if (typeof window === "undefined") return;
  window.sessionStorage.removeItem(AUTH_SESSION_KEY);
}

/** When tenant override is disabled, console requires a login session. */
export function isAuthRequired(): boolean {
  return process.env.NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE === "false";
}

export function sessionToAuthHeaders(session: AuthSession | null): Record<string, string> {
  const headers: Record<string, string> = {};
  if (!session) {
    const envKey = process.env.NEXT_PUBLIC_API_KEY?.trim();
    if (envKey) headers["X-API-Key"] = envKey;
    return headers;
  }
  if (session.accessToken) {
    headers["Authorization"] = `Bearer ${session.accessToken}`;
  } else if (session.apiKey) {
    headers["X-API-Key"] = session.apiKey;
  } else {
    const envKey = process.env.NEXT_PUBLIC_API_KEY?.trim();
    if (envKey) headers["X-API-Key"] = envKey;
  }
  return headers;
}

export async function loginRequest(body: {
  username?: string;
  password?: string;
  apiKey?: string;
}): Promise<LoginResponse> {
  const res = await fetch("/api/v1/auth/login", {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify(body),
    cache: "no-store",
  });
  if (!res.ok) {
    let message = `Login failed (${res.status})`;
    try {
      const err = (await res.json()) as { message?: string; error?: string };
      message = err.message || err.error || message;
    } catch {
      /* ignore */
    }
    throw new Error(message);
  }
  return (await res.json()) as LoginResponse;
}
