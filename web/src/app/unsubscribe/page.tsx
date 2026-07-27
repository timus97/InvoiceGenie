"use client";

import { useEffect, useState, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { publicUnsubscribe } from "@/lib/api/notifications";
import { ApiError } from "@/lib/errors";

type Status = "idle" | "loading" | "success" | "error" | "missing";

function UnsubscribeInner() {
  const search = useSearchParams();
  const token = search.get("token")?.trim() ?? "";
  const [status, setStatus] = useState<Status>(token ? "idle" : "missing");
  const [message, setMessage] = useState<string>("");

  useEffect(() => {
    if (!token) {
      setStatus("missing");
      return;
    }
    let cancelled = false;
    setStatus("loading");
    void publicUnsubscribe(token)
      .then(() => {
        if (cancelled) return;
        setStatus("success");
        setMessage(
          "You have been unsubscribed. You will no longer receive these notifications.",
        );
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setStatus("error");
        const msg =
          e instanceof ApiError
            ? e.message
            : e instanceof Error
              ? e.message
              : "Unable to process unsubscribe request.";
        setMessage(msg);
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-md flex-col justify-center">
      <Card className="space-y-4 p-6">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">
            Notification preferences
          </h1>
          <p className="mt-1 text-sm text-zinc-500">
            InvoiceGenie AR · email / messaging opt-out
          </p>
        </div>

        {status === "missing" ? (
          <p className="text-sm text-amber-700 dark:text-amber-300">
            This unsubscribe link is missing a token. Open the link from your
            email footer, or contact your account administrator.
          </p>
        ) : null}

        {status === "loading" ? (
          <p className="text-sm text-zinc-600 dark:text-zinc-300">
            Processing your request...
          </p>
        ) : null}

        {status === "success" ? (
          <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-100">
            {message}
          </div>
        ) : null}

        {status === "error" ? (
          <div className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-900 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-100">
            <p className="font-medium">We could not unsubscribe you</p>
            <p className="mt-1 text-xs opacity-90">{message}</p>
            <p className="mt-2 text-xs opacity-80">
              The link may have expired or already been used. Try again later or
              contact support.
            </p>
          </div>
        ) : null}

        <div className="pt-2">
          <Link href="/login">
            <Button type="button" variant="secondary">
              Back to sign in
            </Button>
          </Link>
        </div>
      </Card>
    </div>
  );
}

export default function UnsubscribePage() {
  return (
    <Suspense
      fallback={
        <div className="mx-auto max-w-md p-6 text-sm text-zinc-500">
          Loading...
        </div>
      }
    >
      <UnsubscribeInner />
    </Suspense>
  );
}
