/**
 * Browser-safe auth session helpers. Tokens never leave the BFF.
 */

export type AuthSession = {
  tenantId: string;
  subject: string;
  method: string;
  roles: string[];
  expiresAt?: number | null;
  email?: string | null;
  displayName?: string | null;
  userId?: string | null;
};

export type LoginResponse = {
  tenantId: string;
  subject: string;
  method: string;
  roles: string[];
  expiresInSeconds?: number | null;
  refreshExpiresInSeconds?: number | null;
  expiresAt?: number | null;
  email?: string | null;
  displayName?: string | null;
  userId?: string | null;
};

export function isAuthRequired(): boolean {
  return process.env.NEXT_PUBLIC_AUTH_REQUIRED !== "false";
}

export async function fetchSession(): Promise<AuthSession | null> {
  try {
    const res = await fetch("/api/auth/session", {
      method: "GET",
      credentials: "include",
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
    if (!res.ok) return null;
    const data = (await res.json()) as {
      authenticated?: boolean;
      session?: AuthSession | null;
    };
    if (!data.authenticated || !data.session?.tenantId) return null;
    if (data.session.expiresAt && Date.now() > data.session.expiresAt) {
      return null;
    }
    return data.session;
  } catch {
    return null;
  }
}

export async function loginRequest(body: {
  email?: string;
  username?: string;
  password?: string;
  apiKey?: string;
}): Promise<LoginResponse> {
  const res = await fetch("/api/auth/login", {
    method: "POST",
    credentials: "include",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify({
      email: body.email,
      username: body.username,
      password: body.password,
      apiKey: body.apiKey,
    }),
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
  const data = (await res.json()) as LoginResponse & {
    accessToken?: string;
    refreshToken?: string;
    apiKey?: string;
  };
  if ("accessToken" in data) delete data.accessToken;
  if ("refreshToken" in data) delete data.refreshToken;
  if ("apiKey" in data) delete data.apiKey;
  return data;
}

export async function logoutRequest(): Promise<void> {
  try {
    await fetch("/api/auth/logout", {
      method: "POST",
      credentials: "include",
      cache: "no-store",
    });
  } catch {
    /* best-effort */
  }
}