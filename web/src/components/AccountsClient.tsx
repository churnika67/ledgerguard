"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useAccounts, useOpenAccount } from "@/hooks/useAccounts";
import { formatMoney } from "@/lib/format";
import type { AccountResponse } from "@/lib/client-api";
import { Wallet, PlusCircle, ChevronRight, Inbox, User, Tag, Banknote, AlertCircle } from "lucide-react";

const openAccountSchema = z.object({
  ownerName: z.string().min(1, "Owner name is required"),
  currency: z.string().length(3, "Use a 3-letter currency code, e.g. USD").toUpperCase(),
  openingBalance: z.string().regex(/^\d+(\.\d{1,2})?$/, "Enter an amount like 100.00"),
});

type OpenAccountForm = z.infer<typeof openAccountSchema>;

export function AccountsClient({ initialAccounts }: { initialAccounts: AccountResponse[] }) {
  const { data: accounts } = useAccounts(initialAccounts);
  const list = accounts ?? initialAccounts;
  const openAccountMutation = useOpenAccount();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<OpenAccountForm>({
    resolver: zodResolver(openAccountSchema),
    defaultValues: { currency: "USD", openingBalance: "0.00" },
  });

  async function onSubmit(values: OpenAccountForm) {
    await openAccountMutation.mutateAsync(values);
    reset({ ownerName: values.ownerName, currency: "USD", openingBalance: "0.00" });
  }

  return (
    <div className="flex flex-col gap-10">
      <section>
        <h1 className="mb-4 flex items-center gap-2 text-xl font-semibold">
          <Wallet className="h-5 w-5 text-indigo-600" aria-hidden="true" />
          Your accounts
        </h1>
        {list.length === 0 ? (
          <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-slate-300 bg-white py-12 text-center">
            <Inbox className="h-8 w-8 text-slate-400" aria-hidden="true" />
            <p className="text-slate-500">No accounts yet -- open one below.</p>
          </div>
        ) : (
          <ul className="flex flex-col gap-2.5">
            {list.map((account) => (
              <li key={account.id}>
                <Link
                  href={`/accounts/${account.id}`}
                  className="group flex items-center justify-between rounded-xl border border-slate-200 bg-white px-4 py-3.5 shadow-sm shadow-slate-200/40 transition-all hover:-translate-y-0.5 hover:border-indigo-300 hover:shadow-md"
                >
                  <div className="flex items-center gap-3">
                    <span className="flex h-9 w-9 items-center justify-center rounded-full bg-indigo-50 text-sm font-semibold text-indigo-700">
                      {account.ownerName?.[0]?.toUpperCase() ?? "?"}
                    </span>
                    <span className="font-medium">{account.ownerName}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-slate-500">
                      {formatMoney(account.balance)} {account.currency}
                    </span>
                    <ChevronRight className="h-4 w-4 text-slate-400 transition-transform group-hover:translate-x-0.5 group-hover:text-indigo-500" aria-hidden="true" />
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm shadow-slate-200/40">
        <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold">
          <PlusCircle className="h-5 w-5 text-indigo-600" aria-hidden="true" />
          Open a new account
        </h2>
        <form onSubmit={handleSubmit(onSubmit)} className="flex max-w-sm flex-col gap-3">
          <label className="flex flex-col gap-1.5 text-sm font-medium text-slate-700">
            Owner name
            <span className="relative">
              <User className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <input
                {...register("ownerName")}
                className="w-full rounded-lg border border-slate-300 py-2 pl-9 pr-3 text-sm outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
              />
            </span>
            {errors.ownerName && (
              <span className="flex items-center gap-1 text-xs font-normal text-red-600">
                <AlertCircle className="h-3.5 w-3.5" aria-hidden="true" />
                {errors.ownerName.message}
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
            {errors.currency && (
              <span className="flex items-center gap-1 text-xs font-normal text-red-600">
                <AlertCircle className="h-3.5 w-3.5" aria-hidden="true" />
                {errors.currency.message}
              </span>
            )}
          </label>
          <label className="flex flex-col gap-1.5 text-sm font-medium text-slate-700">
            Opening balance
            <span className="relative">
              <Banknote className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <input
                {...register("openingBalance")}
                className="w-full rounded-lg border border-slate-300 py-2 pl-9 pr-3 text-sm outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
              />
            </span>
            {errors.openingBalance && (
              <span className="flex items-center gap-1 text-xs font-normal text-red-600">
                <AlertCircle className="h-3.5 w-3.5" aria-hidden="true" />
                {errors.openingBalance.message}
              </span>
            )}
          </label>
          <button
            type="submit"
            disabled={isSubmitting}
            className="mt-2 flex items-center justify-center gap-2 rounded-lg bg-gradient-to-br from-indigo-600 to-indigo-500 px-4 py-2.5 text-sm font-medium text-white shadow-md shadow-indigo-500/30 transition-transform hover:scale-[1.02] disabled:opacity-50 disabled:hover:scale-100"
          >
            <PlusCircle className="h-4 w-4" aria-hidden="true" />
            Open account
          </button>
        </form>
      </section>
    </div>
  );
}
