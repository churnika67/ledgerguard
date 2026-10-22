"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchAccount, fetchAccounts, openAccount, type AccountResponse } from "@/lib/client-api";

export function useAccounts(initialData?: AccountResponse[]) {
  return useQuery({ queryKey: ["accounts"], queryFn: fetchAccounts, initialData });
}

export function useAccount(id: string, initialData?: Awaited<ReturnType<typeof fetchAccount>>) {
  return useQuery({
    queryKey: ["accounts", id],
    queryFn: () => fetchAccount(id),
    enabled: Boolean(id),
    initialData,
  });
}

export function useOpenAccount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ ownerName, currency, openingBalance }: { ownerName: string; currency: string; openingBalance: string }) =>
      openAccount(ownerName, currency, openingBalance),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
    },
  });
}
