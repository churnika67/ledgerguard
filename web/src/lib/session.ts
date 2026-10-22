import "server-only";
import { cookies } from "next/headers";
import { SESSION_COOKIE } from "./session-constants";

/** Server-only: read the JWT out of the httpOnly session cookie. Never exposed to client JS. */
export function getSessionToken(): string | undefined {
  return cookies().get(SESSION_COOKIE)?.value;
}

export function setSessionCookie(token: string, expiresInSeconds: number) {
  cookies().set(SESSION_COOKIE, token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: expiresInSeconds,
  });
}

export function clearSessionCookie() {
  cookies().delete(SESSION_COOKIE);
}
