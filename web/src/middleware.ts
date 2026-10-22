import { NextRequest, NextResponse } from "next/server";
import { SESSION_COOKIE } from "@/lib/session-constants";

const PUBLIC_PATHS = ["/login", "/api/auth"];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  if (PUBLIC_PATHS.some((path) => pathname.startsWith(path))) {
    return NextResponse.next();
  }

  const hasSession = request.cookies.has(SESSION_COOKIE);
  if (!hasSession) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/accounts/:path*", "/send/:path*", "/notifications/:path*", "/api/accounts/:path*", "/api/transfers/:path*", "/api/notifications/:path*"],
};
