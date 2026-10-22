"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { ShieldCheck, User, Lock, LogIn, AlertCircle } from "lucide-react";

const loginSchema = z.object({
  username: z.string().min(1, "Username is required"),
  password: z.string().min(1, "Password is required"),
});

type LoginForm = z.infer<typeof loginSchema>;

export default function LoginPage() {
  const router = useRouter();
  const [serverError, setServerError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema), defaultValues: { username: "alice", password: "password123" } });

  async function onSubmit(values: LoginForm) {
    setServerError(null);
    const response = await fetch("/api/auth", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(values),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({ detail: "Login failed" }));
      setServerError(body.detail ?? "Login failed");
      return;
    }
    router.push("/accounts");
  }

  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-4">
      <div className="w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-8 shadow-xl shadow-slate-200/60">
        <div className="mb-6 flex flex-col items-center text-center">
          <span className="mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-600 to-indigo-500 text-white shadow-lg shadow-indigo-500/30">
            <ShieldCheck className="h-6 w-6" aria-hidden="true" />
          </span>
          <h1 className="text-2xl font-semibold">LedgerGuard</h1>
          <p className="mt-2 rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-500">
            Demo users: <code className="font-semibold">alice</code>/<code>password123</code> (USER) or{" "}
            <code className="font-semibold">admin</code>/<code>admin123</code> (ADMIN)
          </p>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4">
          <label className="flex flex-col gap-1.5 text-sm font-medium text-slate-700">
            Username
            <span className="relative">
              <User className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <input
                {...register("username")}
                className="w-full rounded-lg border border-slate-300 py-2 pl-9 pr-3 text-sm outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
                autoComplete="username"
              />
            </span>
            {errors.username && (
              <span className="flex items-center gap-1 text-xs font-normal text-red-600">
                <AlertCircle className="h-3.5 w-3.5" aria-hidden="true" />
                {errors.username.message}
              </span>
            )}
          </label>
          <label className="flex flex-col gap-1.5 text-sm font-medium text-slate-700">
            Password
            <span className="relative">
              <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <input
                {...register("password")}
                type="password"
                className="w-full rounded-lg border border-slate-300 py-2 pl-9 pr-3 text-sm outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
                autoComplete="current-password"
              />
            </span>
            {errors.password && (
              <span className="flex items-center gap-1 text-xs font-normal text-red-600">
                <AlertCircle className="h-3.5 w-3.5" aria-hidden="true" />
                {errors.password.message}
              </span>
            )}
          </label>
          {serverError && (
            <p className="flex items-center gap-1.5 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">
              <AlertCircle className="h-4 w-4 shrink-0" aria-hidden="true" />
              {serverError}
            </p>
          )}
          <button
            type="submit"
            disabled={isSubmitting}
            className="mt-1 flex items-center justify-center gap-2 rounded-lg bg-gradient-to-br from-indigo-600 to-indigo-500 px-4 py-2.5 text-sm font-medium text-white shadow-md shadow-indigo-500/30 transition-transform hover:scale-[1.02] disabled:opacity-50 disabled:hover:scale-100"
          >
            <LogIn className="h-4 w-4" aria-hidden="true" />
            {isSubmitting ? "Signing in..." : "Sign in"}
          </button>
        </form>
      </div>
    </div>
  );
}
