"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
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
  getCustomerArSummary,
  unblockCustomer,
  updateCustomer,
} from "@/lib/api/customers";
import {
  getCustomerNotificationPreferences,
  putCustomerNotificationPreferences,
} from "@/lib/api/notifications";
import { formatMoney } from "@/lib/money";
import { ApiError } from "@/lib/errors";
import type { CustomerStatus, NotificationPreferenceDto } from "@/types/ar";

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
  const [creditResult, setCreditResult] = useState<{
    canInvoice: boolean;
    availableCredit: number | string | null;
    message: string;
  } | null>(null);

  const arSummaryQ = useQuery({
    queryKey: ["customer-ar-summary", tenantId, id],
    enabled: ready && !!id,
    queryFn: ({ signal }) => getCustomerArSummary(tenantId, id, signal),
  });

  const prefsQ = useQuery({
    queryKey: ["customer-notif-prefs", tenantId, id],
    enabled: ready && !!id,
    queryFn: ({ signal }) =>
      getCustomerNotificationPreferences(tenantId, id, signal),
  });

  const [emailEnabled, setEmailEnabled] = useState(true);
  const [waEnabled, setWaEnabled] = useState(true);
  const [emailOverride, setEmailOverride] = useState("");
  const [waOverride, setWaOverride] = useState("");

  useEffect(() => {
    if (!prefsQ.data) return;
    const emailPref = prefsQ.data.find((p) => p.channel === "EMAIL");
    const waPref = prefsQ.data.find((p) => p.channel === "WHATSAPP");
    setEmailEnabled(emailPref?.enabled ?? true);
    setWaEnabled(waPref?.enabled ?? true);
    setEmailOverride(emailPref?.destinationOverride ?? "");
    setWaOverride(waPref?.destinationOverride ?? "");
  }, [prefsQ.data]);

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

  const prefsMut = useMutation({
    mutationFn: () => {
      const body: NotificationPreferenceDto[] = [
        {
          channel: "EMAIL",
          enabled: emailEnabled,
          destinationOverride: emailOverride || null,
        },
        {
          channel: "WHATSAPP",
          enabled: waEnabled,
          destinationOverride: waOverride || null,
        },
      ];
      return putCustomerNotificationPreferences(tenantId, id, body);
    },
    onSuccess: () => {
      toast.success("Notification preferences saved");
      void queryClient.invalidateQueries({
        queryKey: ["customer-notif-prefs", tenantId, id],
      });
    },
    onError: onErr,
  });

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
    mutationFn: () => creditCheck(tenantId, id, Number(invoiceAmount)),
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
            <h2 className="mb-4 text-sm font-semibold">
              Notification preferences
            </h2>
            <p className="mb-3 text-xs text-zinc-500">
              Opt-out blocks automated and manual customer sends for that
              channel. Destination override optional.
            </p>
            {prefsQ.isLoading ? (
              <p className="text-sm text-zinc-500">Loading…</p>
            ) : (
              <div className="space-y-3 text-sm">
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={emailEnabled}
                    onChange={(e) => setEmailEnabled(e.target.checked)}
                    disabled={isDeleted}
                  />
                  Email enabled
                </label>
                <div>
                  <Label htmlFor="emailOverride">Email override</Label>
                  <Input
                    id="emailOverride"
                    value={emailOverride}
                    onChange={(e) => setEmailOverride(e.target.value)}
                    placeholder={c.email ?? "customer email"}
                    disabled={isDeleted}
                  />
                </div>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={waEnabled}
                    onChange={(e) => setWaEnabled(e.target.checked)}
                    disabled={isDeleted}
                  />
                  WhatsApp enabled
                </label>
                <div>
                  <Label htmlFor="waOverride">WhatsApp / phone override</Label>
                  <Input
                    id="waOverride"
                    value={waOverride}
                    onChange={(e) => setWaOverride(e.target.value)}
                    placeholder={c.phone ?? "phone E.164"}
                    disabled={isDeleted}
                  />
                </div>
                {!isDeleted ? (
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={prefsMut.isPending}
                    onClick={() => prefsMut.mutate()}
                  >
                    {prefsMut.isPending ? "Saving…" : "Save preferences"}
                  </Button>
                ) : null}
              </div>
            )}
          </Card>

          <Card>
            <h2 className="mb-4 text-sm font-semibold">Open AR summary</h2>
            <p className="mb-3 text-xs text-zinc-500">
              Live balance from open invoices (STORY-014)
            </p>
            {arSummaryQ.isLoading ? (
              <p className="text-sm text-zinc-500">Loading…</p>
            ) : arSummaryQ.data ? (
              <div className="space-y-2 text-sm">
                <p>
                  Open invoices:{" "}
                  <strong>{arSummaryQ.data.openInvoiceCount}</strong>
                </p>
                <p>
                  Total balance:{" "}
                  <strong>
                    {formatMoney(
                      arSummaryQ.data.totalBalance,
                      arSummaryQ.data.baseCurrency === "MIXED"
                        ? (c.currency ?? "USD")
                        : arSummaryQ.data.baseCurrency,
                    )}
                  </strong>
                  {arSummaryQ.data.baseCurrency === "MIXED" ? (
                    <span className="ml-1 text-xs text-zinc-500">
                      (multi-currency; see rows)
                    </span>
                  ) : null}
                </p>
                {arSummaryQ.data.byCurrency?.length ? (
                  <ul className="mt-2 space-y-1 border-t border-zinc-100 pt-2 dark:border-zinc-800">
                    {arSummaryQ.data.byCurrency.map((row) => (
                      <li key={row.currency} className="text-xs text-zinc-600 dark:text-zinc-400">
                        {row.currency}: {row.openCount} open · billed{" "}
                        {formatMoney(row.totalBilled, row.currency)} · paid{" "}
                        {formatMoney(row.totalPaid, row.currency)} · bal{" "}
                        {formatMoney(row.balance, row.currency)}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-xs text-zinc-500">No open invoices</p>
                )}
              </div>
            ) : (
              <p className="text-sm text-zinc-500">No summary available</p>
            )}
          </Card>

          <Card>
            <h2 className="mb-4 text-sm font-semibold">Credit check</h2>
            <p className="mb-3 text-xs text-zinc-500">
              Uses system open AR as outstanding (no manual entry).
            </p>
            <div className="grid gap-3">
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