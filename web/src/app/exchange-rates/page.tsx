"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { useTenant } from "@/components/tenant-provider";
import {
  convertCurrency,
  createExchangeRate,
  listExchangeRates,
} from "@/lib/api/exchange-rates";
import { ApiError } from "@/lib/errors";

export default function ExchangeRatesPage() {
  const { tenantId, ready } = useTenant();
  const qc = useQueryClient();
  const [from, setFrom] = useState("EUR");
  const [to, setTo] = useState("USD");
  const [rate, setRate] = useState("1.10");
  const [amount, setAmount] = useState("100");
  const [converted, setConverted] = useState<string | null>(null);

  const list = useQuery({
    queryKey: ["exchange-rates", tenantId],
    enabled: ready,
    queryFn: ({ signal }) => listExchangeRates(tenantId, signal),
  });

  const createMut = useMutation({
    mutationFn: () =>
      createExchangeRate(tenantId, {
        fromCurrency: from.trim().toUpperCase(),
        toCurrency: to.trim().toUpperCase(),
        rate: Number(rate),
        source: "UI",
      }),
    onSuccess: () => {
      toast.success("Rate saved");
      void qc.invalidateQueries({ queryKey: ["exchange-rates", tenantId] });
    },
    onError: (e: Error) => toast.error(e instanceof ApiError ? e.message : e.message),
  });

  const convertMut = useMutation({
    mutationFn: () =>
      convertCurrency(tenantId, {
        amount: Number(amount),
        fromCurrency: from.trim().toUpperCase(),
        toCurrency: to.trim().toUpperCase(),
      }),
    onSuccess: (r) => setConverted(`${r.amount} ${r.currencyCode}`),
    onError: (e: Error) => toast.error(e instanceof ApiError ? e.message : e.message),
  });

  return (
    <div className="space-y-6">
      <PageHeader title="Exchange rates" description="Multi-currency rates and conversion" />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card className="space-y-3 p-4">
          <h2 className="text-sm font-semibold">Add rate</h2>
          <div className="grid grid-cols-3 gap-2">
            <div>
              <Label>From</Label>
              <Input value={from} onChange={(e) => setFrom(e.target.value)} />
            </div>
            <div>
              <Label>To</Label>
              <Input value={to} onChange={(e) => setTo(e.target.value)} />
            </div>
            <div>
              <Label>Rate</Label>
              <Input value={rate} onChange={(e) => setRate(e.target.value)} />
            </div>
          </div>
          <Button onClick={() => createMut.mutate()} disabled={createMut.isPending}>
            Save rate
          </Button>
        </Card>
        <Card className="space-y-3 p-4">
          <h2 className="text-sm font-semibold">Convert</h2>
          <div>
            <Label>Amount ({from || "FROM"})</Label>
            <Input value={amount} onChange={(e) => setAmount(e.target.value)} />
          </div>
          <Button onClick={() => convertMut.mutate()} disabled={convertMut.isPending}>
            Convert to {to || "TO"}
          </Button>
          {converted && <p className="text-sm font-medium">Result: {converted}</p>}
        </Card>
      </div>
      <Card className="overflow-x-auto p-0">
        <table className="w-full text-sm">
          <thead className="border-b bg-zinc-50 text-left dark:bg-zinc-900">
            <tr>
              <th className="px-4 py-2">Pair</th>
              <th className="px-4 py-2">Rate</th>
              <th className="px-4 py-2">Effective</th>
              <th className="px-4 py-2">Source</th>
            </tr>
          </thead>
          <tbody>
            {(list.data ?? []).map((r) => (
              <tr key={r.id} className="border-b last:border-0">
                <td className="px-4 py-2 font-mono text-xs">
                  {r.fromCurrency}/{r.toCurrency}
                </td>
                <td className="px-4 py-2">{String(r.rate)}</td>
                <td className="px-4 py-2">{r.effectiveDate}</td>
                <td className="px-4 py-2">{r.source ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  );
}