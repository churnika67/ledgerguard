"use client";

import { useQuery } from "@tanstack/react-query";
import { fetchNotifications } from "@/lib/client-api";

export function useNotifications(accountId: string | undefined) {
  return useQuery({
    queryKey: ["notifications", accountId],
    queryFn: () => fetchNotifications(accountId as string),
    enabled: Boolean(accountId),
    refetchInterval: 5_000,
  });
}
