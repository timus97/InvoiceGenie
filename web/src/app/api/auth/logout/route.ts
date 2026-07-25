import { NextRequest, NextResponse } from "next/server";
import {
  backendBaseUrl,
  buildClearCookieHeaders,
  COOKIE_SESSION_ID,
} from "@/lib/server/auth";
import { deleteSession, getSession } from "@/lib/server/session-store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(req: NextRequest) {
  const sid = req.cookies.get(COOKIE_SESSION_ID)?.value;
  const stored = getSession(sid);
  if (stored?.refreshToken) {
    try {
      await fetch(`${backendBaseUrl()}/api/v1/auth/logout`, {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          ...(stored.accessToken
            ? { Authorization: `Bearer ${stored.accessToken}` }
            : {}),
        },
        body: JSON.stringify({ refreshToken: stored.refreshToken }),
        cache: "no-store",
      });
    } catch {
      /* best-effort revoke */
    }
  }
  deleteSession(sid);
  const response = NextResponse.json({ ok: true });
  for (const c of buildClearCookieHeaders()) {
    response.headers.append("Set-Cookie", c);
  }
  response.headers.set("Cache-Control", "no-store");
  return response;
}