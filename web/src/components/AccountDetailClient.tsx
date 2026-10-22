"use client";

import { useState } from "react";
import { useAccount } from "@/hooks/useAccounts";
import { formatMoney } from "@/lib/format";
import type { AccountDetailResponse } from "@/lib/client-api";

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
      <div>
        <h1 className="text-xl font-semibold">{account.ownerName}</h1>
        <p className="text-3xl font-semibold">
          {formatMoney(account.balance)}{" "}
          <span className="text-base font-normal text-slate-500">{account.currency}</span>
        </p>
      </div>

      <div>
        <h2 className="mb-2 text-lg font-semibold">Recent ledger entries</h2>
        {entries.length === 0 ? (
          <p className="text-slate-500">No activity yet.</p>
        ) : (
          <>
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-slate-500">
                  <th className="py-2">Direction</th>
                  <th className="py-2">Amount</th>
                  <th className="py-2">When</th>
                </tr>
              </thead>
              <tbody>
                {pageEntries.map((entry) => (
                  <tr key={entry.id} className="border-b border-slate-100">
                    <td className="py-2">
                      <span className={entry.direction === "CREDIT" ? "text-green-600" : "text-red-600"}>
                        {entry.direction}
                      </span>
                    </td>
                    <td className="py-2">{formatMoney(entry.amount)}</td>
                    <td className="py-2 text-slate-500">
                      {entry.createdAt ? new Date(entry.createdAt).toLocaleString() : ""}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="mt-3 flex items-center gap-3 text-sm">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
              >
                Previous
              </button>
              <span className="text-slate-500">
                Page {page + 1} of {pageCount}
              </span>
              <button
                onClick={() => setPage((p) => Math.min(pageCount - 1, p + 1))}
                disabled={page >= pageCount - 1}
                className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
