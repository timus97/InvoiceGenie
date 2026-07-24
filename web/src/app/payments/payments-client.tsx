"use client";

import { useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label, Select } from "@/components/ui/input";
import { EmptyState } from "@/components/ui/empty-state";
import { TableSkeleton } from "@/components/ui/skeleton";
import { useTenant } from "@/components/tenant-provider";
import { listCustomers } from "@/lib/api/customers";
import { listInvoices } from "@/lib/api/invoices";
import {
  allocateFifo,
  allocateManual,
  createPayment,
  getPaymentAllocations,
  listPayments,
  reversePayment,
} from "@/lib/api/payments";
import { formatMoney } from "@/lib/money";
import { ApiError } from "@/lib/errors";
import type { PaymentMethod } from "@/types/ar";

const METHODS: PaymentMethod[] = [
  "BANK_TRANSFER",
  "CARD",
  "CASH",
  "CHECK",
  "OTHER",
];

export default function PaymentsClient() {
  const { tenantId, ready } = useTenant();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();

  const [paymentNumber, setPaymentNumber] = useState(
    () => `PAY-${Date.now().toString(36).toUpperCase()}`,
  );
  const [customerId, setCustomerId] = useState("");
  const [amount, setAmount] = useState("");
  const [currencyCode, setCurrencyCode] = useState("USD");
  const [paymentDate, setPaymentDate] = useState(
    () => new Date().toISOString().slice(0, 10),
  );
  const [method, setMethod] = useState<PaymentMethod>("BANK_TRANSFER");
  const [reference, setReference] = useState("");
  const [notes, setNotes] = useState("");
  const [paymentId, setPaymentId] = useState(
    searchParams.get("paymentId") ?? "",
  );
  const [manualRows, setManualRows] = useState<
    { invoiceId: string; amount: string }[]
  >([{ invoiceId: searchParams.get("invoiceId") ?? "", amount: "" }]);

  const customers = useQuery({
    queryKey: ["customers", tenantId, "ACTIVE"],
    enabled: ready,
    queryFn: ({ signal }) =>
      listCustomers(tenantId, { status: "ACTIVE", signal }),
  });

  const paymentsList = useQuery({
    queryKey: ["payments", tenantId],
    enabled: ready,
    queryFn: ({ signal }) => listPayments(tenantId, { limit: 50, signal }),
  });

  const openInvoices = useQuery({
    queryKey: ["invoices", tenantId, "open-for-alloc", currencyCode],
    enabled: ready && !!paymentId,
    queryFn: async ({ signal }) => {
      const pages = await Promise.all(
        (["ISSUED", "PARTIALLY_PAID", "OVERDUE"] as const).map((status) =>
          listInvoices(tenantId, { status, limit: 50, signal }),
        ),
      );
      // Same-currency only (STORY-010)
      const payCcy = (currencyCode || "USD").toUpperCase();
      return pages
        .flatMap((p) => p.items)
        .filter(
          (inv) =>
            !inv.currencyCode ||
            inv.currencyCode.toUpperCase() === payCcy,
        );
    },
  });

  const allocQ = useQuery({
    queryKey: ["payment-allocations", tenantId, paymentId],
    enabled: ready && !!paymentId.trim(),
    queryFn: ({ signal }) =>
      getPaymentAllocations(tenantId, paymentId.trim(), signal),
  });

  const createMut = useMutation({
    mutationFn: () => {
      const n = Number(amount);
      if (!paymentNumber.trim()) throw new Error("Payment number required");
      if (!customerId) throw new Error("Customer required");
      if (Number.isNaN(n) || n <= 0) throw new Error("Valid amount required");
      return createPayment(tenantId, {
        paymentNumber: paymentNumber.trim(),
        customerId,
        amount: n,
        currencyCode: currencyCode || "USD",
        paymentDate: paymentDate || undefined,
        method,
        reference: reference || undefined,
        notes: notes || undefined,
      });
    },
    onSuccess: (created) => {
      toast.success(`Payment ${created.paymentNumber} recorded`);
      setPaymentId(created.id);
      void queryClient.invalidateQueries({
        queryKey: ["payment-allocations", tenantId],
      });
      void queryClient.invalidateQueries({ queryKey: ["payments", tenantId] });
    },
    onError: (err: Error) =>
      toast.error(err instanceof ApiError ? err.message : err.message),
  });

  const fifoMut = useMutation({
    mutationFn: () => allocateFifo(tenantId, paymentId.trim()),
    onSuccess: (r) => {
      toast.success(
        r.fullyAllocated
          ? "Fully allocated (FIFO)"
          : `Allocated ${r.totalAllocated}; remaining ${r.remainingUnallocated}`,
      );
      if (r.errors?.length) toast.warning(r.errors.join("; "));
      void queryClient.invalidateQueries({
        queryKey: ["payment-allocations", tenantId, paymentId.trim()],
      });
      void queryClient.invalidateQueries({ queryKey: ["invoices", tenantId] });
    },
    onError: (err: Error) =>
      toast.error(err instanceof ApiError ? err.message : err.message),
  });

  const manualMut = useMutation({
    mutationFn: () => {
      const allocations = manualRows
        .map((r) => ({
          invoiceId: r.invoiceId.trim(),
          amount: Number(r.amount),
        }))
        .filter((r) => r.invoiceId && !Number.isNaN(r.amount) && r.amount > 0);
      if (!allocations.length) throw new Error("Add at least one allocation");
      return allocateManual(tenantId, paymentId.trim(), allocations);
    },
    onSuccess: (r) => {
      toast.success(
        r.fullyAllocated
          ? "Fully allocated"
          : `Allocated ${r.totalAllocated}; remaining ${r.remainingUnallocated}`,
      );
      if (r.errors?.length) toast.warning(r.errors.join("; "));
      void queryClient.invalidateQueries({
        queryKey: ["payment-allocations", tenantId, paymentId.trim()],
      });
      void queryClient.invalidateQueries({ queryKey: ["invoices", tenantId] });
    },
    onError: (err: Error) =>
      toast.error(err instanceof ApiError ? err.message : err.message),
  });

  const reverseMut = useMutation({
    mutationFn: () => reversePayment(tenantId, paymentId.trim(), "Reversed via UI"),
    onSuccess: (r) => {
      toast.success(r.message || "Payment reversed");
      void queryClient.invalidateQueries({ queryKey: ["payments", tenantId] });
      void queryClient.invalidateQueries({
        queryKey: ["payment-allocations", tenantId, paymentId.trim()],
      });
      void queryClient.invalidateQueries({ queryKey: ["invoices", tenantId] });
    },
    onError: (err: Error) =>
      toast.error(err instanceof ApiError ? err.message : err.message),
  });

  const invoiceOptions = useMemo(() => openInvoices.data ?? [], [openInvoices.data]);

  return (
    <div>
      <PageHeader
        title="Payments"
        description="Record customer payments and allocate FIFO or manually across open invoices."
      />

      <Card className="mb-6">
        <h2 className="mb-3 text-sm font-semibold">Recent payments</h2>
        {paymentsList.isLoading ? (
          <TableSkeleton rows={4} />
        ) : !paymentsList.data?.items?.length ? (
          <EmptyState title="No payments yet" description="Record a payment below." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b text-xs text-muted-foreground">
                  <th className="py-2 pr-3">Number</th>
                  <th className="py-2 pr-3">Date</th>
                  <th className="py-2 pr-3">Amount</th>
                  <th className="py-2 pr-3">Unallocated</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2">Action</th>
                </tr>
              </thead>
              <tbody>
                {paymentsList.data.items.map((p) => (
                  <tr key={p.id} className="border-b last:border-0">
                    <td className="py-2 pr-3 font-medium">{p.paymentNumber}</td>
                    <td className="py-2 pr-3">{p.paymentDate ?? "—"}</td>
                    <td className="py-2 pr-3">
                      {formatMoney(p.amount, p.currencyCode)}
                    </td>
                    <td className="py-2 pr-3">
                      {formatMoney(p.amountUnallocated, p.currencyCode)}
                    </td>
                    <td className="py-2 pr-3">{p.status}</td>
                    <td className="py-2">
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => {
                          setPaymentId(p.id);
                          setCurrencyCode(p.currencyCode || "USD");
                        }}
                      >
                        Select
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <h2 className="mb-4 text-sm font-semibold">Record payment</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <Label htmlFor="pay-num">Payment number</Label>
              <Input
                id="pay-num"
                value={paymentNumber}
                onChange={(e) => setPaymentNumber(e.target.value)}
              />
            </div>
            <div className="sm:col-span-2">
              <Label htmlFor="pay-cust">Customer</Label>
              <Select
                id="pay-cust"
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
              <Label htmlFor="pay-amt">Amount</Label>
              <Input
                id="pay-amt"
                type="number"
                min="0"
                step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="pay-ccy">Currency</Label>
              <Input
                id="pay-ccy"
                value={currencyCode}
                onChange={(e) => setCurrencyCode(e.target.value.toUpperCase())}
                maxLength={3}
              />
            </div>
            <div>
              <Label htmlFor="pay-date">Payment date</Label>
              <Input
                id="pay-date"
                type="date"
                value={paymentDate}
                onChange={(e) => setPaymentDate(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="pay-method">Method</Label>
              <Select
                id="pay-method"
                value={method}
                onChange={(e) => setMethod(e.target.value as PaymentMethod)}
              >
                {METHODS.map((m) => (
                  <option key={m} value={m}>
                    {m.replaceAll("_", " ")}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <Label htmlFor="pay-ref">Reference</Label>
              <Input
                id="pay-ref"
                value={reference}
                onChange={(e) => setReference(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="pay-notes">Notes</Label>
              <Input
                id="pay-notes"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </div>
          </div>
          <Button
            type="button"
            className="mt-4"
            disabled={createMut.isPending}
            onClick={() => createMut.mutate()}
          >
            {createMut.isPending ? "Recording…" : "Record payment"}
          </Button>
        </Card>

        <Card>
          <h2 className="mb-4 text-sm font-semibold">Allocate payment</h2>
          <div className="mb-3">
            <Label htmlFor="pay-id">Payment ID</Label>
            <Input
              id="pay-id"
              className="font-mono text-xs"
              value={paymentId}
              onChange={(e) => setPaymentId(e.target.value)}
              placeholder="UUID from record step"
            />
          </div>

          {!paymentId.trim() ? (
            <EmptyState
              title="No payment selected"
              description="Record a payment or paste a payment ID to allocate."
            />
          ) : (
            <>
              <div className="mb-4 flex flex-wrap gap-2">
                <Button
                  type="button"
                  disabled={fifoMut.isPending}
                  onClick={() => fifoMut.mutate()}
                >
                  {fifoMut.isPending ? "Allocating…" : "Allocate FIFO"}
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  disabled={reverseMut.isPending}
                  onClick={() => reverseMut.mutate()}
                >
                  {reverseMut.isPending ? "Reversing…" : "Reverse payment"}
                </Button>
              </div>

              <div className="mb-2 text-xs font-medium uppercase text-zinc-500">
                Manual allocation
              </div>
              <div className="space-y-2">
                {manualRows.map((row, idx) => (
                  <div key={idx} className="flex gap-2">
                    <Select
                      className="flex-1"
                      value={row.invoiceId}
                      onChange={(e) =>
                        setManualRows((rows) =>
                          rows.map((r, i) =>
                            i === idx
                              ? { ...r, invoiceId: e.target.value }
                              : r,
                          ),
                        )
                      }
                    >
                      <option value="">Invoice…</option>
                      {invoiceOptions.map((inv) => (
                        <option key={inv.id} value={inv.id}>
                          {inv.invoiceNumber} (
                          {formatMoney(inv.total, inv.currencyCode)})
                        </option>
                      ))}
                    </Select>
                    <Input
                      className="w-28"
                      type="number"
                      min="0"
                      step="0.01"
                      placeholder="Amt"
                      value={row.amount}
                      onChange={(e) =>
                        setManualRows((rows) =>
                          rows.map((r, i) =>
                            i === idx ? { ...r, amount: e.target.value } : r,
                          ),
                        )
                      }
                    />
                  </div>
                ))}
              </div>
              <div className="mt-2 flex gap-2">
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() =>
                    setManualRows((r) => [...r, { invoiceId: "", amount: "" }])
                  }
                >
                  Add row
                </Button>
                <Button
                  type="button"
                  disabled={manualMut.isPending}
                  onClick={() => manualMut.mutate()}
                >
                  {manualMut.isPending ? "Saving…" : "Allocate manual"}
                </Button>
              </div>

              <div className="mt-6">
                <h3 className="mb-2 text-sm font-semibold">Current allocations</h3>
                {allocQ.isLoading ? (
                  <TableSkeleton rows={2} />
                ) : allocQ.isError ? (
                  <p className="text-sm text-rose-600">
                    {(allocQ.error as Error).message}
                  </p>
                ) : !allocQ.data ? (
                  <p className="text-sm text-zinc-500">No data</p>
                ) : (
                  <div className="text-sm">
                    <p className="mb-2 text-zinc-600 dark:text-zinc-400">
                      Allocated{" "}
                      <span className="font-medium tabular-nums">
                        {formatMoney(allocQ.data.totalAllocated, currencyCode)}
                      </span>
                      {" · "}Remaining{" "}
                      <span className="font-medium tabular-nums">
                        {formatMoney(
                          allocQ.data.remainingUnallocated,
                          currencyCode,
                        )}
                      </span>
                      {allocQ.data.fullyAllocated ? " · Fully allocated" : ""}
                      {allocQ.data.version != null
                        ? ` · v${allocQ.data.version}`
                        : ""}
                    </p>
                    <ul className="divide-y divide-zinc-100 dark:divide-zinc-900">
                      {(allocQ.data.allocations ?? []).map((a) => (
                        <li
                          key={a.allocationId}
                          className="flex justify-between py-2"
                        >
                          <span className="font-mono text-xs">
                            {a.invoiceId.slice(0, 8)}…
                          </span>
                          <span className="tabular-nums">
                            {formatMoney(a.amount, currencyCode)}
                          </span>
                        </li>
                      ))}
                    </ul>
                    {allocQ.data.errors?.length ? (
                      <p className="mt-2 text-xs text-amber-700">
                        {allocQ.data.errors.join("; ")}
                      </p>
                    ) : null}
                  </div>
                )}
              </div>
            </>
          )}
        </Card>
      </div>
    </div>
  );
}
