"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { StatusBadge } from "@/components/ui/status-badge";
import { useTenant } from "@/components/tenant-provider";
import {
  createWebhook,
  deactivateWebhook,
  deleteWebhook,
  listWebhookDeliveries,
  listWebhooks,
  redriveWebhookDelivery,
} from "@/lib/api/webhooks";
import { ApiError } from "@/lib/errors";

function canRedrive(status: string): boolean {
  const s = status.toUpperCase();
  return s === "FAILED" || s === "DEAD" || s === "ERROR";
}

export default function WebhooksPage() {
  const { tenantId, ready } = useTenant();
  const qc = useQueryClient();
  const [url, setUrl] = useState("https://example.com/hooks/ar");
  const [events, setEvents] = useState("*");

  const list = useQuery({
    queryKey: ["webhooks", tenantId],
    enabled: ready,
    queryFn: ({ signal }) => listWebhooks(tenantId, signal),
  });

  const deliveries = useQuery({
    queryKey: ["webhook-deliveries", tenantId],
    enabled: ready,
    queryFn: ({ signal }) => listWebhookDeliveries(tenantId, 50, signal),
    refetchInterval: 20000,
  });

  const createMut = useMutation({
    mutationFn: () =>
      createWebhook(tenantId, { url: url.trim(), eventTypes: events.trim() || "*" }),
    onSuccess: () => {
      toast.success("Webhook created");
      void qc.invalidateQueries({ queryKey: ["webhooks", tenantId] });
    },
    onError: (e: Error) => toast.error(e instanceof ApiError ? e.message : e.message),
  });

  const redriveMut = useMutation({
    mutationFn: (deliveryId: string) =>
      redriveWebhookDelivery(tenantId, deliveryId),
    onSuccess: () => {
      toast.success("Delivery redrive enqueued");
      void qc.invalidateQueries({ queryKey: ["webhook-deliveries", tenantId] });
    },
    onError: (e: Error) =>
      toast.error(e instanceof ApiError ? e.message : e.message),
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title="Webhooks"
        description="Customer-facing event callbacks beyond internal outbox/Kafka"
      />
      <Card className="space-y-3 p-4">
        <div>
          <Label htmlFor="url">Callback URL</Label>
          <Input id="url" value={url} onChange={(e) => setUrl(e.target.value)} />
        </div>
        <div>
          <Label htmlFor="events">Event types (* or comma-separated)</Label>
          <Input id="events" value={events} onChange={(e) => setEvents(e.target.value)} />
        </div>
        <Button onClick={() => createMut.mutate()} disabled={createMut.isPending || !url.trim()}>
          Add webhook
        </Button>
      </Card>
      <Card className="overflow-x-auto p-0">
        <table className="w-full text-sm">
          <thead className="border-b bg-zinc-50 text-left dark:bg-zinc-900">
            <tr>
              <th className="px-4 py-2">URL</th>
              <th className="px-4 py-2">Events</th>
              <th className="px-4 py-2">Status</th>
              <th className="px-4 py-2">Actions</th>
            </tr>
          </thead>
          <tbody>
            {(list.data ?? []).map((w) => (
              <tr key={w.id} className="border-b last:border-0">
                <td className="max-w-xs truncate px-4 py-2 font-mono text-xs">{w.url}</td>
                <td className="px-4 py-2">{w.eventTypes}</td>
                <td className="px-4 py-2">
                  <StatusBadge status={w.active ? "ACTIVE" : "INACTIVE"} />
                </td>
                <td className="space-x-2 px-4 py-2">
                  {w.active && (
                    <Button
                      variant="secondary"
                      onClick={() =>
                        deactivateWebhook(tenantId, w.id).then(() => {
                          toast.success("Deactivated");
                          void qc.invalidateQueries({ queryKey: ["webhooks"] });
                        })
                      }
                    >
                      Deactivate
                    </Button>
                  )}
                  <Button
                    variant="secondary"
                    onClick={() =>
                      deleteWebhook(tenantId, w.id).then(() => {
                        toast.success("Deleted");
                        void qc.invalidateQueries({ queryKey: ["webhooks"] });
                      })
                    }
                  >
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <Card className="overflow-x-auto p-0">
        <div className="border-b px-4 py-3 text-sm font-semibold">
          Recent deliveries
        </div>
        {deliveries.isLoading ? (
          <p className="p-4 text-sm text-zinc-500">Loading deliveries…</p>
        ) : deliveries.isError ? (
          <p className="p-4 text-sm text-rose-600">
            {(deliveries.error as Error)?.message ?? "Failed to load deliveries"}
          </p>
        ) : !(deliveries.data ?? []).length ? (
          <p className="p-4 text-sm text-zinc-500">
            No delivery attempts yet (or delivery log not available).
          </p>
        ) : (
          <table className="w-full text-sm">
            <thead className="border-b bg-zinc-50 text-left dark:bg-zinc-900">
              <tr>
                <th className="px-4 py-2">Created</th>
                <th className="px-4 py-2">Event</th>
                <th className="px-4 py-2">Status</th>
                <th className="px-4 py-2">HTTP</th>
                <th className="px-4 py-2">Attempts</th>
                <th className="px-4 py-2">URL</th>
                <th className="px-4 py-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              {(deliveries.data ?? []).map((d) => (
                <tr key={d.id} className="border-b last:border-0">
                  <td className="whitespace-nowrap px-4 py-2 font-mono text-xs">
                    {d.createdAt
                      ? new Date(d.createdAt).toLocaleString()
                      : "—"}
                  </td>
                  <td className="px-4 py-2">{d.eventType}</td>
                  <td className="px-4 py-2">
                    <StatusBadge status={d.status} />
                    {d.errorMessage ? (
                      <p className="mt-0.5 max-w-xs truncate text-xs text-rose-600">
                        {d.errorMessage}
                      </p>
                    ) : null}
                  </td>
                  <td className="px-4 py-2 tabular-nums">
                    {d.httpStatus ?? "—"}
                  </td>
                  <td className="px-4 py-2 tabular-nums">{d.attemptCount}</td>
                  <td className="max-w-[12rem] truncate px-4 py-2 font-mono text-xs">
                    {d.url}
                  </td>
                  <td className="px-4 py-2">
                    {canRedrive(d.status) ? (
                      <Button
                        type="button"
                        size="sm"
                        variant="secondary"
                        disabled={
                          redriveMut.isPending &&
                          redriveMut.variables === d.id
                        }
                        onClick={() => redriveMut.mutate(d.id)}
                      >
                        Redrive
                      </Button>
                    ) : (
                      "—"
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  );
}