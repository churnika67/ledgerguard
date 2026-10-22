import { NextRequest, NextResponse } from "next/server";
import { listAccounts, openAccount } from "@/lib/backend";
import { requireToken, toErrorResponse } from "@/lib/api-route-helpers";

export async function GET() {
  const auth = requireToken();
  if ("unauthorized" in auth) return auth.unauthorized;

  try {
    const accounts = await listAccounts(auth.token);
    return NextResponse.json(accounts);
  } catch (error) {
    return toErrorResponse(error);
  }
}

export async function POST(request: NextRequest) {
  const auth = requireToken();
  if ("unauthorized" in auth) return auth.unauthorized;

  const body = await request.json();
  try {
    const account = await openAccount(auth.token, body);
    return NextResponse.json(account, { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
