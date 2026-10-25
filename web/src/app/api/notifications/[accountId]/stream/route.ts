import { requireToken } from "@/lib/api-route-helpers";

const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8082";

export const dynamic = "force-dynamic";

/**
 * Proxies notification-service's Server-Sent Events stream through to the browser.
 *
 * This has to be a real proxy, not a redirect straight to the gateway: the browser's
 * `EventSource` API can't set an `Authorization` header, so the only way to authenticate the
 * upstream request without putting the JWT in a URL (where it'd sit in browser history and
 * server logs) is to attach it here, server-side, where a normal `fetch` can still set headers,
 * then stream the response body straight through unmodified.
 */
export async function GET(request: Request, { params }: { params: { accountId: string } }) {
  const auth = requireToken();
  if ("unauthorized" in auth) return auth.unauthorized;

  const upstream = await fetch(
    `${GATEWAY_URL}/api/notifications/notifications/${params.accountId}/stream`,
    {
      headers: { Authorization: `Bearer ${auth.token}` },
      cache: "no-store",
      signal: request.signal,
    },
  );

  if (!upstream.ok || !upstream.body) {
    return new Response(null, { status: upstream.status || 502 });
  }

  return new Response(upstream.body, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
    },
  });
}
