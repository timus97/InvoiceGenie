"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { TableSkeleton } from "@/components/ui/skeleton";
import { useTenant } from "@/components/tenant-provider";
import { useAuth } from "@/components/auth-provider";
import { apiFetch, checkBackendHealth } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import { getAgingReport } from "@/lib/api/aging";
import type { CustomerStatsDto, InvoicePageDto } from "@/types/ar";
import { StatusBadge } from "@/components/ui/status-badge";
import { formatMoney } from "@/lib/money";

export default function DashboardPage() {
  const { tenantId, ready: tenantReady } = useTenant();
  const { ready: authReady, session } = useAuth();
  // Wait for auth session hydration so tenant setTenantId does not race queries
  const ready = tenantReady && authReady && !!session;

  const health = useQuery({
    queryKey: ["backend-health"],
    queryFn: checkBackendHealth,
    refetchInterval: 30000,
  });

  const stats = useQuery({
    queryKey: ["customer-stats", tenantId],
    enabled: ready,
    queryFn: ({ signal }) =>
      apiFetch<CustomerStatsDto>(apiPaths.customerStats, { tenantId, signal }),
  });

  const invoices = useQuery({
    queryKey: ["invoices-recent", tenantId],
    enabled: ready,
    queryFn: ({ signal }) =>
      apiFetch<InvoicePageDto>(apiPaths.invoices, {
        tenantId,
        signal,
        query: { limit: 5 },
      }),
  });

  // Distinct key from Aging page report (avoids cache collisions / abort races)
  const aging = useQuery({
    queryKey: ["aging", "summary", tenantId],
    enabled: ready,
    queryFn: ({ signal }) => getAgingReport(tenantId, undefined, signal),
  });

  const r = aging.data;

  return (
    <div>
      <PageHeader
        title="Dashboard"
        description="Multi-tenant AR overview. Start the Quarkus API (default :8082, or set BACKEND_URL) so this page can load live data."
        actions={
          <span
            className={`rounded-full px-2.5 py-1 text-xs font-medium ${
              health.data?.ok
                ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-200"
                : "bg-rose-100 text-rose-800 dark:bg-rose-900/40 dark:text-rose-200"
            }`}
          >
            API {health.isLoading ? "..." : health.data?.ok ? "online" : "offline"}
          </span>
        }
      />

      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <div className="text-xs font-medium uppercase tracking-wide text-zinc-500">
            Active customers
          </div>
          <div className="mt-2 text-3xl font-semibold tabular-nums">
            {stats.isLoading ? "..." : (stats.data?.active ?? "-")}
          </div>
          {stats.isError ? (
            <p className="mt-2 text-xs text-rose-600">
              {(stats.error as Error).message}
            </p>
          ) : null}
        </Card>
        <Card>
          <div className="text-xs font-medium uppercase tracking-wide text-zinc-500">
            Blocked
          </div>
          <div className="mt-2 text-3xl font-semibold tabular-nums">
            {stats.isLoading ? "..." : (stats.data?.blocked ?? "-")}
          </div>
        </Card>
        <Card>
          <div className="text-xs font-medium uppercase tracking-wide text-zinc-500">
            AR aging total
          </div>
          <div className="mt-2 text-3xl font-semibold tabular-nums">
            {aging.isLoading
              ? "..."
              : r
                ? formatMoney(r.grandTotal, r.currencyCode)
                : "-"}
          </div>
          <Link
            href="/aging"
            className="mt-2 inline-block text-xs text-indigo-600 hover:underline"
          >
            Open aging report
          </Link>
        </Card>
        <Card>
          <div className="text-xs font-medium uppercase tracking-wide text-zinc-500">
            Open invoices
          </div>
          <div className="mt-2 text-3xl font-semibold tabular-nums">
            {invoices.isLoading ? "..." : (invoices.data?.total ?? invoices.data?.items?.length ?? "-")}
          </div>
          {r ? (
            <p className="mt-1 text-xs text-zinc-500">
              Aging open count: {r.totalCount}
            </p>
          ) : null}
        </Card>
      </div>

      <div className="mb-6">
        <div className="mb-2 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Aging buckets
          </h2>
          <Link
            href="/aging"
            className="text-xs font-medium text-indigo-600 hover:underline dark:text-indigo-400"
          >
            Full report
          </Link>
        </div>
        {aging.isLoading ? (
          <Card>
            <TableSkeleton rows={1} />
          </Card>
        ) : aging.isError ? (
          <Card>
            <p className="text-sm text-rose-600">
              {(aging.error as Error).message}
            </p>
          </Card>
        ) : r ? (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            {[
              { label: "0-30", total: r.total0To30, count: r.count0To30 },
              { label: "31-60", total: r.total31To60, count: r.count31To60 },
              { label: "61-90", total: r.total61To90, count: r.count61To90 },
              { label: "90+", total: r.total90Plus, count: r.count90Plus },
              { label: "Grand total", total: r.grandTotal, count: r.totalCount },
            ].map((b) => (
              <Card key={b.label}>
                <div className="text-xs font-medium uppercase tracking-wide text-zinc-500">
                  {b.label}
                </div>
                <div className="mt-1 text-xl font-semibold tabular-nums">
                  {formatMoney(b.total, r.currencyCode)}
                </div>
                <div className="mt-1 text-xs text-zinc-500">{b.count} inv</div>
              </Card>
            ))}
          </div>
        ) : (
          <Card>
            <p className="text-sm text-zinc-500">No aging data yet.</p>
          </Card>
        )}
      </div>

      <Card>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Recent invoices
          </h2>
          <Link
            href="/invoices"
            className="text-sm font-medium text-indigo-600 hover:underline dark:text-indigo-400"
          >
            View all
          </Link>
        </div>
        {invoices.isLoading ? (
          <TableSkeleton rows={3} />
        ) : invoices.isError ? (
          <p className="text-sm text-rose-600">
            {(invoices.error as Error).message}
          </p>
        ) : !invoices.data?.items?.length ? (
          <p className="text-sm text-zinc-500">
            No invoices yet.{" "}
            <Link href="/customers" className="text-indigo-600 hover:underline">
              Create a customer
            </Link>{" "}
            then issue an invoice.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-zinc-200 text-xs uppercase text-zinc-500 dark:border-zinc-800">
                <tr>
                  <th className="pb-2 font-medium">Number</th>
                  <th className="pb-2 font-medium">Status</th>
                  <th className="pb-2 font-medium">Due</th>
                  <th className="pb-2 text-right font-medium">Total</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-900">
                {invoices.data.items.map((inv) => (
                  <tr key={inv.id}>
                    <td className="py-2.5 font-medium">
                      <Link
                        href={`/invoices/${inv.id}`}
                        className="text-indigo-600 hover:underline dark:text-indigo-400"
                      >
                        {inv.invoiceNumber}
                      </Link>
                    </td>
                    <td className="py-2.5">
                      <StatusBadge status={String(inv.status)} />
                    </td>
                    <td className="py-2.5 text-zinc-600 dark:text-zinc-400">
                      {inv.dueDate ?? "-"}
                    </td>
                    <td className="py-2.5 text-right tabular-nums">
                      {formatMoney(inv.total, inv.currencyCode)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {[
          { href: "/customers", label: "Customers", hint: "Master data" },
          { href: "/invoices", label: "Invoices", hint: "Issue and lifecycle" },
          { href: "/payments", label: "Payments", hint: "Allocate FIFO/manual" },
          { href: "/cheques", label: "Cheques", hint: "Deposit / clear / bounce" },
          { href: "/aging", label: "Aging", hint: "Buckets and discount" },
          { href: "/credit-notes", label: "Credit notes", hint: "Early-pay discounts" },
          { href: "/ledger", label: "Ledger", hint: "Balances and lookup" },
          { href: "/settings", label: "Settings", hint: "Tenant UUID" },
        ].map((item) => (
          <Link
            key={item.href}
            href={item.href}
            className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm transition hover:border-indigo-300 hover:shadow dark:border-zinc-800 dark:bg-zinc-950 dark:hover:border-indigo-700"
          >
            <div className="font-medium text-zinc-900 dark:text-zinc-100">
              {item.label}
            </div>
            <div className="mt-1 text-xs text-zinc-500">{item.hint}</div>
          </Link>
        ))}
      </div>
    </div>
  );
}