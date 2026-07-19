"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { useTenant } from "@/components/tenant-provider";
import { checkBackendHealth } from "@/lib/api/client";
import { DEFAULT_TENANT_ID, isValidTenantId } from "@/lib/tenant";

export default function SettingsPage() {
  const { tenantId, setTenantId } = useTenant();
  const [value, setValue] = useState(tenantId);
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
        description="MVP auth is header-based. Every API call sends X-Tenant-Id from this value."
      />

      <div className="grid max-w-3xl gap-6">
        <Card>
          <Label htmlFor="tenant">Active tenant ID</Label>
          <Input
            id="tenant"
            className="mt-2 font-mono text-sm"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            disabled={!allowOverride}
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
            <Button type="button" onClick={onSave} disabled={!allowOverride}>
              Save tenant
            </Button>
          </div>
          {!allowOverride ? (
            <p className="mt-3 text-xs text-amber-700 dark:text-amber-300">
              Tenant override is disabled via NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE.
            </p>
          ) : null}
        </Card>

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
            <code className="font-mono">/q/*</code>; Next.js rewrites proxy to Quarkus on port 8080.
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
          </dl>
          <p className="mt-4 text-xs text-zinc-500">
            Multi-tenant caveat: switching tenant clears React Query caches so rows never leak across tenants.
            Do not share a browser profile across production tenants without logging out.
          </p>
        </Card>
      </div>
    </div>
  );
}