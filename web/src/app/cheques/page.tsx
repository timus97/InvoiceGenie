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
  bounceCheque,
  clearCheque,
  createCheque,
  depositCheque,
  listCheques,
} from "@/lib/api/cheques";
import { formatMoney } from "@/lib/money";
import { ApiError } from "@/lib/errors";

const STATUSES = ["RECEIVED", "DEPOSITED", "CLEARED", "BOUNCED"] as const;

export default function ChequesPage() {
  const { tenantId, ready } = useTenant();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [bounceId, setBounceId] = useState<string | null>(null);
  const [bounceReason, setBounceReason] = useState("");
  const [chequeNumber, setChequeNumber] = useState("");
  const [customerId, setCustomerId] = useState("");
  const [amount, setAmount] = useState("");
  const [currencyCode, setCurrencyCode] = useState("USD");
  const [bankName, setBankName] = useState("");
  const [bankBranch, setBankBranch] = useState("");
  const [chequeDate, setChequeDate] = useState(
    () => new Date().toISOString().slice(0, 10),
  );
  const [notes, setNotes] = useState("");

  const cheques = useQuery({
    queryKey: ["cheques", tenantId, status],
    enabled: ready,
    queryFn: ({ signal }) =>
      listCheques(tenantId, { status: status || undefined, signal }),
  });

  const customers = useQuery({
    queryKey: ["customers", tenantId, "ACTIVE"],
    enabled: ready && showCreate,
    queryFn: ({ signal }) =>
      listCustomers(tenantId, { status: "ACTIVE", signal }),
  });

  const invalidate = () =>
    void queryClient.invalidateQueries({ queryKey: ["cheques", tenantId] });

  const onErr = (err: Error) =>
    toast.error(err instanceof ApiError ? err.message : err.message);

  const createMut = useMutation({
    mutationFn: () => {
      const n = Number(amount);
      if (!chequeNumber.trim()) throw new Error("Cheque number required");
      if (!customerId) throw new Error("Customer required");
      if (Number.isNaN(n) || n <= 0) throw new Error("Valid amount required");
      return createCheque(tenantId, {
        chequeNumber: chequeNumber.trim(),
        customerId,
        amount: n,
        currencyCode: currencyCode || "USD",
        bankName: bankName || undefined,
        bankBranch: bankBranch || undefined,
        chequeDate: chequeDate || undefined,
        notes: notes || undefined,
      });
    },
    onSuccess: (c) => {
      toast.success(`Cheque ${c.chequeNumber} received`);
      setShowCreate(false);
      setChequeNumber("");
      setAmount("");
      invalidate();
    },
    onError: onErr,
  });

  const depositMut = useMutation({
    mutationFn: (id: string) => depositCheque(tenantId, id),
    onSuccess: () => {
      toast.success("Cheque deposited");
      invalidate();
    },
    onError: onErr,
  });

  const clearMut = useMutation({
    mutationFn: (id: string) => clearCheque(tenantId, id),
    onSuccess: () => {
      toast.success("Cheque cleared");
      invalidate();
    },
    onError: onErr,
  });

  const bounceMut = useMutation({
    mutationFn: () => {
      if (!bounceId) throw new Error("No cheque selected");
      if (!bounceReason.trim()) throw new Error("Bounce reason required");
      return bounceCheque(tenantId, bounceId, bounceReason.trim());
    },
    onSuccess: (r) => {
      const affected = r.affectedInvoices?.length
        ? ` Affected invoices: ${r.affectedInvoices.length}`
        : "";
      toast.success(`Cheque bounced.${affected}`);
      setBounceId(null);
      setBounceReason("");
      invalidate();
    },
    onError: onErr,
  });

  return (
    <div>
      <PageHeader
        title="Cheques"
        description="Cheque lifecycle: received to deposited to cleared or bounced."
        actions={
          <Button type="button" onClick={() => setShowCreate((v) => !v)}>
            <Plus className="h-4 w-4" />
            New cheque
          </Button>
        }
      />

      {showCreate ? (
        <Card className="mb-6">
          <h2 className="mb-4 text-sm font-semibold">Receive cheque</h2>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <div>
              <Label htmlFor="chq-num">Cheque number</Label>
              <Input
                id="chq-num"
                value={chequeNumber}
                onChange={(e) => setChequeNumber(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="chq-cust">Customer</Label>
              <Select
                id="chq-cust"
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
              <Label htmlFor="chq-amt">Amount</Label>
              <Input
                id="chq-amt"
                type="number"
                min="0"
                step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="chq-ccy">Currency</Label>
              <Input
                id="chq-ccy"
                value={currencyCode}
                onChange={(e) => setCurrencyCode(e.target.value.toUpperCase())}
                maxLength={3}
              />
            </div>
            <div>
              <Label htmlFor="chq-bank">Bank</Label>
              <Input
                id="chq-bank"
                value={bankName}
                onChange={(e) => setBankName(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="chq-branch">Branch</Label>
              <Input
                id="chq-branch"
                value={bankBranch}
                onChange={(e) => setBankBranch(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="chq-date">Cheque date</Label>
              <Input
                id="chq-date"
                type="date"
                value={chequeDate}
                onChange={(e) => setChequeDate(e.target.value)}
              />
            </div>
            <div className="sm:col-span-2">
              <Label htmlFor="chq-notes">Notes</Label>
              <Input
                id="chq-notes"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </div>
          </div>
          <div className="mt-4 flex gap-2">
            <Button
              type="button"
              disabled={createMut.isPending}
              onClick={() => createMut.mutate()}
            >
              {createMut.isPending ? "Saving..." : "Receive cheque"}
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

      {bounceId ? (
        <Card className="mb-6 border-rose-200 dark:border-rose-900">
          <h2 className="mb-2 text-sm font-semibold text-rose-700 dark:text-rose-300">
            Bounce cheque
          </h2>
          <p className="mb-3 font-mono text-xs text-zinc-500">{bounceId}</p>
          <Label htmlFor="bounce-reason">Reason (required)</Label>
          <Input
            id="bounce-reason"
            value={bounceReason}
            onChange={(e) => setBounceReason(e.target.value)}
            placeholder="NSF / stop payment"
          />
          <div className="mt-3 flex gap-2">
            <Button
              type="button"
              variant="danger"
              disabled={bounceMut.isPending || !bounceReason.trim()}
              onClick={() => bounceMut.mutate()}
            >
              Confirm bounce
            </Button>
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setBounceId(null);
                setBounceReason("");
              }}
            >
              Cancel
            </Button>
          </div>
        </Card>
      ) : null}

      <Card>
        <div className="mb-4 w-full sm:w-52">
          <Label htmlFor="chq-status">Status</Label>
          <Select
            id="chq-status"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
          >
            <option value="">All</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </Select>
        </div>

        {cheques.isLoading ? (
          <TableSkeleton />
        ) : cheques.isError ? (
          <p className="py-8 text-center text-sm text-rose-600">
            {(cheques.error as Error).message}
          </p>
        ) : !cheques.data?.length ? (
          <EmptyState
            title="No cheques"
            description="Receive a cheque to start the deposit / clear / bounce flow."
            action={
              <Button type="button" onClick={() => setShowCreate(true)}>
                <Plus className="h-4 w-4" />
                New cheque
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
                  <th className="pb-2 pr-3 font-medium">Bank</th>
                  <th className="pb-2 pr-3 text-right font-medium">Amount</th>
                  <th className="pb-2 font-medium">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-900">
                {cheques.data.map((c) => (
                  <tr key={c.id}>
                    <td className="py-3 pr-3">
                      <div className="font-medium">{c.chequeNumber}</div>
                      <div className="font-mono text-xs text-zinc-500">
                        {c.id.slice(0, 8)}...
                      </div>
                    </td>
                    <td className="py-3 pr-3">
                      <StatusBadge status={String(c.status)} />
                      {c.bounceReason ? (
                        <div className="mt-1 text-xs text-rose-600">
                          {c.bounceReason}
                        </div>
                      ) : null}
                    </td>
                    <td className="py-3 pr-3 text-zinc-600 dark:text-zinc-400">
                      {c.bankName || "-"}
                      {c.bankBranch ? " / " + c.bankBranch : ""}
                    </td>
                    <td className="py-3 pr-3 text-right tabular-nums">
                      {formatMoney(c.amount, c.currencyCode)}
                    </td>
                    <td className="py-3">
                      <div className="flex flex-wrap gap-1">
                        {c.status === "RECEIVED" ? (
                          <Button
                            type="button"
                            variant="secondary"
                            disabled={depositMut.isPending}
                            onClick={() => depositMut.mutate(c.id)}
                          >
                            Deposit
                          </Button>
                        ) : null}
                        {c.status === "DEPOSITED" ? (
                          <>
                            <Button
                              type="button"
                              disabled={clearMut.isPending}
                              onClick={() => clearMut.mutate(c.id)}
                            >
                              Clear
                            </Button>
                            <Button
                              type="button"
                              variant="danger"
                              onClick={() => setBounceId(c.id)}
                            >
                              Bounce
                            </Button>
                          </>
                        ) : null}
                      </div>
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