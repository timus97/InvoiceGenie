import { NextRequest, NextResponse } from "next/server";
import {
  backendBaseUrl,
  buildSetCookieHeaders,
  cookieMaxAgeSeconds,
  publicSessionFromLogin,
  type BackendLoginResponse,
} from "@/lib/server/auth";
import { createSessionId, putSession } from "@/lib/server/session-store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type LoginBody = {
  email?: string;
  username?: string;
  password?: string;
  apiKey?: string;
};

export async function POST(req: NextRequest) {
  let body: LoginBody;
  try {
    body = (await req.json()) as LoginBody;
  } catch {
    return NextResponse.json(
      { error: "VALIDATION_ERROR", message: "JSON body required" },
      { status: 400 },
    );
  }

  const email = (body.email || body.username || "").trim();
  const password = body.password;
  const apiKey = body.apiKey?.trim();

  if (!email && !apiKey) {
    return NextResponse.json(
      {
        error: "VALIDATION_ERROR",
        message: "Provide email+password or apiKey",
      },
      { status: 400 },
    );
  }
  if (email && (password == null || password === "")) {
    return NextResponse.json(
      { error: "VALIDATION_ERROR", message: "password required" },
      { status: 400 },
    );
  }

  const upstream = await fetch(`${backendBaseUrl()}/api/v1/auth/login`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      email: email && email.includes("@") ? email : null,
      username: email && !email.includes("@") ? email : null,
      password: email ? password : null,
      apiKey: apiKey || null,
    }),
    cache: "no-store",
  });

  const text = await upstream.text();
  let parsed: BackendLoginResponse | { message?: string; error?: string } | null =
    null;
  try {
    parsed = text ? (JSON.parse(text) as BackendLoginResponse) : null;
  } catch {
    parsed = null;
  }

  if (!upstream.ok) {
    const message =
      (parsed as { message?: string; error?: string } | null)?.message ||
      (parsed as { message?: string; error?: string } | null)?.error ||
      `Login failed (${upstream.status})`;
    return NextResponse.json(
      { error: "UNAUTHORIZED", message },
      { status: upstream.status === 401 ? 401 : upstream.status },
    );
  }

  const login = parsed as BackendLoginResponse;
  if (!login?.tenantId || !login?.subject) {
    return NextResponse.json(
      { error: "LOGIN_ERROR", message: "Invalid login response from API" },
      { status: 502 },
    );
  }

  const session = publicSessionFromLogin(login);
  const maxAge = cookieMaxAgeSeconds(
    login.refreshExpiresInSeconds ?? login.expiresInSeconds,
  );
  const sessionId = createSessionId();

  putSession(sessionId, {
    accessToken: login.accessToken || null,
    refreshToken: login.refreshToken || null,
    apiKey: !login.accessToken && apiKey ? apiKey : null,
    session,
    maxAgeSeconds: maxAge,
    accessExpiresInSeconds: login.expiresInSeconds,
  });

  const response = NextResponse.json(
    {
      tenantId: session.tenantId,
      subject: session.subject,
      method: session.method,
      roles: session.roles,
      email: session.email,
      displayName: session.displayName,
      userId: session.userId,
      expiresInSeconds: login.expiresInSeconds ?? null,
      refreshExpiresInSeconds: login.refreshExpiresInSeconds ?? null,
      expiresAt: session.expiresAt ?? null,
    },
    { status: 200 },
  );
  for (const c of buildSetCookieHeaders({ sessionId, maxAge })) {
    response.headers.append("Set-Cookie", c);
  }
  response.headers.set("Cache-Control", "no-store");
  return response;
}