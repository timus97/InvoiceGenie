"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { useTenant } from "@/components/tenant-provider";
import { useAuth } from "@/components/auth-provider";
import { checkBackendHealth } from "@/lib/api/client";
import { DEFAULT_TENANT_ID, isValidTenantId } from "@/lib/tenant";
import { isAuthRequired } from "@/lib/auth-session";

export default function SettingsPage() {
  const { tenantId, setTenantId } = useTenant();
  const { session, logout } = useAuth();
  const [value, setValue] = useState(tenantId);
  // Production builds set NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE=false
  const allowOverride =
    process.env.NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE !== "false";

  const health = useQuery({
    queryKey: ["backend-health"],
    queryFn: checkBackendHealth,
    refetchInterval: 15000,
  });

  const onSave = () => {
    if (!allowOverride) {
      toast.error("Tenant override is disabled");
      return;
    }
    if (!isValidTenantId(value)) {
      toast.error("Enter a valid UUID for X-Tenant-Id");
      return;
    }
    const ok = setTenantId(value);
    if (ok) toast.success("Tenant updated — caches cleared");
  };

  return (
    <div>
      <PageHeader
        title="Settings"
        description={
          session
            ? "Session tenant is derived from login credentials. API calls send X-Tenant-Id bound to that tenant."
            : "MVP auth is header-based. Every API call sends X-Tenant-Id from this value."
        }
      />

      <div className="grid max-w-3xl gap-6">
        <Card>
          <h2 className="mb-2 text-sm font-semibold">Session</h2>
          {session ? (
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between gap-4">
                <dt className="text-zinc-500">Subject</dt>
                <dd className="font-mono text-xs">{session.subject}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-zinc-500">Method</dt>
                <dd>{session.method}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-zinc-500">Roles</dt>
                <dd className="font-mono text-xs">{session.roles.join(", ") || "—"}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-zinc-500">Tenant</dt>
                <dd className="font-mono text-xs">{session.tenantId}</dd>
              </div>
            </dl>
          ) : (
            <p className="text-sm text-zinc-500">
              Not signed in.{" "}
              <Link href="/login" className="text-indigo-600 hover:underline dark:text-indigo-400">
                Sign in
              </Link>{" "}
              to obtain a JWT or API-key session.
            </p>
          )}
          <div className="mt-4 flex gap-2">
            {session ? (
              <Button type="button" variant="secondary" onClick={() => logout()}>
                Sign out
              </Button>
            ) : (
              <Link href="/login">
                <Button type="button">Sign in</Button>
              </Link>
            )}
          </div>
        </Card>

        {allowOverride ? (
          <Card>
            <Label htmlFor="tenant">Active tenant ID</Label>
            <Input
              id="tenant"
              className="mt-2 font-mono text-sm"
              value={value}
              onChange={(e) => setValue(e.target.value)}
              spellCheck={false}
            />
            <p className="mt-2 text-xs text-zinc-500">
              Default smoke tenant:{" "}
              <button
                type="button"
                className="font-mono text-indigo-600 hover:underline dark:text-indigo-400"
                onClick={() => setValue(DEFAULT_TENANT_ID)}
              >
                {DEFAULT_TENANT_ID}
              </button>
            </p>
            <div className="mt-4 flex gap-2">
              <Button type="button" onClick={onSave}>
                Save tenant
              </Button>
            </div>
            <p className="mt-3 text-xs text-amber-700 dark:text-amber-300">
              Dev/demo only. Production sets NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE=false so
              tenant comes from login, not free-form UUID spoofing.
            </p>
          </Card>
        ) : (
          <Card>
            <h2 className="mb-2 text-sm font-semibold">Active tenant ID</h2>
            <code className="block rounded-md bg-zinc-100 px-2 py-1 font-mono text-xs dark:bg-zinc-900">
              {session?.tenantId ?? tenantId}
            </code>
            <p className="mt-3 text-xs text-amber-700 dark:text-amber-300">
              Tenant override is disabled (NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE=false).
              Tenant is bound from authentication only.
            </p>
          </Card>
        )}

        <Card>
          <h2 className="mb-2 text-sm font-semibold">Backend health</h2>
          <p className="text-sm">
            Status:{" "}
            {health.isLoading ? (
              "Checking..."
            ) : health.data?.ok ? (
              <span className="font-medium text-emerald-600">OK ({health.data.status})</span>
            ) : (
              <span className="font-medium text-rose-600">
                Down ({health.data?.status ?? 0}) {health.data?.detail}
              </span>
            )}
          </p>
          <p className="mt-2 text-xs text-zinc-500">
            Browser calls same-origin <code className="font-mono">/api/v1/*</code> and{" "}
            <code className="font-mono">/q/*</code>; Next.js rewrites proxy to Quarkus.
          </p>
          <Button
            type="button"
            variant="secondary"
            className="mt-3"
            onClick={() => health.refetch()}
          >
            Recheck
          </Button>
        </Card>

        <Card>
          <h2 className="mb-2 text-sm font-semibold">Environment</h2>
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between gap-4">
              <dt className="text-zinc-500">App name</dt>
              <dd className="font-medium">
                {process.env.NEXT_PUBLIC_APP_NAME ?? "InvoiceGenie AR"}
              </dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-zinc-500">Default tenant</dt>
              <dd className="font-mono text-xs">{DEFAULT_TENANT_ID}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-zinc-500">Tenant override</dt>
              <dd>{allowOverride ? "enabled" : "disabled"}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-zinc-500">Auth required</dt>
              <dd>{isAuthRequired() ? "yes" : "no"}</dd>
            </div>
          </dl>
          <p className="mt-4 text-xs text-zinc-500">
            Multi-tenant caveat: switching tenant or signing out clears React Query caches so rows never leak across tenants.
          </p>
        </Card>
      </div>
    </div>
  );
}
