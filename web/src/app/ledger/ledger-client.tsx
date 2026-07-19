"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label, Select } from "@/components/ui/input";
import { TableSkeleton } from "@/components/ui/skeleton";
import { useTenant } from "@/components/tenant-provider";
import {
  getLedgerBalance,
  getLedgerByReference,
  listLedgerAccounts,
} from "@/lib/api/ledger";
import { formatMoney } from "@/lib/money";

export default function LedgerClient() {
  const { tenantId, ready } = useTenant();
  const searchParams = useSearchParams();
  const [account, setAccount] = useState("ACCOUNTS_RECEIVABLE");
  const [currency, setCurrency] = useState("USD");
  const [refType, setRefType] = useState(
    searchParams.get("type")?.toUpperCase() || "INVOICE",
  );
  const [refId, setRefId] = useState(searchParams.get("id") || "");
  const [lookupKey, setLookupKey] = useState(
    searchParams.get("id")
      ? `${(searchParams.get("type") || "INVOICE").toUpperCase()}:${searchParams.get("id")}`
      : "",
  );

  const accounts = useQuery({
    queryKey: ["ledger-accounts", tenantId],
    enabled: ready,
    queryFn: ({ signal }) => listLedgerAccounts(tenantId, signal),
  });

  const balance = useQuery({
    queryKey: ["ledger-balance", tenantId, account, currency],
    enabled: ready && !!account,
    queryFn: ({ signal }) =>
      getLedgerBalance(tenantId, account, currency, signal),
  });

  const refEntries = useQuery({
    queryKey: ["ledger-ref", tenantId, lookupKey],
    enabled: ready && !!lookupKey,
    queryFn: ({ signal }) => {
      const [type, id] = lookupKey.split(":");
      return getLedgerByReference(tenantId, type, id, signal);
    },
  });

  return (
    <div>
      <PageHeader
        title="Ledger"
        description="Read-only chart of accounts, balances, and reference lookup."
      />

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <h2 className="mb-3 text-sm font-semibold">Accounts</h2>
          {accounts.isLoading ? (
            <TableSkeleton rows={4} />
          ) : accounts.isError ? (
            <p className="text-sm text-rose-600">
              {(accounts.error as Error).message}
            </p>
          ) : (
            <ul className="max-h-72 space-y-1 overflow-y-auto text-sm">
              {(accounts.data ?? []).map((a) => (
                <li key={a.code}>
                  <button
                    type="button"
                    className={`w-full rounded-lg px-3 py-2 text-left hover:bg-zinc-100 dark:hover:bg-zinc-900 ${
                      account === a.code
                        ? "bg-indigo-50 text-indigo-800 dark:bg-indigo-950 dark:text-indigo-200"
                        : ""
                    }`}
                    onClick={() => setAccount(a.code)}
                  >
                    <span className="font-mono text-xs text-zinc-500">
                      {a.code}
                    </span>
                    <div className="font-medium">{a.name}</div>
                    <div className="text-xs text-zinc-500">{a.type}</div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <h2 className="mb-3 text-sm font-semibold">Account balance</h2>
          <div className="mb-3 flex flex-wrap gap-3">
            <div className="min-w-[12rem] flex-1">
              <Label htmlFor="acct">Account</Label>
              <Select
                id="acct"
                value={account}
                onChange={(e) => setAccount(e.target.value)}
              >
                {(accounts.data ?? [{ code: account, name: account, type: "" }]).map(
                  (a) => (
                    <option key={a.code} value={a.code}>
                      {a.code}
                    </option>
                  ),
                )}
              </Select>
            </div>
            <div className="w-28">
              <Label htmlFor="ccy">Currency</Label>
              <Input
                id="ccy"
                value={currency}
                onChange={(e) => setCurrency(e.target.value.toUpperCase())}
                maxLength={3}
              />
            </div>
          </div>
          {balance.isLoading ? (
            <TableSkeleton rows={1} />
          ) : balance.isError ? (
            <p className="text-sm text-rose-600">
              {(balance.error as Error).message}
            </p>
          ) : (
            <div className="text-3xl font-semibold tabular-nums">
              {formatMoney(balance.data?.balance, balance.data?.currency || currency)}
            </div>
          )}
        </Card>
      </div>

      <Card className="mt-6">
        <h2 className="mb-3 text-sm font-semibold">Lookup by reference</h2>
        <div className="mb-4 flex flex-wrap items-end gap-3">
          <div className="w-40">
            <Label htmlFor="rtype">Type</Label>
            <Select
              id="rtype"
              value={refType}
              onChange={(e) => setRefType(e.target.value)}
            >
              <option value="INVOICE">INVOICE</option>
              <option value="PAYMENT">PAYMENT</option>
              <option value="CHEQUE">CHEQUE</option>
              <option value="CREDIT_NOTE">CREDIT_NOTE</option>
            </Select>
          </div>
          <div className="min-w-[16rem] flex-1">
            <Label htmlFor="rid">Reference ID</Label>
            <Input
              id="rid"
              className="font-mono text-xs"
              value={refId}
              onChange={(e) => setRefId(e.target.value)}
              placeholder="UUID"
            />
          </div>
          <Button
            type="button"
            onClick={() => {
              if (refId.trim()) setLookupKey(`${refType}:${refId.trim()}`);
            }}
          >
            Lookup
          </Button>
        </div>

        {!lookupKey ? (
          <p className="text-sm text-zinc-500">
            Enter a reference ID from an invoice or payment detail page.
          </p>
        ) : refEntries.isLoading ? (
          <TableSkeleton rows={3} />
        ) : refEntries.isError ? (
          <p className="text-sm text-rose-600">
            {(refEntries.error as Error).message}
          </p>
        ) : !refEntries.data?.length ? (
          <p className="text-sm text-zinc-500">No ledger entries for this reference.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-zinc-200 text-xs uppercase text-zinc-500 dark:border-zinc-800">
                <tr>
                  <th className="pb-2 pr-3 font-medium">Account</th>
                  <th className="pb-2 pr-3 font-medium">Type</th>
                  <th className="pb-2 pr-3 font-medium">Description</th>
                  <th className="pb-2 text-right font-medium">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-900">
                {refEntries.data.map((e) => (
                  <tr key={e.id}>
                    <td className="py-2 pr-3 font-mono text-xs">{e.account}</td>
                    <td className="py-2 pr-3">{e.entryType}</td>
                    <td className="py-2 pr-3 text-zinc-600 dark:text-zinc-400">
                      {e.description || "-"}
                    </td>
                    <td className="py-2 text-right tabular-nums">
                      {formatMoney(e.amount, currency)}
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