"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Plus, Search } from "lucide-react";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusBadge } from "@/components/ui/status-badge";
import { Button } from "@/components/ui/button";
import { Input, Label, Select } from "@/components/ui/input";
import { useTenant } from "@/components/tenant-provider";
import {
  createCustomer,
  customerStats,
  listCustomers,
} from "@/lib/api/customers";
import { formatMoney } from "@/lib/money";
import { ApiError } from "@/lib/errors";
import type { CustomerStatus } from "@/types/ar";

export default function CustomersPage() {
  const { tenantId, ready } = useTenant();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<string>("");
  const [search, setSearch] = useState("");
  const [includeDeleted, setIncludeDeleted] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [code, setCode] = useState("");
  const [legalName, setLegalName] = useState("");
  const [currency, setCurrency] = useState("USD");

  const listKey = useMemo(
    () => ["customers", tenantId, status, search, includeDeleted] as const,
    [tenantId, status, search, includeDeleted],
  );

  const customers = useQuery({
    queryKey: listKey,
    enabled: ready,
    queryFn: ({ signal }) =>
      listCustomers(tenantId, {
        status: status || undefined,
        search: search || undefined,
        includeDeleted,
        signal,
      }),
  });

  const stats = useQuery({
    queryKey: ["customer-stats", tenantId],
    enabled: ready,
    queryFn: ({ signal }) => customerStats(tenantId, signal),
  });

  const createMut = useMutation({
    mutationFn: () =>
      createCustomer(tenantId, {
        customerCode: code.trim(),
        legalName: legalName.trim(),
        currency: currency.trim() || "USD",
      }),
    onSuccess: (created) => {
      toast.success(`Customer ${created.customerCode} created`);
      setShowCreate(false);
      setCode("");
      setLegalName("");
      setCurrency("USD");
      void queryClient.invalidateQueries({ queryKey: ["customers", tenantId] });
      void queryClient.invalidateQueries({
        queryKey: ["customer-stats", tenantId],
      });
    },
    onError: (err: Error) => {
      toast.error(err instanceof ApiError ? err.message : err.message);
    },
  });

  return (
    <div>
      <PageHeader
        title="Customers"
        description="Master data for AR customers. Create customers before issuing invoices."
        actions={
          <Button type="button" onClick={() => setShowCreate((v) => !v)}>
            <Plus className="h-4 w-4" />
            New customer
          </Button>
        }
      />

      <div className="mb-6 grid gap-3 sm:grid-cols-3">
        <Card>
          <div className="text-xs font-medium uppercase tracking-wide text-zinc-500">
            Active
          </div>
          <div className="mt-1 text-2xl font-semibold tabular-nums">
            {stats.data?.active ?? "—"}
          </div>
        </Card>
        <Card>
          <div className="text-xs font-medium uppercase tracking-wide text-zinc-500">
            Blocked
          </div>
          <div className="mt-1 text-2xl font-semibold tabular-nums">
            {stats.data?.blocked ?? "—"}
          </div>
        </Card>
        <Card>
          <div className="text-xs font-medium uppercase tracking-wide text-zinc-500">
            Deleted
          </div>
          <div className="mt-1 text-2xl font-semibold tabular-nums">
            {stats.data?.deleted ?? "—"}
          </div>
        </Card>
      </div>

      {showCreate ? (
        <Card className="mb-6">
          <h2 className="mb-4 text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Create customer
          </h2>
          <div className="grid gap-4 sm:grid-cols-3">
            <div>
              <Label htmlFor="code">Customer code</Label>
              <Input
                id="code"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder="CUST-001"
              />
            </div>
            <div>
              <Label htmlFor="legalName">Legal name</Label>
              <Input
                id="legalName"
                value={legalName}
                onChange={(e) => setLegalName(e.target.value)}
                placeholder="Acme Corporation"
              />
            </div>
            <div>
              <Label htmlFor="currency">Currency</Label>
              <Input
                id="currency"
                value={currency}
                onChange={(e) => setCurrency(e.target.value.toUpperCase())}
                placeholder="USD"
                maxLength={3}
              />
            </div>
          </div>
          <div className="mt-4 flex gap-2">
            <Button
              type="button"
              disabled={
                createMut.isPending || !code.trim() || !legalName.trim()
              }
              onClick={() => createMut.mutate()}
            >
              {createMut.isPending ? "Creating…" : "Create"}
            </Button>
            <Button
              type="button"
              variant="secondary"
              onClick={() => setShowCreate(false)}
            >
              Cancel
            </Button>
          </div>
        </Card>
      ) : null}

      <Card>
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="relative min-w-0 flex-1">
            <Label htmlFor="search">Search</Label>
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
              <Input
                id="search"
                className="pl-9"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Code or name…"
              />
            </div>
          </div>
          <div className="w-full sm:w-44">
            <Label htmlFor="status">Status</Label>
            <Select
              id="status"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
            >
              <option value="">All</option>
              <option value="ACTIVE">Active</option>
              <option value="BLOCKED">Blocked</option>
              <option value="DELETED">Deleted</option>
            </Select>
          </div>
          <label className="flex items-center gap-2 pb-2 text-sm text-zinc-600 dark:text-zinc-400">
            <input
              type="checkbox"
              checked={includeDeleted}
              onChange={(e) => setIncludeDeleted(e.target.checked)}
              className="rounded border-zinc-300"
            />
            Include deleted
          </label>
        </div>

        {customers.isLoading ? (
          <p className="py-8 text-center text-sm text-zinc-500">Loading…</p>
        ) : customers.isError ? (
          <p className="py-8 text-center text-sm text-rose-600">
            {(customers.error as Error).message}
          </p>
        ) : !customers.data?.length ? (
          <EmptyState
            title="No customers found"
            description="Create a customer to start issuing invoices for this tenant."
            action={
              <Button type="button" onClick={() => setShowCreate(true)}>
                <Plus className="h-4 w-4" />
                New customer
              </Button>
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-zinc-200 text-xs uppercase text-zinc-500 dark:border-zinc-800">
                <tr>
                  <th className="pb-2 pr-3 font-medium">Code</th>
                  <th className="pb-2 pr-3 font-medium">Name</th>
                  <th className="pb-2 pr-3 font-medium">Status</th>
                  <th className="pb-2 pr-3 font-medium">Currency</th>
                  <th className="pb-2 text-right font-medium">Credit limit</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-900">
                {customers.data.map((c) => (
                  <tr
                    key={c.id}
                    className="hover:bg-zinc-50 dark:hover:bg-zinc-900/50"
                  >
                    <td className="py-3 pr-3 font-medium">
                      <Link
                        href={`/customers/${c.id}`}
                        className="text-indigo-600 hover:underline dark:text-indigo-400"
                      >
                        {c.customerCode}
                      </Link>
                    </td>
                    <td className="py-3 pr-3">
                      <div className="font-medium text-zinc-900 dark:text-zinc-100">
                        {c.displayName || c.legalName}
                      </div>
                      {c.displayName ? (
                        <div className="text-xs text-zinc-500">
                          {c.legalName}
                        </div>
                      ) : null}
                    </td>
                    <td className="py-3 pr-3">
                      <StatusBadge status={c.status as CustomerStatus} />
                    </td>
                    <td className="py-3 pr-3 text-zinc-600 dark:text-zinc-400">
                      {c.currency}
                    </td>
                    <td className="py-3 text-right tabular-nums text-zinc-700 dark:text-zinc-300">
                      {c.creditLimit != null
                        ? formatMoney(c.creditLimit, c.currency)
                        : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}