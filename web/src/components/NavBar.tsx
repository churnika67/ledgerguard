"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { ShieldCheck, Wallet, Send, Bell, LogOut } from "lucide-react";

const LINKS = [
  { href: "/accounts", label: "Accounts", icon: Wallet },
  { href: "/send", label: "Send", icon: Send },
  { href: "/notifications", label: "Notifications", icon: Bell },
];

export function NavBar() {
  const pathname = usePathname();
  const router = useRouter();

  async function logout() {
    await fetch("/api/auth/logout", { method: "POST" });
    router.push("/login");
  }

  if (pathname === "/login") {
    return null;
  }

  return (
    <nav className="sticky top-0 z-10 border-b border-slate-200/70 bg-white/80 backdrop-blur-md">
      <div className="mx-auto flex max-w-4xl items-center justify-between px-4 py-3">
        <div className="flex items-center gap-6">
          <Link href="/accounts" className="flex items-center gap-2 font-semibold text-slate-900">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-600 to-indigo-500 text-white shadow-sm shadow-indigo-500/30">
              <ShieldCheck className="h-4 w-4" aria-hidden="true" />
            </span>
            LedgerGuard
          </Link>
          <div className="flex items-center gap-1">
            {LINKS.map((link) => {
              const Icon = link.icon;
              const active = pathname.startsWith(link.href);
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  className={`flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm transition-colors ${
                    active
                      ? "bg-indigo-50 font-medium text-indigo-700"
                      : "text-slate-500 hover:bg-slate-100 hover:text-slate-900"
                  }`}
                >
                  <Icon className="h-4 w-4" aria-hidden="true" />
                  {link.label}
                </Link>
              );
            })}
          </div>
        </div>
        <button
          onClick={logout}
          className="flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900"
        >
          <LogOut className="h-4 w-4" aria-hidden="true" />
          Log out
        </button>
      </div>
    </nav>
  );
}
