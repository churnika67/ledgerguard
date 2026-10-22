"use client";

import { useState } from "react";
import { useAccount } from "@/hooks/useAccounts";
import { formatMoney } from "@/lib/format";
import type { AccountDetailResponse } from "@/lib/client-api";
import { Wallet, ArrowDownLeft, ArrowUpRight, ChevronLeft, ChevronRight, History } from "lucide-react";

const PAGE_SIZE = 5;

export function AccountDetailClient({
  accountId,
  initialAccount,
}: {
  accountId: string;
  initialAccount: AccountDetailResponse;
}) {
  const { data } = useAccount(accountId, initialAccount);
  const account = data ?? initialAccount;
  const entries = account.recentEntries ?? [];
  const [page, setPage] = useState(0);
  const pageCount = Math.max(1, Math.ceil(entries.length / PAGE_SIZE));
  const pageEntries = entries.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE);

  return (
    <div className="flex flex-col gap-6">
      <div className="rounded-2xl bg-gradient-to-br from-indigo-600 via-indigo-600 to-slate-900 p-6 text-white shadow-lg shadow-indigo-900/20">
        <div className="mb-6 flex items-center justify-between">
          <span className="flex items-center gap-2 text-sm text-indigo-100">
            <Wallet className="h-4 w-4" aria-hidden="true" />
            {account.ownerName}
          </span>
          <span className="rounded-full bg-white/15 px-2.5 py-0.5 text-xs font-medium tracking-wide">
            {account.currency}
          </span>
        </div>
        <p className="text-3xl font-semibold tabular-nums">
          {formatMoney(account.balance)}{" "}
          <span className="text-base font-normal text-indigo-100">{account.currency}</span>
        </p>
      </div>

      <div>
        <h2 className="mb-2 flex items-center gap-2 text-lg font-semibold">
          <History className="h-5 w-5 text-indigo-600" aria-hidden="true" />
          Recent ledger entries
        </h2>
        {entries.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-slate-300 bg-white py-8 text-center text-slate-500">
            No activity yet.
          </p>
        ) : (
          <>
            <ul className="flex flex-col gap-2">
              {pageEntries.map((entry) => {
                const isCredit = entry.direction === "CREDIT";
                return (
                  <li
                    key={entry.id}
                    className="flex items-center justify-between rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm shadow-slate-200/40"
                  >
                    <div className="flex items-center gap-3">
                      <span
                        className={`flex h-9 w-9 items-center justify-center rounded-full ${
                          isCredit ? "bg-emerald-50 text-emerald-600" : "bg-rose-50 text-rose-600"
                        }`}
                      >
                        {isCredit ? (
                          <ArrowDownLeft className="h-4 w-4" aria-hidden="true" />
                        ) : (
                          <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
                        )}
                      </span>
                      <span className={isCredit ? "font-medium text-emerald-600" : "font-medium text-rose-600"}>
                        {entry.direction}
                      </span>
                    </div>
                    <div className="text-right">
                      <div className={`font-medium tabular-nums ${isCredit ? "text-emerald-600" : "text-rose-600"}`}>
                        {isCredit ? "+" : "-"}
                        {formatMoney(entry.amount)}
                      </div>
                      <div className="text-xs text-slate-400">
                        {entry.createdAt ? new Date(entry.createdAt).toLocaleString() : ""}
                      </div>
                    </div>
                  </li>
                );
              })}
            </ul>
            <div className="mt-4 flex items-center justify-center gap-3 text-sm">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="flex items-center gap-1 rounded-full border border-slate-300 px-3 py-1.5 transition-colors hover:bg-slate-100 disabled:opacity-40 disabled:hover:bg-transparent"
              >
                <ChevronLeft className="h-4 w-4" aria-hidden="true" />
                Previous
              </button>
              <span className="text-slate-500">
                Page {page + 1} of {pageCount}
              </span>
              <button
                onClick={() => setPage((p) => Math.min(pageCount - 1, p + 1))}
                disabled={page >= pageCount - 1}
                className="flex items-center gap-1 rounded-full border border-slate-300 px-3 py-1.5 transition-colors hover:bg-slate-100 disabled:opacity-40 disabled:hover:bg-transparent"
              >
                Next
                <ChevronRight className="h-4 w-4" aria-hidden="true" />
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
