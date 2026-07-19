"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, Ban, CheckCircle2, Trash2 } from "lucide-react";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { useTenant } from "@/components/tenant-provider";
import {
  blockCustomer,
  creditCheck,
  deleteCustomer,
  getCustomer,
  unblockCustomer,
  updateCustomer,
} from "@/lib/api/customers";
import { formatMoney } from "@/lib/money";
import { ApiError } from "@/lib/errors";
import type { CustomerStatus } from "@/types/ar";

export default function CustomerDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const router = useRouter();
  const { tenantId, ready } = useTenant();
  const queryClient = useQueryClient();

  const customerQ = useQuery({
    queryKey: ["customer", tenantId, id],
    enabled: ready && !!id,
    queryFn: ({ signal }) => getCustomer(tenantId, id, signal),
  });

  const c = customerQ.data;

  const [displayName, setDisplayName] = useState<string | null>(null);
  const [email, setEmail] = useState<string | null>(null);
  const [phone, setPhone] = useState<string | null>(null);
  const [billingAddress, setBillingAddress] = useState<string | null>(null);
  const [creditLimit, setCreditLimit] = useState<string | null>(null);
  const [paymentTerms, setPaymentTerms] = useState<string | null>(null);
  const [invoiceAmount, setInvoiceAmount] = useState("100");
  const [outstanding, setOutstanding] = useState("0");
  const [creditResult, setCreditResult] = useState<{
    canInvoice: boolean;
    availableCredit: number | string | null;
    message: string;
  } | null>(null);

  // Sync form when customer loads
  const formReady = c != null;
  const dn = displayName ?? c?.displayName ?? "";
  const em = email ?? c?.email ?? "";
  const ph = phone ?? c?.phone ?? "";
  const ba = billingAddress ?? c?.billingAddress ?? "";
  const cl =
    creditLimit ??
    (c?.creditLimit != null ? String(c.creditLimit) : "");
  const pt = paymentTerms ?? c?.paymentTerms ?? "";

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["customer", tenantId, id] });
    void queryClient.invalidateQueries({ queryKey: ["customers", tenantId] });
    void queryClient.invalidateQueries({
      queryKey: ["customer-stats", tenantId],
    });
  };

  const onErr = (err: Error) =>
    toast.error(err instanceof ApiError ? err.message : err.message);

  const updateMut = useMutation({
    mutationFn: () =>
      updateCustomer(tenantId, id, {
        displayName: dn || null,
        email: em || null,
        phone: ph || null,
        billingAddress: ba || null,
        creditLimit: cl === "" ? null : Number(cl),
        paymentTerms: pt || null,
      }),
    onSuccess: () => {
      toast.success("Customer updated");
      setDisplayName(null);
      setEmail(null);
      setPhone(null);
      setBillingAddress(null);
      setCreditLimit(null);
      setPaymentTerms(null);
      invalidate();
    },
    onError: onErr,
  });

  const blockMut = useMutation({
    mutationFn: () => blockCustomer(tenantId, id),
    onSuccess: () => {
      toast.success("Customer blocked");
      invalidate();
    },
    onError: onErr,
  });

  const unblockMut = useMutation({
    mutationFn: () => unblockCustomer(tenantId, id),
    onSuccess: () => {
      toast.success("Customer unblocked");
      invalidate();
    },
    onError: onErr,
  });

  const deleteMut = useMutation({
    mutationFn: () => deleteCustomer(tenantId, id),
    onSuccess: () => {
      toast.success("Customer deleted");
      invalidate();
      router.push("/customers");
    },
    onError: onErr,
  });

  const creditMut = useMutation({
    mutationFn: () =>
      creditCheck(
        tenantId,
        id,
        Number(invoiceAmount),
        Number(outstanding || 0),
      ),
    onSuccess: (result) => {
      setCreditResult(result);
      if (result.canInvoice) toast.success("Credit check passed");
      else toast.message("Credit check failed", { description: result.message });
    },
    onError: onErr,
  });

  if (customerQ.isLoading) {
    return <p className="text-sm text-zinc-500">Loading customer…</p>;
  }

  if (customerQ.isError || !c) {
    return (
      <div>
        <Link
          href="/customers"
          className="mb-4 inline-flex items-center gap-1 text-sm text-indigo-600 hover:underline"
        >
          <ArrowLeft className="h-4 w-4" /> Back to customers
        </Link>
        <p className="text-sm text-rose-600">
          {(customerQ.error as Error)?.message ?? "Customer not found"}
        </p>
      </div>
    );
  }

  const isDeleted = c.status === "DELETED";
  const isBlocked = c.status === "BLOCKED";
  const isActive = c.status === "ACTIVE";

  return (
    <div>
      <Link
        href="/customers"
        className="mb-4 inline-flex items-center gap-1 text-sm text-indigo-600 hover:underline dark:text-indigo-400"
      >
        <ArrowLeft className="h-4 w-4" /> Back to customers
      </Link>

      <PageHeader
        title={c.displayName || c.legalName}
        description={`${c.customerCode} · ${c.currency}`}
        actions={
          <div className="flex flex-wrap gap-2">
            {isActive ? (
              <Button
                type="button"
                variant="secondary"
                disabled={blockMut.isPending}
                onClick={() => blockMut.mutate()}
              >
                <Ban className="h-4 w-4" />
                Block
              </Button>
            ) : null}
            {isBlocked ? (
              <Button
                type="button"
                variant="secondary"
                disabled={unblockMut.isPending}
                onClick={() => unblockMut.mutate()}
              >
                <CheckCircle2 className="h-4 w-4" />
                Unblock
              </Button>
            ) : null}
            {!isDeleted ? (
              <Button
                type="button"
                variant="danger"
                disabled={deleteMut.isPending}
                onClick={() => {
                  if (
                    window.confirm(
                      `Soft-delete customer ${c.customerCode}? This cannot be undone from the UI.`,
                    )
                  ) {
                    deleteMut.mutate();
                  }
                }}
              >
                <Trash2 className="h-4 w-4" />
                Delete
              </Button>
            ) : null}
          </div>
        }
      />

      <div className="mb-4 flex items-center gap-3">
        <StatusBadge status={c.status as CustomerStatus} />
        <span className="font-mono text-xs text-zinc-500">{c.id}</span>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <h2 className="mb-4 text-sm font-semibold">Profile</h2>
          {!formReady ? null : (
            <div className="grid gap-3">
              <div>
                <Label>Legal name</Label>
                <Input value={c.legalName} disabled />
              </div>
              <div>
                <Label htmlFor="displayName">Display name</Label>
                <Input
                  id="displayName"
                  value={dn}
                  onChange={(e) => setDisplayName(e.target.value)}
                  disabled={isDeleted}
                />
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <Label htmlFor="email">Email</Label>
                  <Input
                    id="email"
                    type="email"
                    value={em}
                    onChange={(e) => setEmail(e.target.value)}
                    disabled={isDeleted}
                  />
                </div>
                <div>
                  <Label htmlFor="phone">Phone</Label>
                  <Input
                    id="phone"
                    value={ph}
                    onChange={(e) => setPhone(e.target.value)}
                    disabled={isDeleted}
                  />
                </div>
              </div>
              <div>
                <Label htmlFor="billingAddress">Billing address</Label>
                <Input
                  id="billingAddress"
                  value={ba}
                  onChange={(e) => setBillingAddress(e.target.value)}
                  disabled={isDeleted}
                />
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <Label htmlFor="creditLimit">Credit limit</Label>
                  <Input
                    id="creditLimit"
                    type="number"
                    min="0"
                    step="0.01"
                    value={cl}
                    onChange={(e) => setCreditLimit(e.target.value)}
                    disabled={isDeleted}
                  />
                </div>
                <div>
                  <Label htmlFor="paymentTerms">Payment terms</Label>
                  <Input
                    id="paymentTerms"
                    value={pt}
                    onChange={(e) => setPaymentTerms(e.target.value)}
                    placeholder="NET30"
                    disabled={isDeleted}
                  />
                </div>
              </div>
              {!isDeleted ? (
                <div className="pt-2">
                  <Button
                    type="button"
                    disabled={updateMut.isPending}
                    onClick={() => updateMut.mutate()}
                  >
                    {updateMut.isPending ? "Saving…" : "Save changes"}
                  </Button>
                </div>
              ) : null}
            </div>
          )}
        </Card>

        <div className="space-y-6">
          <Card>
            <h2 className="mb-4 text-sm font-semibold">Credit check</h2>
            <p className="mb-3 text-xs text-zinc-500">
              Calls{" "}
              <code className="font-mono">
                GET /api/v1/customers/{"{id}"}/credit-check
              </code>
            </p>
            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <Label htmlFor="invoiceAmount">Invoice amount</Label>
                <Input
                  id="invoiceAmount"
                  type="number"
                  min="0"
                  step="0.01"
                  value={invoiceAmount}
                  onChange={(e) => setInvoiceAmount(e.target.value)}
                />
              </div>
              <div>
                <Label htmlFor="outstanding">Outstanding</Label>
                <Input
                  id="outstanding"
                  type="number"
                  min="0"
                  step="0.01"
                  value={outstanding}
                  onChange={(e) => setOutstanding(e.target.value)}
                />
              </div>
            </div>
            <div className="mt-3">
              <Button
                type="button"
                variant="secondary"
                disabled={creditMut.isPending || !invoiceAmount}
                onClick={() => creditMut.mutate()}
              >
                {creditMut.isPending ? "Checking…" : "Run credit check"}
              </Button>
            </div>
            {creditResult ? (
              <div
                className={`mt-4 rounded-lg border p-3 text-sm ${
                  creditResult.canInvoice
                    ? "border-emerald-200 bg-emerald-50 text-emerald-900 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-100"
                    : "border-rose-200 bg-rose-50 text-rose-900 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-100"
                }`}
              >
                <div className="font-medium">
                  {creditResult.canInvoice ? "Can invoice" : "Cannot invoice"}
                </div>
                <div className="mt-1 text-xs opacity-90">
                  Available credit:{" "}
                  {formatMoney(creditResult.availableCredit, c.currency)}
                </div>
                {creditResult.message ? (
                  <div className="mt-1 text-xs opacity-90">
                    {creditResult.message}
                  </div>
                ) : null}
              </div>
            ) : null}
          </Card>

          <Card>
            <h2 className="mb-3 text-sm font-semibold">Metadata</h2>
            <dl className="grid grid-cols-2 gap-2 text-sm">
              <dt className="text-zinc-500">Created</dt>
              <dd className="truncate font-mono text-xs">
                {c.createdAt ?? "—"}
              </dd>
              <dt className="text-zinc-500">Updated</dt>
              <dd className="truncate font-mono text-xs">
                {c.updatedAt ?? "—"}
              </dd>
              <dt className="text-zinc-500">Version</dt>
              <dd>{c.version ?? "—"}</dd>
              <dt className="text-zinc-500">Tax ID</dt>
              <dd>{c.taxId ?? "—"}</dd>
            </dl>
          </Card>
        </div>
      </div>
    </div>
  );
}