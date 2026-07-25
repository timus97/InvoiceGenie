import { describe, expect, it, beforeEach } from "vitest";
import {
  buildClearCookieHeaders,
  buildSetCookieHeaders,
  buildUpstreamAuthHeaders,
  cookieMaxAgeSeconds,
  hasCredentialCookies,
  publicSessionFromLogin,
  stripSecrets,
} from "@/lib/server/auth-core";
import {
  createSessionId,
  putSession,
  getSession,
  deleteSession,
  _resetSessionsForTests,
} from "@/lib/server/session-store";

describe("server auth helpers", () => {
  beforeEach(() => {
    _resetSessionsForTests();
  });

  it("builds public session without tokens", () => {
    const session = publicSessionFromLogin({
      accessToken: "super.secret.jwt",
      refreshToken: "refresh-secret",
      tokenType: "Bearer",
      tenantId: "00000000-0000-0000-0000-000000000001",
      subject: "admin@invoicegenie.local",
      method: "jwt",
      roles: ["TENANT_ADMIN"],
      expiresInSeconds: 900,
      refreshExpiresInSeconds: 604800,
      email: "admin@invoicegenie.local",
    });
    expect(session.subject).toBe("admin@invoicegenie.local");
    expect(session).not.toHaveProperty("accessToken");
    expect(session).not.toHaveProperty("refreshToken");
  });

  it("strips secrets including refreshToken", () => {
    const cleaned = stripSecrets({
      tenantId: "t1",
      accessToken: "jwt",
      refreshToken: "rt",
      password: "pw",
    });
    expect(cleaned.accessToken).toBeUndefined();
    expect(cleaned.refreshToken).toBeUndefined();
    expect(cleaned.password).toBeUndefined();
  });

  it("sets only opaque session id cookie", () => {
    const headers = buildSetCookieHeaders({
      sessionId: "opaque-id",
      maxAge: 604800,
    });
    expect(headers).toHaveLength(1);
    expect(headers[0]).toContain("ig_sid=");
    expect(headers[0]).toContain("HttpOnly");
  });

  it("default cookie max-age is 7 days", () => {
    expect(cookieMaxAgeSeconds(null)).toBe(60 * 60 * 24 * 7);
    expect(cookieMaxAgeSeconds(900)).toBe(900);
  });

  it("stores access+refresh server-side under opaque id", () => {
    const id = createSessionId();
    putSession(id, {
      accessToken: "jwt-token-value",
      refreshToken: "refresh-token-value",
      apiKey: null,
      session: {
        tenantId: "00000000-0000-0000-0000-000000000001",
        subject: "admin@invoicegenie.local",
        method: "jwt",
        roles: ["TENANT_ADMIN"],
      },
      maxAgeSeconds: 604800,
      accessExpiresInSeconds: 900,
    });
    const stored = getSession(id)!;
    expect(stored.accessToken).toBe("jwt-token-value");
    expect(stored.refreshToken).toBe("refresh-token-value");
    expect(id).not.toContain("jwt-token-value");
    deleteSession(id);
  });

  it("builds upstream Authorization from access token", () => {
    const headers = buildUpstreamAuthHeaders({
      accessToken: "abc.def.ghi",
      session: {
        tenantId: "00000000-0000-0000-0000-000000000001",
        subject: "u",
        method: "jwt",
        roles: ["AR_CLERK"],
      },
    });
    expect(headers.Authorization).toBe("Bearer abc.def.ghi");
  });

  it("detects resolved credentials", () => {
    expect(hasCredentialCookies({ session: null })).toBe(false);
    expect(hasCredentialCookies({ accessToken: "x", session: null })).toBe(true);
  });
});