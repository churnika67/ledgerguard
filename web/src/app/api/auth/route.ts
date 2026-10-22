import { NextRequest, NextResponse } from "next/server";
import { BackendError, login } from "@/lib/backend";
import { setSessionCookie } from "@/lib/session";

export async function POST(request: NextRequest) {
  const body = await request.json();
  try {
    const result = await login(body);
    if (!result.accessToken || !result.expiresInSeconds) {
      return NextResponse.json({ detail: "Login succeeded but the token was malformed" }, { status: 500 });
    }
    setSessionCookie(result.accessToken, result.expiresInSeconds);
    return NextResponse.json({ ok: true });
  } catch (error) {
    if (error instanceof BackendError) {
      return NextResponse.json({ detail: error.detail }, { status: error.status });
    }
    return NextResponse.json({ detail: "Login failed" }, { status: 500 });
  }
}
