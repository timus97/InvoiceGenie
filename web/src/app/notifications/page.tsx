"use client";

import Link from "next/link";
import { Fragment, useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/input";
import { StatusBadge } from "@/components/ui/status-badge";
import { TableSkeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { useTenant } from "@/components/tenant-provider";
import { useAuth } from "@/components/auth-provider";
import {
  logClientError,
  USER_ACTION_FAILED,
  USER_LOAD_FAILED,
} from "@/lib/log-client-error";
import {
  getNotificationMetrics,
  listNotificationAttempts,
  listNotifications,
  normalizeMetricsCounts,
  previewNotificationTemplate,
  type NotificationDto,
} from "@/lib/api/notifications";

const METRIC_KEYS = ["SENT", "FAILED", "PENDING", "SKIPPED"] as const;

export default function NotificationsPage() {
  const { tenantId, ready: tenantReady } = useTenant();
  const { ready: authReady, session } = useAuth();
  const ready = tenantReady && authReady && !!session;
  const [statusFilter, setStatusFilter] = useState("");
  const [eventFilter, setEventFilter] = useState("");
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const [previewEvent, setPreviewEvent] = useState("INVOICE_ISSUED");
  const [previewChannel, setPreviewChannel] = useState("EMAIL");
  const [previewVars, setPreviewVars] = useState(
    '{\n  "customerName": "Acme Corp",\n  "invoiceNumber": "INV-1001",\n  "amount": "1,250.00",\n  "dueDate": "2026-08-15"\n}',
  );
  const [previewResult, setPreviewResult] = useState<{
    subject?: string | null;
    body?: string | null;
  } | null>(null);
  const [previewNote, setPreviewNote] = useState<string | null>(null);

  const list = useQuery({
    queryKey: ["notifications", tenantId, statusFilter, eventFilter],
    enabled: ready,
    queryFn: ({ signal }) => listNotifications(tenantId, 100, signal),
    refetchInterval: 15000,
  });

  const metricsQ = useQuery({
    queryKey: ["notification-metrics", tenantId],
    enabled: ready,
    queryFn: ({ signal }) => getNotificationMetrics(tenantId, signal),
    refetchInterval: 30000,
  });

  // Log load failures; never surface technical messages in the UI.
  useEffect(() => {
    if (list.isError) {
      logClientError("NotificationsPage.list", list.error);
    }
  }, [list.isError, list.error]);

  useEffect(() => {
    if (metricsQ.isError) {
      logClientError("NotificationsPage.metrics", metricsQ.error);
    }
  }, [metricsQ.isError, metricsQ.error]);

  const metrics = normalizeMetricsCounts(
    metricsQ.isError ? null : (metricsQ.data ?? null),
  );

  const attempts = useQuery({
    queryKey: ["notification-attempts", tenantId, expandedId],
    enabled: ready && !!expandedId,
    queryFn: ({ signal }) =>
      listNotificationAttempts(tenantId, expandedId!, signal),
  });

  useEffect(() => {
    if (attempts.isError) {
      logClientError("NotificationsPage.attempts", attempts.error, {
        notificationId: expandedId,
      });
    }
  }, [attempts.isError, attempts.error, expandedId]);

  const previewMut = useMutation({
    mutationFn: async () => {
      let variables: Record<string, string> = {};
      try {
        const parsed = JSON.parse(previewVars) as unknown;
        if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
          variables = Object.fromEntries(
            Object.entries(parsed as Record<string, unknown>).map(([k, v]) => [
              k,
              String(v ?? ""),
            ]),
          );
        } else {
          throw new Error("INVALID_VARS_SHAPE");
        }
      } catch (e) {
        logClientError("NotificationsPage.previewVars", e);
        throw new Error("INVALID_VARS");
      }
      return previewNotificationTemplate(tenantId, {
        eventType: previewEvent,
        channel: previewChannel,
        variables,
      });
    },
    onSuccess: (data) => {
      if (data === null) {
        setPreviewResult(null);
        setPreviewNote(
          "Preview is temporarily unavailable. You can still send notifications from invoice detail.",
        );
        return;
      }
      setPreviewNote(null);
      setPreviewResult(data);
    },
    onError: (e: Error) => {
      logClientError("NotificationsPage.preview", e);
      setPreviewResult(null);
      if (e.message === "INVALID_VARS") {
        setPreviewNote(
          "Sample variables must be valid JSON object text. Check the format and try again.",
        );
      } else {
        setPreviewNote(USER_ACTION_FAILED);
      }
    },
  });

  const rows = useMemo(() => {
    const data = Array.isArray(list.data) ? list.data : [];
    let filtered = data;
    if (statusFilter) {
      filtered = filtered.filter((n) => n.status === statusFilter);
    }
    if (eventFilter) {
      filtered = filtered.filter((n) => n.eventType === eventFilter);
    }
    return filtered;
  }, [list.data, statusFilter, eventFilter]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Notifications"
        description="Customer email / WhatsApp delivery history (auto + manual)"
      />

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {METRIC_KEYS.map((key) => (
          <Card key={key} className="p-4">
            <div className="text-xs font-medium uppercase tracking-wide text-zinc-500">
              {key}
            </div>
            <div className="mt-1 text-2xl font-semibold tabular-nums">
              {metricsQ.isLoading ? "…" : metrics[key]}
            </div>
          </Card>
        ))}
      </div>

      <Card className="space-y-3 p-4">
        <h2 className="text-sm font-semibold">Template preview</h2>
        <p className="text-xs text-zinc-500">
          Render subject/body with sample variables (no send).
        </p>
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <Label htmlFor="preview-event">Event type</Label>
            <select
              id="preview-event"
              className="mt-1 w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-950"
              value={previewEvent}
              onChange={(e) => setPreviewEvent(e.target.value)}
            >
              {[
                "INVOICE_ISSUED",
                "PAYMENT_REMINDER",
                "DUNNING_NOTICE",
                "STATEMENT_SEND",
              ].map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
          <div>
            <Label htmlFor="preview-channel">Channel</Label>
            <select
              id="preview-channel"
              className="mt-1 w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-950"
              value={previewChannel}
              onChange={(e) => setPreviewChannel(e.target.value)}
            >
              {["EMAIL", "WHATSAPP"].map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div>
          <Label htmlFor="preview-vars">Sample variables (JSON)</Label>
          <textarea
            id="preview-vars"
            className="mt-1 w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 font-mono text-xs dark:border-zinc-700 dark:bg-zinc-950"
            rows={5}
            value={previewVars}
            onChange={(e) => setPreviewVars(e.target.value)}
            spellCheck={false}
          />
        </div>
        <Button
          type="button"
          variant="secondary"
          disabled={previewMut.isPending || !ready}
          onClick={() => previewMut.mutate()}
        >
          {previewMut.isPending ? "Rendering…" : "Preview template"}
        </Button>
        {previewNote ? (
          <p className="text-xs text-zinc-500">{previewNote}</p>
        ) : null}
        {previewResult ? (
          <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-3 text-sm dark:border-zinc-800 dark:bg-zinc-900/40">
            <div className="text-xs uppercase text-zinc-500">Subject</div>
            <div className="mt-0.5 font-medium">
              {previewResult.subject ?? "—"}
            </div>
            <div className="mt-3 text-xs uppercase text-zinc-500">Body</div>
            <pre className="mt-0.5 whitespace-pre-wrap font-mono text-xs">
              {previewResult.body ?? "—"}
            </pre>
          </div>
        ) : null}
      </Card>

      <Card className="flex flex-wrap gap-3 p-4">
        <label className="text-xs">
          Status
          <select
            className="ml-2 rounded border border-zinc-300 bg-white px-2 py-1 text-sm dark:border-zinc-700 dark:bg-zinc-950"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="">All</option>
            {[
              "PENDING",
              "QUEUED",
              "SENDING",
              "SENT",
              "FAILED",
              "SKIPPED",
              "CANCELLED",
            ].map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        <label className="text-xs">
          Event
          <select
            className="ml-2 rounded border border-zinc-300 bg-white px-2 py-1 text-sm dark:border-zinc-700 dark:bg-zinc-950"
            value={eventFilter}
            onChange={(e) => setEventFilter(e.target.value)}
          >
            <option value="">All</option>
            {[
              "INVOICE_ISSUED",
              "PAYMENT_REMINDER",
              "DUNNING_NOTICE",
              "STATEMENT_SEND",
            ].map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
      </Card>

      <Card className="overflow-x-auto p-0">
        {list.isLoading ? (
          <div className="p-4">
            <TableSkeleton rows={6} />
          </div>
        ) : list.isError ? (
          <div className="p-6">
            <EmptyState
              title="Notifications unavailable"
              description={USER_LOAD_FAILED}
              action={
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => void list.refetch()}
                >
                  Retry
                </Button>
              }
            />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="border-b bg-zinc-50 text-left dark:bg-zinc-900">
              <tr>
                <th className="px-4 py-2">Created</th>
                <th className="px-4 py-2">Event</th>
                <th className="px-4 py-2">Channel</th>
                <th className="px-4 py-2">Status</th>
                <th className="px-4 py-2">Destination</th>
                <th className="px-4 py-2">Subject</th>
                <th className="px-4 py-2">Invoice</th>
                <th className="px-4 py-2">Attempts</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <tr>
                  <td
                    colSpan={8}
                    className="px-4 py-8 text-center text-zinc-500"
                  >
                    No notifications yet. Issue an invoice or use Send on an
                    invoice detail page.
                  </td>
                </tr>
              ) : (
                rows.map((n: NotificationDto) => (
                  <Fragment key={n.id}>
                    <tr className="border-b last:border-0">
                      <td className="whitespace-nowrap px-4 py-2 font-mono text-xs">
                        {n.createdAt
                          ? new Date(n.createdAt).toLocaleString()
                          : "—"}
                      </td>
                      <td className="px-4 py-2">{n.eventType}</td>
                      <td className="px-4 py-2">{n.channel}</td>
                      <td className="px-4 py-2">
                        <StatusBadge status={String(n.status)} />
                        {n.skipReason ? (
                          <span className="ml-1 text-xs text-zinc-500">
                            ({n.skipReason})
                          </span>
                        ) : null}
                      </td>
                      <td className="max-w-[10rem] truncate px-4 py-2 font-mono text-xs">
                        {n.destination ?? "—"}
                      </td>
                      <td className="max-w-[12rem] truncate px-4 py-2">
                        {n.subject ?? "—"}
                      </td>
                      <td className="px-4 py-2">
                        {n.invoiceId ? (
                          <Link
                            href={`/invoices/${n.invoiceId}`}
                            className="text-indigo-600 hover:underline dark:text-indigo-400"
                          >
                            open
                          </Link>
                        ) : (
                          "—"
                        )}
                      </td>
                      <td className="px-4 py-2">
                        <Button
                          type="button"
                          size="sm"
                          variant="secondary"
                          onClick={() =>
                            setExpandedId((cur) =>
                              cur === n.id ? null : n.id,
                            )
                          }
                        >
                          {expandedId === n.id ? "Hide" : "Show"}
                        </Button>
                      </td>
                    </tr>
                    {expandedId === n.id ? (
                      <tr className="bg-zinc-50 dark:bg-zinc-900/40">
                        <td colSpan={8} className="px-4 py-3">
                          {attempts.isLoading ? (
                            <p className="text-xs text-zinc-500">
                              Loading attempts…
                            </p>
                          ) : attempts.isError ? (
                            <p className="text-xs text-zinc-500">
                              {USER_LOAD_FAILED}
                            </p>
                          ) : !(attempts.data ?? []).length ? (
                            <p className="text-xs text-zinc-500">
                              No attempts yet
                            </p>
                          ) : (
                            <ul className="space-y-1 text-xs font-mono">
                              {(attempts.data ?? []).map((a) => (
                                <li key={a.id}>
                                  #{a.attemptNumber} {a.status}{" "}
                                  {a.provider ?? ""}{" "}
                                  {a.providerMessageId
                                    ? `msg=${a.providerMessageId}`
                                    : ""}{" "}
                                  {a.attemptedAt
                                    ? new Date(a.attemptedAt).toLocaleString()
                                    : ""}
                                </li>
                              ))}
                            </ul>
                          )}
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
                ))
              )}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  );
}
