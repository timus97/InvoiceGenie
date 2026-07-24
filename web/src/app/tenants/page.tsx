"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { StatusBadge } from "@/components/ui/status-badge";
import { useTenant } from "@/components/tenant-provider";
import { activateTenant, createTenant, listTenants, suspendTenant } from "@/lib/api/tenants";
import { ApiError } from "@/lib/errors";

export default function TenantsPage() {
  const { tenantId, ready } = useTenant();
  const qc = useQueryClient();
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [currency, setCurrency] = useState("USD");

  const list = useQuery({
    queryKey: ["tenants", tenantId],
    enabled: ready,
    queryFn: ({ signal }) => listTenants(tenantId, undefined, signal),
  });

  const createMut = useMutation({
    mutationFn: () =>
      createTenant(tenantId, {
        code: code.trim(),
        name: name.trim(),
        baseCurrency: currency.trim() || "USD",
      }),
    onSuccess: () => {
      toast.success("Tenant created");
      setCode("");
      setName("");
      void qc.invalidateQueries({ queryKey: ["tenants"] });
    },
    onError: (e: Error) => toast.error(e instanceof ApiError ? e.message : e.message),
  });

  return (
    <div className="space-y-6">
      <PageHeader title="Tenants" description="Platform tenant registry for onboarding organizations" />
      <Card className="space-y-3 p-4">
        <h2 className="text-sm font-semibold">Create tenant</h2>
        <div className="grid gap-3 sm:grid-cols-3">
          <div>
            <Label htmlFor="tcode">Code</Label>
            <Input id="tcode" value={code} onChange={(e) => setCode(e.target.value)} placeholder="ACME" />
          </div>
          <div>
            <Label htmlFor="tname">Name</Label>
            <Input id="tname" value={name} onChange={(e) => setName(e.target.value)} placeholder="Acme Corp" />
          </div>
          <div>
            <Label htmlFor="tcur">Base currency</Label>
            <Input id="tcur" value={currency} onChange={(e) => setCurrency(e.target.value)} />
          </div>
        </div>
        <Button
          disabled={!code.trim() || !name.trim() || createMut.isPending}
          onClick={() => createMut.mutate()}
        >
          Create
        </Button>
      </Card>
      <Card className="overflow-x-auto p-0">
        <table className="w-full text-sm">
          <thead className="border-b bg-zinc-50 text-left dark:bg-zinc-900">
            <tr>
              <th className="px-4 py-2">Code</th>
              <th className="px-4 py-2">Name</th>
              <th className="px-4 py-2">Currency</th>
              <th className="px-4 py-2">Status</th>
              <th className="px-4 py-2">Actions</th>
            </tr>
          </thead>
          <tbody>
            {(list.data ?? []).map((t) => (
              <tr key={t.id} className="border-b last:border-0">
                <td className="px-4 py-2 font-mono text-xs">{t.code}</td>
                <td className="px-4 py-2">{t.name}</td>
                <td className="px-4 py-2">{t.baseCurrency}</td>
                <td className="px-4 py-2">
                  <StatusBadge status={t.status} />
                </td>
                <td className="px-4 py-2 space-x-2">
                  {t.status !== "ACTIVE" && (
                    <Button
                      variant="secondary"
                      onClick={() =>
                        activateTenant(tenantId, t.id).then(() => {
                          toast.success("Activated");
                          void qc.invalidateQueries({ queryKey: ["tenants"] });
                        })
                      }
                    >
                      Activate
                    </Button>
                  )}
                  {t.status === "ACTIVE" && (
                    <Button
                      variant="secondary"
                      onClick={() =>
                        suspendTenant(tenantId, t.id).then(() => {
                          toast.success("Suspended");
                          void qc.invalidateQueries({ queryKey: ["tenants"] });
                        })
                      }
                    >
                      Suspend
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  );
}