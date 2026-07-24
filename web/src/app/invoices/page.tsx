"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Plus, Trash2 } from "lucide-react";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusBadge } from "@/components/ui/status-badge";
import { Button } from "@/components/ui/button";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import { TableSkeleton } from "@/components/ui/skeleton";
import { useTenant } from "@/components/tenant-provider";
import { listCustomers } from "@/lib/api/customers";
import { createInvoice, listInvoices } from "@/lib/api/invoices";
import { formatMoney } from "@/lib/money";
import { ApiError } from "@/lib/errors";
import type { InvoiceStatus } from "@/types/ar";

const STATUSES: InvoiceStatus[] = [
  "DRAFT",
  "ISSUED",
  "PARTIALLY_PAID",
  "PAID",
  "OVERDUE",
  "WRITTEN_OFF",
];

type LineDraft = { description: string; amount: string };

export default function InvoicesPage() {
  const { tenantId, ready } = useTenant();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState("");
  const [cursor, setCursor] = useState<string | undefined>();
  const [cursorStack, setCursorStack] = useState<(string | undefined)[]>([
    undefined,
  ]);
  const [showCreate, setShowCreate] = useState(false);
  const [invoiceNumber, setInvoiceNumber] = useState("");
  const [customerId, setCustomerId] = useState("");
  const [currencyCode, setCurrencyCode] = useState("USD");
  const [dueDate, setDueDate] = useState("");
  const [lines, setLines] = useState<LineDraft[]>([
    { description: "", amount: "" },
  ]);
  const [issueImmediately, setIssueImmediately] = useState(true);

  const listKey = useMemo(
    () => ["invoices", tenantId, status, cursor] as const,
    [tenantId, status, cursor],
  );

  const invoices = useQuery({
    queryKey: listKey,
    enabled: ready,
    queryFn: ({ signal }) =>
      listInvoices(tenantId, {
        status: status || undefined,
        cursor,
        limit: 20,
        signal,
      }),
  });

  const customers = useQuery({
    queryKey: ["customers", tenantId, "ACTIVE"],
    enabled: ready && showCreate,
    queryFn: ({ signal }) =>
      listCustomers(tenantId, { status: "ACTIVE", signal }),
  });

  const createMut = useMutation({
    mutationFn: () => {
      const parsedLines = lines
        .map((l, i) => ({
          sequence: i + 1,
          description: l.description.trim(),
          amount: Number(l.amount),
        }))
        .filter((l) => l.description && !Number.isNaN(l.amount) && l.amount > 0);
      if (!invoiceNumber.trim()) throw new Error("Invoice number required");
      if (!customerId) throw new Error("Customer required");
      if (!parsedLines.length) throw new Error("Add at least one line");
      return createInvoice(tenantId, {
        invoiceNumber: invoiceNumber.trim(),
        customerId,
        currencyCode: currencyCode.trim() || "USD",
        dueDate: dueDate || undefined,
        lines: parsedLines,
        issueImmediately,
      });
    },
    onSuccess: (created) => {
      toast.success("Invoice created");
      void queryClient.invalidateQueries({ queryKey: ["invoices", tenantId] });
      void queryClient.invalidateQueries({
        queryKey: ["invoices-recent", tenantId],
      });
      router.push(`/invoices/${created.id}`);
    },
    onError: (err: Error) =>
      toast.error(err instanceof ApiError ? err.message : err.message),
  });

  const goNext = () => {
    const next = invoices.data?.nextCursor;
    if (!next) return;
    setCursorStack((s) => [...s, next]);
    setCursor(next);
  };

  const goPrev = () => {
    if (cursorStack.length <= 1) return;
    const next = cursorStack.slice(0, -1);
    setCursorStack(next);
    setCursor(next[next.length - 1]);
  };

  return (
    <div>
      <PageHeader
        title="Invoices"
        description="Create, issue, and manage AR invoices with lifecycle actions."
        actions={
          <Button type="button" onClick={() => setShowCreate((v) => !v)}>
            <Plus className="h-4 w-4" />
            New invoice
          </Button>
        }
      />

      {showCreate ? (
        <Card className="mb-6">
          <h2 className="mb-4 text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Create invoice
          </h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <Label htmlFor="inv-num">Invoice number</Label>
              <Input
                id="inv-num"
                value={invoiceNumber}
                onChange={(e) => setInvoiceNumber(e.target.value)}
                placeholder="INV-001"
              />
            </div>
            <div>
              <Label htmlFor="inv-cust">Customer</Label>
              <Select
                id="inv-cust"
                value={customerId}
                onChange={(e) => {
                  setCustomerId(e.target.value);
                  const c = customers.data?.find((x) => x.id === e.target.value);
                  if (c?.currency) setCurrencyCode(c.currency);
                }}
              >
                <option value="">Select…</option>
                {(customers.data ?? []).map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.customerCode} — {c.displayName || c.legalName}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <Label htmlFor="inv-ccy">Currency</Label>
              <Input
                id="inv-ccy"
                value={currencyCode}
                onChange={(e) => setCurrencyCode(e.target.value.toUpperCase())}
                maxLength={3}
              />
            </div>
            <div>
              <Label htmlFor="inv-due">Due date</Label>
              <Input
                id="inv-due"
                type="date"
                value={dueDate}
                onChange={(e) => setDueDate(e.target.value)}
              />
            </div>
            <div className="flex items-center gap-2">
              <input
                id="issue-now"
                type="checkbox"
                checked={issueImmediately}
                onChange={(e) => setIssueImmediately(e.target.checked)}
                className="h-4 w-4 rounded border-zinc-300"
              />
              <Label htmlFor="issue-now">Issue immediately (uncheck to save as DRAFT)</Label>
            </div>
          </div>

          <div className="mt-4">
            <div className="mb-2 flex items-center justify-between">
              <Label>Line items</Label>
              <Button
                type="button"
                variant="secondary"
                onClick={() =>
                  setLines((ls) => [...ls, { description: "", amount: "" }])
                }
              >
                <Plus className="h-4 w-4" />
                Add line
              </Button>
            </div>
            <div className="space-y-3">
              {lines.map((line, idx) => (
                <div
                  key={idx}
                  className="rounded-lg border border-zinc-200 p-3 dark:border-zinc-800"
                >
                  <div className="mb-2 flex items-center justify-between gap-2">
                    <span className="text-xs font-medium uppercase tracking-wide text-zinc-500">
                      Line {idx + 1}
                    </span>
                    <Button
                      type="button"
                      variant="ghost"
                      disabled={lines.length <= 1}
                      onClick={() =>
                        setLines((ls) => ls.filter((_, i) => i !== idx))
                      }
                      aria-label="Remove line"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                  <div className="grid gap-3 lg:grid-cols-[1fr_10rem]">
                    <div>
                      <Label htmlFor={`line-desc-${idx}`}>Description</Label>
                      <Textarea
                        id={`line-desc-${idx}`}
                        className="min-h-[5.5rem]"
                        rows={3}
                        placeholder="Service or product description (full details)"
                        value={line.description}
                        onChange={(e) =>
                          setLines((ls) =>
                            ls.map((l, i) =>
                              i === idx
                                ? { ...l, description: e.target.value }
                                : l,
                            ),
                          )
                        }
                      />
                    </div>
                    <div>
                      <Label htmlFor={`line-amt-${idx}`}>Amount</Label>
                      <Input
                        id={`line-amt-${idx}`}
                        type="number"
                        min="0"
                        step="0.01"
                        placeholder="0.00"
                        value={line.amount}
                        onChange={(e) =>
                          setLines((ls) =>
                            ls.map((l, i) =>
                              i === idx ? { ...l, amount: e.target.value } : l,
                            ),
                          )
                        }
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="mt-4 flex gap-2">
            <Button
              type="button"
              disabled={createMut.isPending}
              onClick={() => createMut.mutate()}
            >
              {createMut.isPending ? "Creating…" : "Create & issue"}
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
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div className="w-full sm:w-52">
            <Label htmlFor="inv-status">Status</Label>
            <Select
              id="inv-status"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setCursor(undefined);
                setCursorStack([undefined]);
              }}
            >
              <option value="">All</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s.replaceAll("_", " ")}
                </option>
              ))}
            </Select>
          </div>
          <p className="text-xs text-zinc-500">
            {invoices.data?.total != null
              ? `${invoices.data.total} total`
              : null}
          </p>
        </div>

        {invoices.isLoading ? (
          <TableSkeleton />
        ) : invoices.isError ? (
          <p className="py-8 text-center text-sm text-rose-600">
            {(invoices.error as Error).message}
          </p>
        ) : !invoices.data?.items?.length ? (
          <EmptyState
            title="No invoices"
            description="Create an invoice for an active customer to begin the AR lifecycle."
            action={
              <Button type="button" onClick={() => setShowCreate(true)}>
                <Plus className="h-4 w-4" />
                New invoice
              </Button>
            }
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-zinc-200 text-xs uppercase text-zinc-500 dark:border-zinc-800">
                  <tr>
                    <th className="pb-2 pr-3 font-medium">Number</th>
                    <th className="pb-2 pr-3 font-medium">Customer</th>
                    <th className="pb-2 pr-3 font-medium">Status</th>
                    <th className="pb-2 pr-3 font-medium">Due</th>
                    <th className="pb-2 text-right font-medium">Total</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-100 dark:divide-zinc-900">
                  {invoices.data.items.map((inv) => (
                    <tr
                      key={inv.id}
                      className="hover:bg-zinc-50 dark:hover:bg-zinc-900/50"
                    >
                      <td className="py-3 pr-3 font-medium">
                        <Link
                          href={`/invoices/${inv.id}`}
                          className="text-indigo-600 hover:underline dark:text-indigo-400"
                        >
                          {inv.invoiceNumber}
                        </Link>
                      </td>
                      <td className="py-3 pr-3 font-mono text-xs text-zinc-600 dark:text-zinc-400">
                        {inv.customerRef || inv.customerId?.slice(0, 8)}
                      </td>
                      <td className="py-3 pr-3">
                        <StatusBadge status={String(inv.status)} />
                      </td>
                      <td className="py-3 pr-3 text-zinc-600 dark:text-zinc-400">
                        {inv.dueDate ?? "—"}
                      </td>
                      <td className="py-3 text-right tabular-nums">
                        {formatMoney(inv.total, inv.currencyCode)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="mt-4 flex justify-between">
              <Button
                type="button"
                variant="secondary"
                disabled={cursorStack.length <= 1}
                onClick={goPrev}
              >
                Previous
              </Button>
              <Button
                type="button"
                variant="secondary"
                disabled={!invoices.data.nextCursor}
                onClick={goNext}
              >
                Next
              </Button>
            </div>
          </>
        )}
      </Card>
    </div>
  );
}
