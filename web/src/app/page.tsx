import { redirect } from "next/navigation";
import { getSessionToken } from "@/lib/session";

export default function RootPage() {
  redirect(getSessionToken() ? "/accounts" : "/login");
}
