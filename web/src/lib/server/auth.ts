/**
 * Server-only auth helpers for the Next.js BFF.
 */

import { cookies } from "next/headers";
import {
  COOKIE_SESSION_ID,
  type AuthCookieBundle,
} from "@/lib/server/auth-core";
import { getSession } from "@/lib/server/session-store";

export * from "@/lib/server/auth-core";

export async function readAuthCookies(): Promise<AuthCookieBundle> {
  const jar = await cookies();
  const sessionId = jar.get(COOKIE_SESSION_ID)?.value;
  const stored = getSession(sessionId);
  if (!stored) {
    return { sessionId: sessionId || undefined, session: null };
  }
  return {
    sessionId,
    accessToken: stored.accessToken,
    refreshToken: stored.refreshToken,
    apiKey: stored.apiKey,
    session: stored.session,
  };
}