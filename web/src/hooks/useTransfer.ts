"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { submitTransfer, TransferInput } from "@/lib/client-api";

/**
 * Submits a transfer. The caller controls the Idempotency-Key: a fresh key per new transfer,
 * or the same key re-sent (see the "retry same request" flow on /send) to prove a replay never
 * double-charges -- the ledger returns the original result instead of moving money again.
 */
export function useTransfer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: TransferInput) => submitTransfer(input),
    onSuccess: (_result, variables) => {
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
      queryClient.invalidateQueries({ queryKey: ["accounts", variables.fromAccountId] });
      queryClient.invalidateQueries({ queryKey: ["accounts", variables.toAccountId] });
    },
  });
}
