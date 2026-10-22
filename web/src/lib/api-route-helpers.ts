import "server-only";
import { NextResponse } from "next/server";
import { BackendError } from "./backend";
import { getSessionToken } from "./session";

/** Reads the session token for a route handler, or returns a 401 response to short-circuit with. */
export function requireToken(): { token: string } | { unauthorized: NextResponse } {
  const token = getSessionToken();
  if (!token) {
    return { unauthorized: NextResponse.json({ detail: "Not authenticated" }, { status: 401 }) };
  }
  return { token };
}

export function toErrorResponse(error: unknown): NextResponse {
  if (error instanceof BackendError) {
    return NextResponse.json({ detail: error.detail }, { status: error.status });
  }
  return NextResponse.json({ detail: "Unexpected error" }, { status: 500 });
}
