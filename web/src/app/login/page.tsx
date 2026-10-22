"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

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
    <div className="mx-auto flex min-h-screen max-w-sm flex-col justify-center px-4">
      <h1 className="mb-1 text-2xl font-semibold">LedgerGuard</h1>
      <p className="mb-6 text-sm text-slate-500">
        Demo users: <code>alice</code>/<code>password123</code> (USER) or <code>admin</code>/<code>admin123</code> (ADMIN).
      </p>
      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-3">
        <label className="flex flex-col gap-1 text-sm">
          Username
          <input
            {...register("username")}
            className="rounded border border-slate-300 px-3 py-2"
            autoComplete="username"
          />
          {errors.username && <span className="text-xs text-red-600">{errors.username.message}</span>}
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Password
          <input
            {...register("password")}
            type="password"
            className="rounded border border-slate-300 px-3 py-2"
            autoComplete="current-password"
          />
          {errors.password && <span className="text-xs text-red-600">{errors.password.message}</span>}
        </label>
        {serverError && <p className="text-sm text-red-600">{serverError}</p>}
        <button
          type="submit"
          disabled={isSubmitting}
          className="mt-2 rounded bg-slate-900 px-4 py-2 text-white disabled:opacity-50"
        >
          {isSubmitting ? "Signing in..." : "Sign in"}
        </button>
      </form>
    </div>
  );
}
