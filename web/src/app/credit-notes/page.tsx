"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Plus } from "lucide-react";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusBadge } from "@/components/ui/status-badge";
import { Button } from "@/components/ui/button";
import { Input, Label, Select } from "@/components/ui/input";
import { TableSkeleton } from "@/components/ui/skeleton";
import { useTenant } from "@/components/tenant-provider";
import { listCustomers } from "@/lib/api/customers";
import {
  applyCreditNote,
  generateCreditNote,
  listCreditNotes,
} from "@/lib/api/credit-notes";
import { formatMoney } from "@/lib/money";
import { ApiError } from "@/lib/errors";

export default function CreditNotesPage() {
  const { tenantId, ready } = useTenant();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [customerId, setCustomerId] = useState("");
  const [discountAmount, setDiscountAmount] = useState("");
  const [currencyCode, setCurrencyCode] = useState("USD");
  const [referenceInvoiceId, setReferenceInvoiceId] = useState("");
  const [applyId, setApplyId] = useState<string | null>(null);
  const [paymentId, setPaymentId] = useState("");

  const notes = useQuery({
    queryKey: ["credit-notes", tenantId, status],
    enabled: ready,
    queryFn: ({ signal }) =>
      listCreditNotes(tenantId, { status: status || undefined, signal }),
  });

  const customers = useQuery({
    queryKey: ["customers", tenantId, "ACTIVE"],
    enabled: ready && showCreate,
    queryFn: ({ signal }) =>
      listCustomers(tenantId, { status: "ACTIVE", signal }),
  });

  const invalidate = () =>
    void queryClient.invalidateQueries({
      queryKey: ["credit-notes", tenantId],
    });

  const onErr = (err: Error) =>
    toast.error(err instanceof ApiError ? err.message : err.message);

  const createMut = useMutation({
    mutationFn: () => {
      const n = Number(discountAmount);
      if (!customerId) throw new Error("Customer required");
      if (Number.isNaN(n) || n <= 0) throw new Error("Valid amount required");
      return generateCreditNote(tenantId, {
        customerId,
        discountAmount: n,
        currencyCode: currencyCode || "USD",
        referenceInvoiceId: referenceInvoiceId.trim() || undefined,
      });
    },
    onSuccess: (cn) => {
      toast.success(`Credit note ${cn.creditNoteNumber} created`);
      setShowCreate(false);
      setDiscountAmount("");
      setReferenceInvoiceId("");
      invalidate();
    },
    onError: onErr,
  });

  const applyMut = useMutation({
    mutationFn: () => {
      if (!applyId) throw new Error("No credit note selected");
      if (!paymentId.trim()) throw new Error("Payment ID required");
      return applyCreditNote(tenantId, applyId, paymentId.trim());
    },
    onSuccess: (r) => {
      toast.success(r.message || "Credit note applied");
      setApplyId(null);
      setPaymentId("");
      invalidate();
    },
    onError: onErr,
  });

  return (
    <div>
      <PageHeader
        title="Credit notes"
        description="Generate early-pay credit notes and apply them to payments."
        actions={
          <Button type="button" onClick={() => setShowCreate((v) => !v)}>
            <Plus className="h-4 w-4" />
            New credit note
          </Button>
        }
      />

      {showCreate ? (
        <Card className="mb-6">
          <h2 className="mb-4 text-sm font-semibold">
            Generate early-payment credit note
          </h2>
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <Label htmlFor="cn-cust">Customer</Label>
              <Select
                id="cn-cust"
                value={customerId}
                onChange={(e) => setCustomerId(e.target.value)}
              >
                <option value="">Select...</option>
                {(customers.data ?? []).map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.customerCode} - {c.displayName || c.legalName}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <Label htmlFor="cn-amt">Discount amount</Label>
              <Input
                id="cn-amt"
                type="number"
                min="0"
                step="0.01"
                value={discountAmount}
                onChange={(e) => setDiscountAmount(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="cn-ccy">Currency</Label>
              <Input
                id="cn-ccy"
                value={currencyCode}
                onChange={(e) => setCurrencyCode(e.target.value.toUpperCase())}
                maxLength={3}
              />
            </div>
            <div>
              <Label htmlFor="cn-inv">Reference invoice ID (optional)</Label>
              <Input
                id="cn-inv"
                className="font-mono text-xs"
                value={referenceInvoiceId}
                onChange={(e) => setReferenceInvoiceId(e.target.value)}
              />
            </div>
          </div>
          <div className="mt-4 flex gap-2">
            <Button
              type="button"
              disabled={createMut.isPending}
              onClick={() => createMut.mutate()}
            >
              {createMut.isPending ? "Creating..." : "Generate"}
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

      {applyId ? (
        <Card className="mb-6">
          <h2 className="mb-2 text-sm font-semibold">Apply credit note</h2>
          <p className="mb-3 font-mono text-xs text-zinc-500">{applyId}</p>
          <Label htmlFor="cn-pay">Payment ID</Label>
          <Input
            id="cn-pay"
            className="font-mono text-xs"
            value={paymentId}
            onChange={(e) => setPaymentId(e.target.value)}
            placeholder="Payment UUID"
          />
          <div className="mt-3 flex gap-2">
            <Button
              type="button"
              disabled={applyMut.isPending || !paymentId.trim()}
              onClick={() => applyMut.mutate()}
            >
              Apply to payment
            </Button>
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setApplyId(null);
                setPaymentId("");
              }}
            >
              Cancel
            </Button>
          </div>
        </Card>
      ) : null}

      <Card>
        <div className="mb-4 w-full sm:w-52">
          <Label htmlFor="cn-status">Status</Label>
          <Select
            id="cn-status"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
          >
            <option value="">All</option>
            <option value="ISSUED">Issued</option>
            <option value="APPLIED">Applied</option>
            <option value="EXPIRED">Expired</option>
            <option value="CANCELLED">Cancelled</option>
          </Select>
        </div>

        {notes.isLoading ? (
          <TableSkeleton />
        ) : notes.isError ? (
          <p className="py-8 text-center text-sm text-rose-600">
            {(notes.error as Error).message}
          </p>
        ) : !notes.data?.length ? (
          <EmptyState
            title="No credit notes"
            description="Generate a credit note for early payment discounts."
            action={
              <Button type="button" onClick={() => setShowCreate(true)}>
                <Plus className="h-4 w-4" />
                New credit note
              </Button>
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-zinc-200 text-xs uppercase text-zinc-500 dark:border-zinc-800">
                <tr>
                  <th className="pb-2 pr-3 font-medium">Number</th>
                  <th className="pb-2 pr-3 font-medium">Status</th>
                  <th className="pb-2 pr-3 font-medium">Type</th>
                  <th className="pb-2 pr-3 text-right font-medium">Amount</th>
                  <th className="pb-2 font-medium">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-900">
                {notes.data.map((cn) => (
                  <tr key={cn.id}>
                    <td className="py-3 pr-3">
                      <div className="font-medium">{cn.creditNoteNumber}</div>
                      <div className="font-mono text-xs text-zinc-500">
                        {cn.id.slice(0, 8)}...
                      </div>
                    </td>
                    <td className="py-3 pr-3">
                      <StatusBadge status={String(cn.status)} />
                    </td>
                    <td className="py-3 pr-3 text-xs text-zinc-600 dark:text-zinc-400">
                      {cn.type}
                    </td>
                    <td className="py-3 pr-3 text-right tabular-nums">
                      {formatMoney(cn.amount, cn.currencyCode)}
                    </td>
                    <td className="py-3">
                      {String(cn.status) === "ISSUED" ? (
                        <Button
                          type="button"
                          variant="secondary"
                          onClick={() => setApplyId(cn.id)}
                        >
                          Apply...
                        </Button>
                      ) : null}
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