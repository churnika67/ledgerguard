"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useAccounts } from "@/hooks/useAccounts";
import { useTransfer } from "@/hooks/useTransfer";
import { formatMoney } from "@/lib/format";
import type { TransferResponseDto } from "@/lib/client-api";
import { Send as SendIcon, RotateCcw, CheckCircle2, XCircle, AlertCircle, Wallet, Hash, Banknote, Tag, Repeat, ChevronDown } from "lucide-react";

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
      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm shadow-slate-200/40">
        <h1 className="mb-4 flex items-center gap-2 text-xl font-semibold">
          <SendIcon className="h-5 w-5 text-indigo-600" aria-hidden="true" />
          Send money
        </h1>
        <form onSubmit={handleSubmit(onSubmit)} className="flex max-w-sm flex-col gap-3">
          <label className="flex flex-col gap-1.5 text-sm font-medium text-slate-700">
            From
            <span className="relative">
              <Wallet className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <select
                {...register("fromAccountId")}
                className="w-full appearance-none rounded-lg border border-slate-300 py-2 pl-9 pr-8 text-sm outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
              >
                <option value="">Select an account</option>
                {accountOptions.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.ownerName} -- {formatMoney(a.balance)} {a.currency}
                  </option>
                ))}
              </select>
              <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            </span>
            {errors.fromAccountId && (
              <span className="flex items-center gap-1 text-xs font-normal text-red-600">
                <AlertCircle className="h-3.5 w-3.5" aria-hidden="true" />
                {errors.fromAccountId.message}
              </span>
            )}
          </label>
          <label className="flex flex-col gap-1.5 text-sm font-medium text-slate-700">
            To (account id)
            <span className="relative">
              <Hash className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <input
                {...register("toAccountId")}
                className="w-full rounded-lg border border-slate-300 py-2 pl-9 pr-3 text-sm outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
                placeholder="destination account id"
              />
            </span>
            {errors.toAccountId && (
              <span className="flex items-center gap-1 text-xs font-normal text-red-600">
                <AlertCircle className="h-3.5 w-3.5" aria-hidden="true" />
                {errors.toAccountId.message}
              </span>
            )}
          </label>
          <label className="flex flex-col gap-1.5 text-sm font-medium text-slate-700">
            Amount
            <span className="relative">
              <Banknote className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <input
                {...register("amount")}
                className="w-full rounded-lg border border-slate-300 py-2 pl-9 pr-3 text-sm outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
                placeholder="25.00"
              />
            </span>
            {errors.amount && (
              <span className="flex items-center gap-1 text-xs font-normal text-red-600">
                <AlertCircle className="h-3.5 w-3.5" aria-hidden="true" />
                {errors.amount.message}
              </span>
            )}
          </label>
          <label className="flex flex-col gap-1.5 text-sm font-medium text-slate-700">
            Currency
            <span className="relative">
              <Tag className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <input
                {...register("currency")}
                className="w-full rounded-lg border border-slate-300 py-2 pl-9 pr-3 text-sm outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
                maxLength={3}
              />
            </span>
          </label>
          <button
            type="submit"
            disabled={isSubmitting}
            className="mt-2 flex items-center justify-center gap-2 rounded-lg bg-gradient-to-br from-indigo-600 to-indigo-500 px-4 py-2.5 text-sm font-medium text-white shadow-md shadow-indigo-500/30 transition-transform hover:scale-[1.02] disabled:opacity-50 disabled:hover:scale-100"
          >
            <SendIcon className="h-4 w-4" aria-hidden="true" />
            Send
          </button>
        </form>

        {lastSubmission && (
          <button
            onClick={retrySameRequest}
            disabled={transferMutation.isPending}
            className="mt-4 flex items-center gap-2 rounded-lg border border-slate-300 px-4 py-2 text-sm text-slate-700 transition-colors hover:bg-slate-100 disabled:opacity-50"
          >
            <RotateCcw className="h-4 w-4" aria-hidden="true" />
            Retry same request (proves no double charge)
          </button>
        )}

        {transferMutation.isError && (
          <p className="mt-3 flex items-center gap-1.5 text-sm text-red-600">
            <AlertCircle className="h-4 w-4 shrink-0" aria-hidden="true" />
            {(transferMutation.error as Error).message}
          </p>
        )}
      </section>

      {results.length > 0 && (
        <section>
          <h2 className="mb-2 text-lg font-semibold">Results</h2>
          <ul className="flex flex-col gap-2">
            {results.map((entry, index) => {
              const completed = entry.result.status === "COMPLETED";
              return (
                <li key={index} className="rounded-xl border border-slate-200 bg-white p-4 text-sm shadow-sm shadow-slate-200/40">
                  <div className="mb-1 flex items-center gap-1.5 font-medium">
                    {entry.label === "New transfer" ? (
                      <SendIcon className="h-4 w-4 text-indigo-500" aria-hidden="true" />
                    ) : (
                      <Repeat className="h-4 w-4 text-indigo-500" aria-hidden="true" />
                    )}
                    {entry.label}
                  </div>
                  <div className="flex items-center gap-1.5">
                    Transfer <code className="rounded bg-slate-100 px-1.5 py-0.5 text-xs">{entry.result.transferId}</code> --{" "}
                    <span className={`flex items-center gap-1 ${completed ? "text-emerald-600" : "text-red-600"}`}>
                      {completed ? (
                        <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
                      ) : (
                        <XCircle className="h-3.5 w-3.5" aria-hidden="true" />
                      )}
                      {entry.result.status}
                    </span>
                    {entry.result.rejectReason && <span> ({entry.result.rejectReason})</span>}
                  </div>
                  {index < results.length - 1 && results[index + 1].result.transferId === entry.result.transferId && (
                    <div className="mt-1.5 flex items-center gap-1.5 rounded-lg bg-emerald-50 px-2.5 py-1.5 text-xs text-emerald-700">
                      <Repeat className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                      Same transfer id as before -- this was a replay, not a new charge.
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        </section>
      )}
    </div>
  );
}
