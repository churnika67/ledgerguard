import { NextResponse } from "next/server";
import { listNotifications } from "@/lib/backend";
import { requireToken, toErrorResponse } from "@/lib/api-route-helpers";

export async function GET(_request: Request, { params }: { params: { accountId: string } }) {
  const auth = requireToken();
  if ("unauthorized" in auth) return auth.unauthorized;

  try {
    const notifications = await listNotifications(auth.token, params.accountId);
    return NextResponse.json(notifications);
  } catch (error) {
    return toErrorResponse(error);
  }
}
