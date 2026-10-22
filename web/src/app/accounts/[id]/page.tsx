import { redirect } from "next/navigation";
import { getAccount } from "@/lib/backend";
import { getSessionToken } from "@/lib/session";
import { AccountDetailClient } from "@/components/AccountDetailClient";

export default async function AccountDetailPage({ params }: { params: { id: string } }) {
  const token = getSessionToken();
  if (!token) {
    redirect("/login");
  }

  const account = await getAccount(token, params.id);
  return <AccountDetailClient accountId={params.id} initialAccount={account} />;
}
