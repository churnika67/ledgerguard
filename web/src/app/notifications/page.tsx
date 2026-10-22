"use client";

import { useState } from "react";
import { useAccounts } from "@/hooks/useAccounts";
import { useNotifications } from "@/hooks/useNotifications";
import { Bell, User, BellOff, AlertTriangle, ArrowDownLeft, ArrowUpRight, ChevronDown } from "lucide-react";

export default function NotificationsPage() {
  const { data: accounts } = useAccounts();
  const [accountId, setAccountId] = useState<string>("");
  const { data: notifications, isLoading, isError } = useNotifications(accountId || undefined);

  return (
    <div className="flex flex-col gap-4">
      <h1 className="flex items-center gap-2 text-xl font-semibold">
        <Bell className="h-5 w-5 text-indigo-600" aria-hidden="true" />
        Notifications
      </h1>
      <p className="text-sm text-slate-500">
        Polls every 5s for now; a live SSE stream from notification-service lands in a later step.
      </p>

      <label className="flex max-w-sm flex-col gap-1.5 text-sm font-medium text-slate-700">
        Account
        <span className="relative">
          <User className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
          <select
            value={accountId}
            onChange={(e) => setAccountId(e.target.value)}
            className="w-full appearance-none rounded-lg border border-slate-300 py-2 pl-9 pr-8 text-sm outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
          >
            <option value="">Select an account</option>
            {(accounts ?? []).map((a) => (
              <option key={a.id} value={a.id}>
                {a.ownerName}
              </option>
            ))}
          </select>
          <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
        </span>
      </label>

      {!accountId && (
        <p className="flex items-center gap-1.5 text-slate-500">
          <BellOff className="h-4 w-4" aria-hidden="true" />
          Pick an account to see its notifications.
        </p>
      )}
      {isLoading && accountId && <p className="text-slate-500">Loading...</p>}
      {isError && (
        <p className="flex items-center gap-1.5 text-red-600">
          <AlertTriangle className="h-4 w-4" aria-hidden="true" />
          Could not load notifications (is notification-service running?).
        </p>
      )}

      {notifications && notifications.length === 0 && (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-slate-300 bg-white py-10 text-center">
          <BellOff className="h-7 w-7 text-slate-400" aria-hidden="true" />
          <p className="text-slate-500">No notifications yet.</p>
        </div>
      )}

      {notifications && notifications.length > 0 && (
        <ul className="flex flex-col gap-2">
          {notifications.map((n) => {
            const isReceived = n.type === "PAYMENT_RECEIVED";
            return (
              <li key={n.id} className="rounded-xl border border-slate-200 bg-white p-3.5 text-sm shadow-sm shadow-slate-200/40">
                <div className="flex items-center justify-between">
                  <span className="flex items-center gap-1.5 font-medium">
                    <span
                      className={`flex h-6 w-6 items-center justify-center rounded-full ${
                        isReceived ? "bg-emerald-50 text-emerald-600" : "bg-rose-50 text-rose-600"
                      }`}
                    >
                      {isReceived ? (
                        <ArrowDownLeft className="h-3.5 w-3.5" aria-hidden="true" />
                      ) : (
                        <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
                      )}
                    </span>
                    {n.type.replace("_", " ")}
                  </span>
                  <span className="text-slate-500">{new Date(n.createdAt).toLocaleString()}</span>
                </div>
                <div className="mt-1 text-slate-700">{n.message}</div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
