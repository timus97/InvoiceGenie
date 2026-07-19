"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Activity, ExternalLink } from "lucide-react";
import { useTenant } from "@/components/tenant-provider";
import { checkBackendHealth } from "@/lib/api/client";

export function Topbar() {
  const { tenantId, ready } = useTenant();
  const [health, setHealth] = useState<"unknown" | "up" | "down">("unknown");

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      const result = await checkBackendHealth();
      if (!cancelled) setHealth(result.ok ? "up" : "down");
    };
    void tick();
    const id = window.setInterval(tick, 30_000);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, []);

  return (
    <header className="flex h-14 items-center justify-between border-b border-zinc-200 bg-white px-6 dark:border-zinc-800 dark:bg-zinc-950">
      <div className="flex min-w-0 items-center gap-3 text-sm">
        <span className="text-zinc-500 dark:text-zinc-400">Tenant</span>
        <code className="truncate rounded-md bg-zinc-100 px-2 py-1 font-mono text-xs text-zinc-800 dark:bg-zinc-900 dark:text-zinc-200">
          {ready ? tenantId : "…"}
        </code>
        <Link
          href="/settings"
          className="text-xs font-medium text-indigo-600 hover:underline dark:text-indigo-400"
        >
          Change
        </Link>
      </div>
      <div className="flex items-center gap-4 text-sm">
        <span className="inline-flex items-center gap-1.5 text-zinc-600 dark:text-zinc-300">
          <Activity
            className={
              health === "up"
                ? "h-4 w-4 text-emerald-500"
                : health === "down"
                  ? "h-4 w-4 text-rose-500"
                  : "h-4 w-4 text-zinc-400"
            }
          />
          API{" "}
          {health === "up" ? "up" : health === "down" ? "down" : "checking…"}
        </span>
        <a
          href="http://localhost:8080/q/swagger-ui/"
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-1 text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
        >
          Swagger
          <ExternalLink className="h-3.5 w-3.5" />
        </a>
      </div>
    </header>
  );
}