"use client";

import { FormEvent, Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input, Label } from "@/components/ui/input";
import { useAuth } from "@/components/auth-provider";

function LoginForm() {
  const { loginWithPassword, loginWithApiKey, session, ready } = useAuth();
  const router = useRouter();
  const search = useSearchParams();
  const next = search.get("next") || "/";

  const [mode, setMode] = useState<"user" | "apikey">("user");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const showDemoHints =
    process.env.NEXT_PUBLIC_SHOW_DEMO_LOGIN_HINTS === "true";

  useEffect(() => {
    if (ready && session) {
      router.replace(next.startsWith("/") ? next : "/");
    }
  }, [ready, session, next, router]);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === "user") {
        if (!email.trim() || !password) {
          setError("Email and password are required");
          toast.error("Email and password are required");
          return;
        }
        if (!email.includes("@")) {
          setError("Enter a valid email address");
          toast.error("Enter a valid email address");
          return;
        }
        await loginWithPassword(email.trim().toLowerCase(), password);
      } else {
        if (!apiKey.trim()) {
          setError("API key is required");
          toast.error("API key is required");
          return;
        }
        await loginWithApiKey(apiKey.trim());
      }
      toast.success("Signed in securely");
      router.replace(next.startsWith("/") ? next : "/");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Login failed";
      setError(message);
      toast.error(message);
      setPassword("");
      setApiKey("");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-md flex-col justify-center px-4">
      <div className="mb-6 text-center">
        <p className="text-xs font-semibold uppercase tracking-wider text-indigo-600">
          InvoiceGenie
        </p>
        <h1 className="mt-1 text-2xl font-semibold text-zinc-900 dark:text-zinc-50">
          Sign in
        </h1>
        <p className="mt-2 text-sm text-zinc-500">
          Email + password. Access tokens (15 min) and refresh tokens (7 days)
          stay server-side — never exposed in browser storage.
        </p>
      </div>

      <Card>
        <div className="mb-4 flex gap-2" role="tablist" aria-label="Sign-in method">
          <Button
            type="button"
            size="sm"
            variant={mode === "user" ? "primary" : "secondary"}
            onClick={() => {
              setMode("user");
              setError(null);
            }}
          >
            Email
          </Button>
          <Button
            type="button"
            size="sm"
            variant={mode === "apikey" ? "primary" : "secondary"}
            onClick={() => {
              setMode("apikey");
              setError(null);
            }}
          >
            API key
          </Button>
        </div>

        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          {mode === "user" ? (
            <>
              <div>
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  autoFocus
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="mt-1"
                  disabled={busy}
                  required
                />
              </div>
              <div>
                <Label htmlFor="password">Password</Label>
                <Input
                  id="password"
                  name="password"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="mt-1"
                  disabled={busy}
                  required
                />
              </div>
            </>
          ) : (
            <div>
              <Label htmlFor="apiKey">API key (M2M)</Label>
              <Input
                id="apiKey"
                name="apiKey"
                type="password"
                autoComplete="off"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                className="mt-1 font-mono text-sm"
                spellCheck={false}
                disabled={busy}
                required
              />
            </div>
          )}

          {error ? (
            <p
              role="alert"
              className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-300"
            >
              {error}
            </p>
          ) : null}

          <Button type="submit" className="w-full" disabled={busy}>
            {busy ? "Signing in…" : "Sign in"}
          </Button>
        </form>

        {showDemoHints ? (
          <div className="mt-5 rounded-md border border-zinc-200 bg-zinc-50 p-3 text-xs text-zinc-600 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-400">
            <p className="font-medium text-zinc-700 dark:text-zinc-300">
              Bootstrap admin (first boot)
            </p>
            <p className="mt-1 font-mono">admin@invoicegenie.local</p>
            <p className="font-mono">Admin123!</p>
            <p className="mt-2 text-zinc-500">
              Change this password after first login. Create more users under
              Admin → Users.
            </p>
          </div>
        ) : null}
      </Card>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="mx-auto max-w-md py-24 text-center text-sm text-zinc-500">
          Loading…
        </div>
      }
    >
      <LoginForm />
    </Suspense>
  );
}