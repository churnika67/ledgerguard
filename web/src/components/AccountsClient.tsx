"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useAccounts, useOpenAccount } from "@/hooks/useAccounts";
import { formatMoney } from "@/lib/format";
import type { AccountResponse } from "@/lib/client-api";

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
    <div className="flex flex-col gap-8">
      <section>
        <h1 className="mb-4 text-xl font-semibold">Your accounts</h1>
        {list.length === 0 ? (
          <p className="text-slate-500">No accounts yet -- open one below.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {list.map((account) => (
              <li key={account.id}>
                <Link
                  href={`/accounts/${account.id}`}
                  className="flex items-center justify-between rounded border border-slate-200 bg-white px-4 py-3 hover:border-slate-400"
                >
                  <span className="font-medium">{account.ownerName}</span>
                  <span className="text-slate-500">
                    {formatMoney(account.balance)} {account.currency}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h2 className="mb-3 text-lg font-semibold">Open a new account</h2>
        <form onSubmit={handleSubmit(onSubmit)} className="flex max-w-sm flex-col gap-3">
          <label className="flex flex-col gap-1 text-sm">
            Owner name
            <input {...register("ownerName")} className="rounded border border-slate-300 px-3 py-2" />
            {errors.ownerName && <span className="text-xs text-red-600">{errors.ownerName.message}</span>}
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Currency
            <input {...register("currency")} className="rounded border border-slate-300 px-3 py-2" maxLength={3} />
            {errors.currency && <span className="text-xs text-red-600">{errors.currency.message}</span>}
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Opening balance
            <input {...register("openingBalance")} className="rounded border border-slate-300 px-3 py-2" />
            {errors.openingBalance && <span className="text-xs text-red-600">{errors.openingBalance.message}</span>}
          </label>
          <button
            type="submit"
            disabled={isSubmitting}
            className="mt-2 rounded bg-slate-900 px-4 py-2 text-white disabled:opacity-50"
          >
            Open account
          </button>
        </form>
      </section>
    </div>
  );
}
