import { redirect } from "next/navigation";
import { listAccounts } from "@/lib/backend";
import { getSessionToken } from "@/lib/session";
import { AccountsClient } from "@/components/AccountsClient";

// Server Component: does the initial fetch server-side (fast first paint, and the JWT never
// needs to reach the browser), then hands off to a Client Component for interactivity.
export default async function AccountsPage() {
  const token = getSessionToken();
  if (!token) {
    redirect("/login");
  }

  const accounts = await listAccounts(token);
  return <AccountsClient initialAccounts={accounts} />;
}
