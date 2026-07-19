"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft } from "lucide-react";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { TableSkeleton } from "@/components/ui/skeleton";
import { useTenant } from "@/components/tenant-provider";
import {
  applyInvoicePayment,
  getInvoice,
  issueInvoice,
  markInvoiceOverdue,
  updateInvoiceDueDate,
  writeOffInvoice,
} from "@/lib/api/invoices";
import { getInvoiceAllocations } from "@/lib/api/payments";
import { getLedgerByReference } from "@/lib/api/ledger";
import { formatMoney } from "@/lib/money";
import { ApiError } from "@/lib/errors";

export default function InvoiceDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const { tenantId, ready } = useTenant();
  const queryClient = useQueryClient();
  const [writeOffReason, setWriteOffReason] = useState("");
  const [showWriteOff, setShowWriteOff] = useState(false);
  const [partialAmount, setPartialAmount] = useState("");
  const [dueDate, setDueDate] = useState("");

  const invQ = useQuery({
    queryKey: ["invoice", tenantId, id],
    enabled: ready && !!id,
    queryFn: ({ signal }) => getInvoice(tenantId, id, signal),
  });

  const allocQ = useQuery({
    queryKey: ["invoice-allocations", tenantId, id],
    enabled: ready && !!id,
    queryFn: ({ signal }) => getInvoiceAllocations(tenantId, id, signal),
  });

  const ledgerQ = useQuery({
    queryKey: ["ledger-ref", tenantId, "INVOICE", id],
    enabled: ready && !!id,
    queryFn: ({ signal }) =>
      getLedgerByReference(tenantId, "INVOICE", id, signal),
  });

  const inv = invQ.data;

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["invoice", tenantId, id] });
    void queryClient.invalidateQueries({ queryKey: ["invoices", tenantId] });
    void queryClient.invalidateQueries({
      queryKey: ["invoices-recent", tenantId],
    });
    void queryClient.invalidateQueries({
      queryKey: ["invoice-allocations", tenantId, id],
    });
    void queryClient.invalidateQueries({
      queryKey: ["ledger-ref", tenantId, "INVOICE", id],
    });
  };

  const onErr = (err: Error) =>
    toast.error(err instanceof ApiError ? err.message : err.message);

  const issueMut = useMutation({
    mutationFn: () => issueInvoice(tenantId, id),
    onSuccess: () => {
      toast.success("Invoice issued");
      invalidate();
    },
    onError: onErr,
  });

  const overdueMut = useMutation({
    mutationFn: () => markInvoiceOverdue(tenantId, id),
    onSuccess: () => {
      toast.success("Marked overdue");
      invalidate();
    },
    onError: onErr,
  });

  const writeOffMut = useMutation({
    mutationFn: () => writeOffInvoice(tenantId, id, writeOffReason.trim()),
    onSuccess: () => {
      toast.success("Written off");
      setShowWriteOff(false);
      setWriteOffReason("");
      invalidate();
    },
    onError: onErr,
  });

  const payFullMut = useMutation({
    mutationFn: () => applyInvoicePayment(tenantId, id, { fullyPaid: true }),
    onSuccess: () => {
      toast.success("Payment applied (full)");
      invalidate();
    },
    onError: onErr,
  });

  const payPartialMut = useMutation({
    mutationFn: () => {
      const amount = Number(partialAmount);
      if (Number.isNaN(amount) || amount <= 0) throw new Error("Invalid amount");
      return applyInvoicePayment(tenantId, id, {
        fullyPaid: false,
        amount,
      });
    },
    onSuccess: () => {
      toast.success("Partial payment applied");
      setPartialAmount("");
      invalidate();
    },
    onError: onErr,
  });

  const dueMut = useMutation({
    mutationFn: () => {
      if (!dueDate) throw new Error("Due date required");
      return updateInvoiceDueDate(tenantId, id, dueDate);
    },
    onSuccess: () => {
      toast.success("Due date updated");
      invalidate();
    },
    onError: onErr,
  });

  if (invQ.isLoading) {
    return (
      <div>
        <PageHeader title="Invoice" description="Loading…" />
        <Card>
          <TableSkeleton rows={4} />
        </Card>
      </div>
    );
  }

  if (invQ.isError || !inv) {
    return (
      <div>
        <PageHeader title="Invoice" description="Not found" />
        <Card>
          <p className="text-sm text-rose-600">
            {(invQ.error as Error)?.message ?? "Invoice not found"}
          </p>
          <Link
            href="/invoices"
            className="mt-4 inline-flex items-center gap-1 text-sm text-indigo-600 hover:underline"
          >
            <ArrowLeft className="h-4 w-4" /> Back to invoices
          </Link>
        </Card>
      </div>
    );
  }

  const status = String(inv.status);
  const canPay =
    status === "ISSUED" || status === "PARTIALLY_PAID" || status === "OVERDUE";

  return (
    <div>
      <PageHeader
        title={inv.invoiceNumber}
        description={`Invoice ${id}`}
        actions={
          <Link
            href="/invoices"
            className="inline-flex items-center gap-1 text-sm text-zinc-600 hover:text-zinc-900 dark:text-zinc-400"
          >
            <ArrowLeft className="h-4 w-4" /> All invoices
          </Link>
        }
      />

      <div className="mb-6 grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div className="text-xs uppercase text-zinc-500">Status</div>
              <div className="mt-1">
                <StatusBadge status={status} />
              </div>
            </div>
            <div className="text-right">
              <div className="text-xs uppercase text-zinc-500">Total</div>
              <div className="mt-1 text-2xl font-semibold tabular-nums">
                {formatMoney(inv.total, inv.currencyCode)}
              </div>
            </div>
          </div>
          <dl className="mt-6 grid gap-3 sm:grid-cols-2 text-sm">
            <div>
              <dt className="text-zinc-500">Customer</dt>
              <dd className="font-mono text-xs">
                <Link
                  href={`/customers/${inv.customerId}`}
                  className="text-indigo-600 hover:underline"
                >
                  {inv.customerId}
                </Link>
              </dd>
            </div>
            <div>
              <dt className="text-zinc-500">Currency</dt>
              <dd>{inv.currencyCode}</dd>
            </div>
            <div>
              <dt className="text-zinc-500">Issue date</dt>
              <dd>{inv.issueDate ?? "—"}</dd>
            </div>
            <div>
              <dt className="text-zinc-500">Due date</dt>
              <dd>{inv.dueDate ?? "—"}</dd>
            </div>
            <div>
              <dt className="text-zinc-500">Version</dt>
              <dd>{inv.version ?? "—"}</dd>
            </div>
          </dl>

          {inv.lines?.length ? (
            <div className="mt-6 overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-zinc-200 text-xs uppercase text-zinc-500 dark:border-zinc-800">
                  <tr>
                    <th className="pb-2 pr-3 font-medium">#</th>
                    <th className="pb-2 pr-3 font-medium">Description</th>
                    <th className="pb-2 text-right font-medium">Amount</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-100 dark:divide-zinc-900">
                  {inv.lines.map((l, i) => (
                    <tr key={i}>
                      <td className="py-2 pr-3 text-zinc-500">
                        {l.sequence ?? i + 1}
                      </td>
                      <td className="py-2 pr-3">{l.description}</td>
                      <td className="py-2 text-right tabular-nums">
                        {formatMoney(l.amount, inv.currencyCode)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </Card>

        <Card>
          <h2 className="mb-3 text-sm font-semibold">Lifecycle actions</h2>
          <div className="flex flex-col gap-2">
            {status === "DRAFT" ? (
              <Button
                type="button"
                disabled={issueMut.isPending}
                onClick={() => issueMut.mutate()}
              >
                Issue
              </Button>
            ) : null}
            {status === "ISSUED" || status === "PARTIALLY_PAID" ? (
              <Button
                type="button"
                variant="secondary"
                disabled={overdueMut.isPending}
                onClick={() => overdueMut.mutate()}
              >
                Mark overdue
              </Button>
            ) : null}
            {canPay ? (
              <>
                <Button
                  type="button"
                  disabled={payFullMut.isPending}
                  onClick={() => payFullMut.mutate()}
                >
                  Pay remaining (full)
                </Button>
                <div className="flex gap-2">
                  <Input
                    type="number"
                    min="0"
                    step="0.01"
                    placeholder="Partial amount"
                    value={partialAmount}
                    onChange={(e) => setPartialAmount(e.target.value)}
                  />
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={payPartialMut.isPending || !partialAmount}
                    onClick={() => payPartialMut.mutate()}
                  >
                    Pay
                  </Button>
                </div>
              </>
            ) : null}
            {status === "OVERDUE" ? (
              <Button
                type="button"
                variant="danger"
                onClick={() => setShowWriteOff((v) => !v)}
              >
                Write off…
              </Button>
            ) : null}
            {status === "DRAFT" ? (
              <div className="mt-2 space-y-2 border-t border-zinc-200 pt-3 dark:border-zinc-800">
                <Label htmlFor="due-edit">Update due date</Label>
                <Input
                  id="due-edit"
                  type="date"
                  value={dueDate || inv.dueDate || ""}
                  onChange={(e) => setDueDate(e.target.value)}
                />
                <Button
                  type="button"
                  variant="secondary"
                  disabled={dueMut.isPending}
                  onClick={() => dueMut.mutate()}
                >
                  Save due date
                </Button>
              </div>
            ) : null}
          </div>

          {showWriteOff ? (
            <div className="mt-4 space-y-2 border-t border-zinc-200 pt-3 dark:border-zinc-800">
              <Label htmlFor="wo-reason">Write-off reason</Label>
              <Input
                id="wo-reason"
                value={writeOffReason}
                onChange={(e) => setWriteOffReason(e.target.value)}
                placeholder="Uncollectible…"
              />
              <Button
                type="button"
                variant="danger"
                disabled={writeOffMut.isPending || !writeOffReason.trim()}
                onClick={() => writeOffMut.mutate()}
              >
                Confirm write-off
              </Button>
            </div>
          ) : null}

          <div className="mt-4 border-t border-zinc-200 pt-3 text-xs text-zinc-500 dark:border-zinc-800">
            <Link
              href={`/payments?invoiceId=${id}`}
              className="text-indigo-600 hover:underline"
            >
              Allocate via payments →
            </Link>
            <br />
            <Link
              href={`/ledger?type=INVOICE&id=${id}`}
              className="text-indigo-600 hover:underline"
            >
              View ledger entries →
            </Link>
          </div>
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <h2 className="mb-3 text-sm font-semibold">Allocations</h2>
          {allocQ.isLoading ? (
            <TableSkeleton rows={2} />
          ) : !allocQ.data?.allocations?.length ? (
            <p className="text-sm text-zinc-500">No allocations yet.</p>
          ) : (
            <ul className="space-y-2 text-sm">
              {allocQ.data.allocations.map((a) => (
                <li
                  key={a.allocationId}
                  className="flex justify-between border-b border-zinc-100 py-2 dark:border-zinc-900"
                >
                  <span className="font-mono text-xs">
                    {a.allocationId.slice(0, 8)}…
                  </span>
                  <span className="tabular-nums">
                    {formatMoney(a.amount, inv.currencyCode)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card>
          <h2 className="mb-3 text-sm font-semibold">Ledger (by reference)</h2>
          {ledgerQ.isLoading ? (
            <TableSkeleton rows={2} />
          ) : !ledgerQ.data?.length ? (
            <p className="text-sm text-zinc-500">No ledger entries.</p>
          ) : (
            <ul className="space-y-2 text-sm">
              {ledgerQ.data.map((e) => (
                <li
                  key={e.id}
                  className="flex justify-between gap-2 border-b border-zinc-100 py-2 dark:border-zinc-900"
                >
                  <span>
                    {e.account}{" "}
                    <span className="text-xs text-zinc-500">{e.entryType}</span>
                  </span>
                  <span className="tabular-nums">
                    {formatMoney(e.amount, inv.currencyCode)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  );
}