"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";

const LINKS = [
  { href: "/accounts", label: "Accounts" },
  { href: "/send", label: "Send" },
  { href: "/notifications", label: "Notifications" },
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
    <nav className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3">
      <div className="flex items-center gap-6">
        <span className="font-semibold">LedgerGuard</span>
        {LINKS.map((link) => (
          <Link
            key={link.href}
            href={link.href}
            className={pathname.startsWith(link.href) ? "font-medium text-slate-900" : "text-slate-500"}
          >
            {link.label}
          </Link>
        ))}
      </div>
      <button onClick={logout} className="text-sm text-slate-500 hover:text-slate-900">
        Log out
      </button>
    </nav>
  );
}
