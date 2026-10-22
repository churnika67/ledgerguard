"use client";

import type { components } from "./api-types";
import type { NotificationResponse } from "./types";

export type AccountResponse = components["schemas"]["AccountResponse"];
export type AccountDetailResponse = components["schemas"]["AccountDetailResponse"];
export type TransferResponseDto = components["schemas"]["TransferResponseDto"];

export class ClientApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({ detail: response.statusText }));
    throw new ClientApiError(response.status, body.detail ?? "Request failed");
  }
  return response.json() as Promise<T>;
}

export function fetchAccounts(): Promise<AccountResponse[]> {
  return request("/api/accounts");
}

export function fetchAccount(id: string): Promise<AccountDetailResponse> {
  return request(`/api/accounts/${id}`);
}

export function openAccount(ownerName: string, currency: string, openingBalance: string): Promise<AccountResponse> {
  return request("/api/accounts", {
    method: "POST",
    body: JSON.stringify({ ownerName, currency, openingBalance }),
  });
}

export interface TransferInput {
  idempotencyKey: string;
  fromAccountId: string;
  toAccountId: string;
  amount: string;
  currency: string;
}

export function submitTransfer(input: TransferInput): Promise<TransferResponseDto> {
  return request("/api/transfers", { method: "POST", body: JSON.stringify(input) });
}

export function fetchNotifications(accountId: string): Promise<NotificationResponse[]> {
  return request(`/api/notifications/${accountId}`);
}
