import { NextRequest, NextResponse } from "next/server";
import {
  backendBaseUrl,
  buildUpstreamAuthHeaders,
  hasCredentialCookies,
  publicSessionFromLogin,
  readAuthCookies,
  type BackendLoginResponse,
} from "@/lib/server/auth";
import { getSession, updateSessionTokens } from "@/lib/server/session-store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const HOP_BY_HOP = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailers",
  "transfer-encoding",
  "upgrade",
  "host",
  "content-length",
  "authorization",
  "x-api-key",
  "cookie",
]);

async function tryRefresh(sessionId: string | undefined): Promise<boolean> {
  if (!sessionId) return false;
  const stored = getSession(sessionId);
  if (!stored?.refreshToken) return false;
  try {
    const res = await fetch(`${backendBaseUrl()}/api/v1/auth/refresh`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ refreshToken: stored.refreshToken }),
      cache: "no-store",
    });
    if (!res.ok) return false;
    const login = (await res.json()) as BackendLoginResponse;
    if (!login.accessToken) return false;
    updateSessionTokens(sessionId, {
      accessToken: login.accessToken,
      refreshToken: login.refreshToken,
      accessExpiresInSeconds: login.expiresInSeconds,
      session: publicSessionFromLogin(login),
    });
    return true;
  } catch {
    return false;
  }
}

async function proxy(req: NextRequest, pathParts: string[]) {
  const path = pathParts.map(encodeURIComponent).join("/");
  if (path === "auth/login" || path.startsWith("auth/login/")) {
    return NextResponse.json(
      {
        error: "USE_BFF_LOGIN",
        message: "Use POST /api/auth/login",
      },
      { status: 400 },
    );
  }

  let bundle = await readAuthCookies();
  if (!hasCredentialCookies(bundle)) {
    return NextResponse.json(
      { error: "UNAUTHORIZED", message: "Sign in required" },
      { status: 401 },
    );
  }

  // Proactive refresh if access token near expiry (< 60s)
  const stored = getSession(bundle.sessionId);
  if (
    stored?.refreshToken &&
    stored.accessExpiresAt &&
    stored.accessExpiresAt - Date.now() < 60_000
  ) {
    await tryRefresh(bundle.sessionId);
    bundle = await readAuthCookies();
  }

  const url = new URL(req.url);
  const target = `${backendBaseUrl()}/api/v1/${path}${url.search}`;

  const buildHeaders = () => {
    const headers = new Headers();
    headers.set("Accept", req.headers.get("Accept") || "application/json");
    const contentType = req.headers.get("Content-Type");
    if (contentType) headers.set("Content-Type", contentType);
    const idempotency = req.headers.get("Idempotency-Key");
    if (idempotency) headers.set("Idempotency-Key", idempotency);
    const authHeaders = buildUpstreamAuthHeaders(bundle);
    for (const [k, v] of Object.entries(authHeaders)) {
      headers.set(k, v);
    }
    const clientTenant = req.headers.get("X-Tenant-Id")?.trim();
    if (
      clientTenant &&
      bundle.session?.tenantId &&
      clientTenant !== bundle.session.tenantId
    ) {
      return null;
    }
    if (!headers.has("X-Tenant-Id") && clientTenant) {
      headers.set("X-Tenant-Id", clientTenant);
    }
    return headers;
  };

  const headers = buildHeaders();
  if (!headers) {
    return NextResponse.json(
      {
        error: "TENANT_MISMATCH",
        message: "X-Tenant-Id does not match authenticated tenant",
      },
      { status: 403 },
    );
  }

  const method = req.method.toUpperCase();
  const hasBody = !["GET", "HEAD"].includes(method);
  const body = hasBody ? await req.arrayBuffer() : undefined;

  const doFetch = async (h: Headers) => {
    return fetch(target, {
      method,
      headers: h,
      body: body && body.byteLength > 0 ? body : undefined,
      cache: "no-store",
      redirect: "manual",
    });
  };

  let upstream: Response;
  try {
    upstream = await doFetch(headers);
  } catch (e) {
    return NextResponse.json(
      {
        error: "UPSTREAM_ERROR",
        message: e instanceof Error ? e.message : "Backend unreachable",
      },
      { status: 502 },
    );
  }

  // On 401, try refresh once and retry
  if (upstream.status === 401 && bundle.sessionId) {
    const refreshed = await tryRefresh(bundle.sessionId);
    if (refreshed) {
      bundle = await readAuthCookies();
      const retryHeaders = buildHeaders();
      if (retryHeaders) {
        try {
          upstream = await doFetch(retryHeaders);
        } catch {
          /* keep original */
        }
      }
    }
  }

  const responseHeaders = new Headers();
  upstream.headers.forEach((value, key) => {
    const lower = key.toLowerCase();
    if (HOP_BY_HOP.has(lower)) return;
    if (lower === "set-cookie") return;
    responseHeaders.set(key, value);
  });
  responseHeaders.set("Cache-Control", "no-store");

  const buf = await upstream.arrayBuffer();
  return new NextResponse(buf, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  });
}

type Ctx = { params: Promise<{ path: string[] }> };

export async function GET(req: NextRequest, ctx: Ctx) {
  const { path } = await ctx.params;
  return proxy(req, path);
}
export async function POST(req: NextRequest, ctx: Ctx) {
  const { path } = await ctx.params;
  return proxy(req, path);
}
export async function PUT(req: NextRequest, ctx: Ctx) {
  const { path } = await ctx.params;
  return proxy(req, path);
}
export async function PATCH(req: NextRequest, ctx: Ctx) {
  const { path } = await ctx.params;
  return proxy(req, path);
}
export async function DELETE(req: NextRequest, ctx: Ctx) {
  const { path } = await ctx.params;
  return proxy(req, path);
}
export async function HEAD(req: NextRequest, ctx: Ctx) {
  const { path } = await ctx.params;
  return proxy(req, path);
}
export async function OPTIONS() {
  return new NextResponse(null, {
    status: 204,
    headers: { Allow: "GET,HEAD,POST,PUT,PATCH,DELETE,OPTIONS" },
  });
}