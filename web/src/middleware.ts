import { NextResponse, type NextRequest } from "next/server";

const PUBLIC_PATHS = ["/login", "/favicon.ico"];
const PUBLIC_PREFIXES = ["/_next/", "/api/auth/login", "/api/auth/session", "/q/"];

function isPublic(pathname: string): boolean {
  if (PUBLIC_PATHS.includes(pathname)) return true;
  return PUBLIC_PREFIXES.some(
    (p) => pathname === p || pathname.startsWith(p),
  );
}

function hasSessionCookie(req: NextRequest): boolean {
  return Boolean(
    req.cookies.get("ig_sid")?.value || req.cookies.get("ig_at")?.value || req.cookies.get("ig_sess")?.value,
  );
}

export function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl;

  // Always let static assets through
  if (pathname.startsWith("/_next/") || pathname.includes(".")) {
    if (pathname.endsWith(".ico") || pathname.startsWith("/_next/")) {
      return NextResponse.next();
    }
  }

  if (isPublic(pathname)) {
    // Already signed in â†’ bounce away from login
    if (pathname === "/login" && hasSessionCookie(req)) {
      const next = req.nextUrl.searchParams.get("next") || "/";
      const dest = next.startsWith("/") ? next : "/";
      return NextResponse.redirect(new URL(dest, req.url));
    }
    return NextResponse.next();
  }

  // API proxy: return 401 JSON rather than redirect
  if (pathname.startsWith("/api/")) {
    if (!hasSessionCookie(req) && !pathname.startsWith("/api/auth/")) {
      return NextResponse.json(
        { error: "UNAUTHORIZED", message: "Sign in required" },
        { status: 401 },
      );
    }
    return NextResponse.next();
  }

  if (!hasSessionCookie(req)) {
    const login = new URL("/login", req.url);
    login.searchParams.set("next", pathname);
    return NextResponse.redirect(login);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    /*
     * Match all paths except Next internals and common static files.
     */
    "/((?!_next/static|_next/image|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)",
  ],
};
