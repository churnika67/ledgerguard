import { NextRequest, NextResponse } from "next/server";
import { transfer } from "@/lib/backend";
import { requireToken, toErrorResponse } from "@/lib/api-route-helpers";

export async function POST(request: NextRequest) {
  const auth = requireToken();
  if ("unauthorized" in auth) return auth.unauthorized;

  const { idempotencyKey, ...rest } = await request.json();
  if (!idempotencyKey) {
    return NextResponse.json({ detail: "idempotencyKey is required" }, { status: 400 });
  }

  try {
    const result = await transfer(auth.token, idempotencyKey, rest);
    return NextResponse.json(result);
  } catch (error) {
    return toErrorResponse(error);
  }
}
