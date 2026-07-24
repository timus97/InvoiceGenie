"use client";

import { FormEvent, Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input, Label } from "@/components/ui/input";
import { useAuth } from "@/components/auth-provider";
import { isAuthRequired } from "@/lib/auth-session";

function LoginForm() {
  const { loginWithPassword, loginWithApiKey, session, ready } = useAuth();
  const router = useRouter();
  const search = useSearchParams();
  const next = search.get("next") || "/";

  const [mode, setMode] = useState<"user" | "apikey">("user");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [busy, setBusy] = useState(false);

  if (ready && session) {
    if (typeof window !== "undefined") {
      router.replace(next.startsWith("/") ? next : "/");
    }
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      if (mode === "user") {
        if (!username.trim() || !password) {
          toast.error("Username and password required");
          return;
        }
        await loginWithPassword(username.trim(), password);
      } else {
        if (!apiKey.trim()) {
          toast.error("API key required");
          return;
        }
        await loginWithApiKey(apiKey.trim());
      }
      toast.success("Signed in");
      router.replace(next.startsWith("/") ? next : "/");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Login failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-md flex-col justify-center">
      <div className="mb-6 text-center">
        <p className="text-xs font-semibold uppercase tracking-wider text-indigo-600">
          InvoiceGenie
        </p>
        <h1 className="mt-1 text-2xl font-semibold text-zinc-900 dark:text-zinc-50">
          Sign in
        </h1>
        <p className="mt-2 text-sm text-zinc-500">
          Tenant is derived from your credentials — not free-form UUID spoofing
          {isAuthRequired() ? " (production mode)." : "."}
        </p>
      </div>

      <Card>
        <div className="mb-4 flex gap-2">
          <Button
            type="button"
            size="sm"
            variant={mode === "user" ? "primary" : "secondary"}
            onClick={() => setMode("user")}
          >
            Username
          </Button>
          <Button
            type="button"
            size="sm"
            variant={mode === "apikey" ? "primary" : "secondary"}
            onClick={() => setMode("apikey")}
          >
            API key
          </Button>
        </div>

        <form onSubmit={onSubmit} className="space-y-4">
          {mode === "user" ? (
            <>
              <div>
                <Label htmlFor="username">Username</Label>
                <Input
                  id="username"
                  autoComplete="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="mt-1"
                />
              </div>
              <div>
                <Label htmlFor="password">Password</Label>
                <Input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="mt-1"
                />
              </div>
            </>
          ) : (
            <div>
              <Label htmlFor="apiKey">API key</Label>
              <Input
                id="apiKey"
                type="password"
                autoComplete="off"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                className="mt-1 font-mono text-sm"
                spellCheck={false}
              />
              <p className="mt-1 text-xs text-zinc-500">
                Tenant is resolved server-side from the key mapping.
              </p>
            </div>
          )}

          <Button type="submit" className="w-full" disabled={busy}>
            {busy ? "Signing in…" : "Sign in"}
          </Button>
        </form>
      </Card>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="mx-auto max-w-md p-8 text-center text-sm text-zinc-500">
          Loading sign-in…
        </div>
      }
    >
      <LoginForm />
    </Suspense>
  );
}
