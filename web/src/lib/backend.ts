import "server-only";
import type { components } from "./api-types";
import type { NotificationResponse } from "./types";

export type { NotificationResponse };

export type AccountResponse = components["schemas"]["AccountResponse"];
export type AccountDetailResponse = components["schemas"]["AccountDetailResponse"];
export type TransferResponseDto = components["schemas"]["TransferResponseDto"];
export type AuthRequest = components["schemas"]["AuthRequest"];
export type AuthResponse = components["schemas"]["AuthResponse"];

// Money amounts are kept as strings end-to-end on our side (never a JS `number`, which can't
// represent decimals like 0.1 exactly) -- the generated OpenAPI types model them as `number`
// because that's what the JSON schema says, but Jackson on the Java side happily accepts a JSON
// string for a BigDecimal field too, so sending a string here is both safer and compatible.
export interface OpenAccountRequest {
  ownerName: string;
  currency: string;
  openingBalance: string;
}

export interface TransferRequestDto {
  fromAccountId: string;
  toAccountId: string;
  amount: string;
  currency: string;
}

// Both backends are reached through api-gateway now, never directly -- it strips the
// /api/ledger or /api/notifications prefix before forwarding, so every path below carries one.
const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8082";

/** A problem+json error from either backend service, thrown so route handlers can forward it. */
export class BackendError extends Error {
  constructor(
    public readonly status: number,
    public readonly detail: string,
  ) {
    super(detail);
  }
}

async function call<T>(baseUrl: string, path: string, init: RequestInit & { token?: string }): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Content-Type", "application/json");
  if (init.token) {
    headers.set("Authorization", `Bearer ${init.token}`);
  }

  const response = await fetch(`${baseUrl}${path}`, { ...init, headers, cache: "no-store" });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({ detail: response.statusText }));
    throw new BackendError(response.status, problem.detail ?? "Request failed");
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export function login(request: AuthRequest): Promise<AuthResponse> {
  return call(GATEWAY_URL, "/api/ledger/auth/token", { method: "POST", body: JSON.stringify(request) });
}

export function listAccounts(token: string): Promise<AccountResponse[]> {
  return call(GATEWAY_URL, "/api/ledger/accounts", { method: "GET", token });
}

export function openAccount(token: string, request: OpenAccountRequest): Promise<AccountResponse> {
  return call(GATEWAY_URL, "/api/ledger/accounts", { method: "POST", token, body: JSON.stringify(request) });
}

export function getAccount(token: string, accountId: string): Promise<AccountDetailResponse> {
  return call(GATEWAY_URL, `/api/ledger/accounts/${accountId}`, { method: "GET", token });
}

export function transfer(
  token: string,
  idempotencyKey: string,
  request: TransferRequestDto,
): Promise<TransferResponseDto> {
  return call(GATEWAY_URL, "/api/ledger/transfers", {
    method: "POST",
    token,
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(request),
  });
}

export function listNotifications(token: string, accountId: string): Promise<NotificationResponse[]> {
  return call(GATEWAY_URL, `/api/notifications/notifications/${accountId}`, { method: "GET", token });
}
