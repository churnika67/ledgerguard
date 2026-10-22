"use client";

import { useState } from "react";
import { useAccounts } from "@/hooks/useAccounts";
import { useNotifications } from "@/hooks/useNotifications";

export default function NotificationsPage() {
  const { data: accounts } = useAccounts();
  const [accountId, setAccountId] = useState<string>("");
  const { data: notifications, isLoading, isError } = useNotifications(accountId || undefined);

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold">Notifications</h1>
      <p className="text-sm text-slate-500">
        Polls every 5s for now; a live SSE stream from notification-service lands in a later step.
      </p>

      <label className="flex max-w-sm flex-col gap-1 text-sm">
        Account
        <select
          value={accountId}
          onChange={(e) => setAccountId(e.target.value)}
          className="rounded border border-slate-300 px-3 py-2"
        >
          <option value="">Select an account</option>
          {(accounts ?? []).map((a) => (
            <option key={a.id} value={a.id}>
              {a.ownerName}
            </option>
          ))}
        </select>
      </label>

      {!accountId && <p className="text-slate-500">Pick an account to see its notifications.</p>}
      {isLoading && accountId && <p className="text-slate-500">Loading...</p>}
      {isError && <p className="text-red-600">Could not load notifications (is notification-service running?).</p>}

      {notifications && notifications.length === 0 && <p className="text-slate-500">No notifications yet.</p>}

      {notifications && notifications.length > 0 && (
        <ul className="flex flex-col gap-2">
          {notifications.map((n) => (
            <li key={n.id} className="rounded border border-slate-200 bg-white p-3 text-sm">
              <div className="flex items-center justify-between">
                <span className="font-medium">{n.type.replace("_", " ")}</span>
                <span className="text-slate-500">{new Date(n.createdAt).toLocaleString()}</span>
              </div>
              <div>{n.message}</div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
