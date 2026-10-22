"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useAccounts } from "@/hooks/useAccounts";
import { useTransfer } from "@/hooks/useTransfer";
import { formatMoney } from "@/lib/format";
import type { TransferResponseDto } from "@/lib/client-api";

const sendSchema = z.object({
  fromAccountId: z.string().min(1, "Choose a source account"),
  toAccountId: z.string().min(1, "Choose a destination account"),
  amount: z.string().regex(/^\d+(\.\d{1,2})?$/, "Enter an amount like 25.00"),
  currency: z.string().length(3),
});

type SendForm = z.infer<typeof sendSchema>;

export default function SendPage() {
  const { data: accounts } = useAccounts();
  const transferMutation = useTransfer();
  const [lastSubmission, setLastSubmission] = useState<{ idempotencyKey: string; values: SendForm } | null>(null);
  const [results, setResults] = useState<Array<{ label: string; result: TransferResponseDto }>>([]);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<SendForm>({ resolver: zodResolver(sendSchema), defaultValues: { currency: "USD" } });

  const accountOptions = useMemo(() => accounts ?? [], [accounts]);

  async function send(values: SendForm, idempotencyKey: string, label: string) {
    const result = await transferMutation.mutateAsync({ idempotencyKey, ...values });
    setResults((prev) => [{ label, result }, ...prev]);
    setLastSubmission({ idempotencyKey, values });
  }

  async function onSubmit(values: SendForm) {
    const idempotencyKey = crypto.randomUUID();
    await send(values, idempotencyKey, "New transfer");
  }

  async function retrySameRequest() {
    if (!lastSubmission) return;
    // Same Idempotency-Key, same body: the ledger must return the original result, not move
    // money again. This is the one-click proof of that guarantee.
    await send(lastSubmission.values, lastSubmission.idempotencyKey, "Retry (same Idempotency-Key)");
  }

  return (
    <div className="flex flex-col gap-8">
      <section>
        <h1 className="mb-4 text-xl font-semibold">Send money</h1>
        <form onSubmit={handleSubmit(onSubmit)} className="flex max-w-sm flex-col gap-3">
          <label className="flex flex-col gap-1 text-sm">
            From
            <select {...register("fromAccountId")} className="rounded border border-slate-300 px-3 py-2">
              <option value="">Select an account</option>
              {accountOptions.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.ownerName} -- {formatMoney(a.balance)} {a.currency}
                </option>
              ))}
            </select>
            {errors.fromAccountId && <span className="text-xs text-red-600">{errors.fromAccountId.message}</span>}
          </label>
          <label className="flex flex-col gap-1 text-sm">
            To (account id)
            <input {...register("toAccountId")} className="rounded border border-slate-300 px-3 py-2" placeholder="destination account id" />
            {errors.toAccountId && <span className="text-xs text-red-600">{errors.toAccountId.message}</span>}
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Amount
            <input {...register("amount")} className="rounded border border-slate-300 px-3 py-2" placeholder="25.00" />
            {errors.amount && <span className="text-xs text-red-600">{errors.amount.message}</span>}
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Currency
            <input {...register("currency")} className="rounded border border-slate-300 px-3 py-2" maxLength={3} />
          </label>
          <button
            type="submit"
            disabled={isSubmitting}
            className="mt-2 rounded bg-slate-900 px-4 py-2 text-white disabled:opacity-50"
          >
            Send
          </button>
        </form>

        {lastSubmission && (
          <button
            onClick={retrySameRequest}
            disabled={transferMutation.isPending}
            className="mt-4 rounded border border-slate-400 px-4 py-2 text-sm disabled:opacity-50"
          >
            Retry same request (proves no double charge)
          </button>
        )}

        {transferMutation.isError && (
          <p className="mt-3 text-sm text-red-600">{(transferMutation.error as Error).message}</p>
        )}
      </section>

      {results.length > 0 && (
        <section>
          <h2 className="mb-2 text-lg font-semibold">Results</h2>
          <ul className="flex flex-col gap-2">
            {results.map((entry, index) => (
              <li key={index} className="rounded border border-slate-200 bg-white p-3 text-sm">
                <div className="mb-1 font-medium">{entry.label}</div>
                <div>
                  Transfer <code>{entry.result.transferId}</code> --{" "}
                  <span className={entry.result.status === "COMPLETED" ? "text-green-600" : "text-red-600"}>
                    {entry.result.status}
                  </span>
                  {entry.result.rejectReason && <span> ({entry.result.rejectReason})</span>}
                </div>
                {index < results.length - 1 && results[index + 1].result.transferId === entry.result.transferId && (
                  <div className="mt-1 text-xs text-green-700">
                    Same transfer id as before -- this was a replay, not a new charge.
                  </div>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
