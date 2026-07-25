import { NextResponse } from "next/server";
import {
  buildClearCookieHeaders,
  hasCredentialCookies,
  readAuthCookies,
} from "@/lib/server/auth";
import { deleteSession } from "@/lib/server/session-store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * When the browser still has ig_sid but the in-memory store is empty
 * (dev server restart, expired session, etc.), clear the cookie so
 * middleware no longer treats the user as signed in.
 */
function unauthenticatedResponse(sessionId?: string | null) {
  if (sessionId) deleteSession(sessionId);
  const response = NextResponse.json(
    { authenticated: false, session: null },
    { status: 401, headers: { "Cache-Control": "no-store" } },
  );
  for (const c of buildClearCookieHeaders()) {
    response.headers.append("Set-Cookie", c);
  }
  return response;
}

export async function GET() {
  const bundle = await readAuthCookies();
  if (!hasCredentialCookies(bundle) || !bundle.session) {
    return unauthenticatedResponse(bundle.sessionId);
  }
  return NextResponse.json(
    {
      authenticated: true,
      session: {
        tenantId: bundle.session.tenantId,
        subject: bundle.session.subject,
        method: bundle.session.method,
        roles: bundle.session.roles,
        expiresAt: bundle.session.expiresAt ?? null,
      },
    },
    { headers: { "Cache-Control": "no-store" } },
  );
}
