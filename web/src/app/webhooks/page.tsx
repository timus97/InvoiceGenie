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
  listWebhooks,
} from "@/lib/api/webhooks";
import { ApiError } from "@/lib/errors";

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

  const createMut = useMutation({
    mutationFn: () =>
      createWebhook(tenantId, { url: url.trim(), eventTypes: events.trim() || "*" }),
    onSuccess: () => {
      toast.success("Webhook created");
      void qc.invalidateQueries({ queryKey: ["webhooks", tenantId] });
    },
    onError: (e: Error) => toast.error(e instanceof ApiError ? e.message : e.message),
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
    </div>
  );
}