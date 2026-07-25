import { NextResponse } from "next/server";
import {
  hasCredentialCookies,
  readAuthCookies,
} from "@/lib/server/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  const bundle = await readAuthCookies();
  if (!hasCredentialCookies(bundle) || !bundle.session) {
    return NextResponse.json(
      { authenticated: false, session: null },
      { status: 401, headers: { "Cache-Control": "no-store" } },
    );
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
