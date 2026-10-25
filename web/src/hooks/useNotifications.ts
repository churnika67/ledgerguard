"use client";

import { useEffect } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchNotifications } from "@/lib/client-api";
import type { NotificationResponse } from "@/lib/types";

export function useNotifications(accountId: string | undefined) {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ["notifications", accountId],
    queryFn: () => fetchNotifications(accountId as string),
    enabled: Boolean(accountId),
    // The SSE subscription below is the primary live-update path now; this is just a slow
    // safety net in case a stream drops in a way the browser's own auto-reconnect doesn't catch.
    refetchInterval: 60_000,
  });

  useEffect(() => {
    if (!accountId) return;

    const source = new EventSource(`/api/notifications/${accountId}/stream`);
    source.addEventListener("notification", (event) => {
      const notification = JSON.parse((event as MessageEvent).data) as NotificationResponse;
      queryClient.setQueryData<NotificationResponse[]>(["notifications", accountId], (existing) => {
        const withoutDuplicate = (existing ?? []).filter((n) => n.id !== notification.id);
        return [notification, ...withoutDuplicate];
      });
    });

    return () => source.close();
  }, [accountId, queryClient]);

  return query;
}
