import { NextResponse } from "next/server";
import { getAccount } from "@/lib/backend";
import { requireToken, toErrorResponse } from "@/lib/api-route-helpers";

export async function GET(_request: Request, { params }: { params: { id: string } }) {
  const auth = requireToken();
  if ("unauthorized" in auth) return auth.unauthorized;

  try {
    const account = await getAccount(auth.token, params.id);
    return NextResponse.json(account);
  } catch (error) {
    return toErrorResponse(error);
  }
}
