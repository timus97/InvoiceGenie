"use client";

import Link from "next/link";
import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { TableSkeleton } from "@/components/ui/skeleton";
import { useTenant } from "@/components/tenant-provider";
import {
  calculateDiscount,
  getAgingBuckets,
  getAgingReport,
} from "@/lib/api/aging";
import { formatMoney } from "@/lib/money";
import { ApiError } from "@/lib/errors";

export default function AgingPage() {
  const { tenantId, ready } = useTenant();
  const [asOfDate, setAsOfDate] = useState(
    () => new Date().toISOString().slice(0, 10),
  );
  const [discAmount, setDiscAmount] = useState("1000");
  const [discCcy, setDiscCcy] = useState("USD");
  const [discDue, setDiscDue] = useState(
    () => new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10),
  );
  const [discToday, setDiscToday] = useState(
    () => new Date().toISOString().slice(0, 10),
  );

  const report = useQuery({
    queryKey: ["aging", tenantId, asOfDate],
    enabled: ready,
    queryFn: ({ signal }) => getAgingReport(tenantId, asOfDate, signal),
  });

  const buckets = useQuery({
    queryKey: ["aging-buckets", tenantId],
    enabled: ready,
    queryFn: ({ signal }) => getAgingBuckets(tenantId, signal),
  });

  const discMut = useMutation({
    mutationFn: () => {
      const amount = Number(discAmount);
      if (Number.isNaN(amount) || amount <= 0) throw new Error("Invalid amount");
      return calculateDiscount(tenantId, {
        amount,
        currencyCode: discCcy || "USD",
        dueDate: discDue || undefined,
        today: discToday || undefined,
      });
    },
    onError: (err: Error) =>
      toast.error(err instanceof ApiError ? err.message : err.message),
  });

  const r = report.data;

  return (
    <div>
      <PageHeader
        title="Aging"
        description="AR aging buckets and early-payment discount calculator."
      />

      <Card className="mb-6">
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <Label htmlFor="asof">As of date</Label>
            <Input
              id="asof"
              type="date"
              value={asOfDate}
              onChange={(e) => setAsOfDate(e.target.value)}
            />
          </div>
          <Button
            type="button"
            variant="secondary"
            onClick={() => report.refetch()}
          >
            Refresh
          </Button>
        </div>
      </Card>

      {report.isLoading ? (
        <Card>
          <TableSkeleton />
        </Card>
      ) : report.isError ? (
        <Card>
          <p className="text-sm text-rose-600">
            {(report.error as Error).message}
          </p>
        </Card>
      ) : !r ? (
        <EmptyState
          title="No aging data"
          description="Issue invoices to populate the aging report."
        />
      ) : (
        <>
          <div className="mb-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
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

          <Card className="mb-6">
            <h2 className="mb-3 text-sm font-semibold">Invoice details</h2>
            {!r.invoices?.length ? (
              <p className="text-sm text-zinc-500">No open invoices in aging.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-zinc-200 text-xs uppercase text-zinc-500 dark:border-zinc-800">
                    <tr>
                      <th className="pb-2 pr-3 font-medium">Invoice</th>
                      <th className="pb-2 pr-3 font-medium">Due</th>
                      <th className="pb-2 pr-3 font-medium">Days</th>
                      <th className="pb-2 pr-3 font-medium">Bucket</th>
                      <th className="pb-2 text-right font-medium">Amount due</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-zinc-100 dark:divide-zinc-900">
                    {r.invoices.map((inv) => (
                      <tr key={inv.invoiceId}>
                        <td className="py-2 pr-3">
                          <Link
                            href={`/invoices/${inv.invoiceId}`}
                            className="font-medium text-indigo-600 hover:underline dark:text-indigo-400"
                          >
                            {inv.invoiceNumber}
                          </Link>
                        </td>
                        <td className="py-2 pr-3 text-zinc-600 dark:text-zinc-400">
                          {inv.dueDate}
                        </td>
                        <td className="py-2 pr-3 tabular-nums">{inv.daysOverdue}</td>
                        <td className="py-2 pr-3 text-xs">{inv.bucket}</td>
                        <td className="py-2 text-right tabular-nums">
                          {formatMoney(inv.amountDue, r.currencyCode)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <h2 className="mb-3 text-sm font-semibold">Bucket labels</h2>
          {buckets.isLoading ? (
            <TableSkeleton rows={3} />
          ) : (
            <ul className="space-y-2 text-sm">
              {(buckets.data ?? []).map((b) => (
                <li
                  key={b.code}
                  className="flex justify-between border-b border-zinc-100 py-2 dark:border-zinc-900"
                >
                  <span>
                    <span className="font-mono text-xs text-zinc-500">
                      {b.code}
                    </span>{" "}
                    {b.label}
                  </span>
                  {b.earlyPaymentEligible ? (
                    <span className="text-xs text-emerald-600">
                      early-pay eligible
                    </span>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <h2 className="mb-3 text-sm font-semibold">
            Early payment discount
          </h2>
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <Label htmlFor="d-amt">Amount due</Label>
              <Input
                id="d-amt"
                type="number"
                value={discAmount}
                onChange={(e) => setDiscAmount(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="d-ccy">Currency</Label>
              <Input
                id="d-ccy"
                value={discCcy}
                onChange={(e) => setDiscCcy(e.target.value.toUpperCase())}
                maxLength={3}
              />
            </div>
            <div>
              <Label htmlFor="d-due">Due date</Label>
              <Input
                id="d-due"
                type="date"
                value={discDue}
                onChange={(e) => setDiscDue(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="d-today">Today</Label>
              <Input
                id="d-today"
                type="date"
                value={discToday}
                onChange={(e) => setDiscToday(e.target.value)}
              />
            </div>
          </div>
          <Button
            type="button"
            className="mt-3"
            disabled={discMut.isPending}
            onClick={() => discMut.mutate()}
          >
            {discMut.isPending ? "Calculating..." : "Calculate"}
          </Button>
          {discMut.data ? (
            <div className="mt-4 rounded-lg bg-zinc-50 p-3 text-sm dark:bg-zinc-900">
              <p>
                Eligible:{" "}
                <strong>{discMut.data.eligible ? "Yes" : "No"}</strong>
              </p>
              <p className="text-zinc-600 dark:text-zinc-400">
                {discMut.data.reason}
              </p>
              <p className="mt-2 tabular-nums">
                Original: {formatMoney(discMut.data.originalAmount, discCcy)}
              </p>
              <p className="tabular-nums">
                Discount ({discMut.data.discountRate}):{" "}
                {formatMoney(discMut.data.discountAmount, discCcy)}
              </p>
              <p className="font-medium tabular-nums">
                Pay: {formatMoney(discMut.data.discountedAmount, discCcy)}
              </p>
            </div>
          ) : null}
        </Card>
      </div>
    </div>
  );
}