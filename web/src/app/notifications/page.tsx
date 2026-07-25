"use client";

import Link from "next/link";
import { Fragment, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/status-badge";
import { TableSkeleton } from "@/components/ui/skeleton";
import { useTenant } from "@/components/tenant-provider";
import { useAuth } from "@/components/auth-provider";
import {
  listNotificationAttempts,
  listNotifications,
  type NotificationDto,
} from "@/lib/api/notifications";

export default function NotificationsPage() {
  const { tenantId, ready: tenantReady } = useTenant();
  const { ready: authReady, session } = useAuth();
  const ready = tenantReady && authReady && !!session;
  const [statusFilter, setStatusFilter] = useState("");
  const [eventFilter, setEventFilter] = useState("");
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const list = useQuery({
    queryKey: ["notifications", tenantId, statusFilter, eventFilter],
    enabled: ready,
    queryFn: ({ signal }) => listNotifications(tenantId, 100, signal),
    refetchInterval: 15000,
  });

  const attempts = useQuery({
    queryKey: ["notification-attempts", tenantId, expandedId],
    enabled: ready && !!expandedId,
    queryFn: ({ signal }) =>
      listNotificationAttempts(tenantId, expandedId!, signal),
  });

  const rows = useMemo(() => {
    let data = list.data ?? [];
    if (statusFilter) {
      data = data.filter((n) => n.status === statusFilter);
    }
    if (eventFilter) {
      data = data.filter((n) => n.eventType === eventFilter);
    }
    return data;
  }, [list.data, statusFilter, eventFilter]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Notifications"
        description="Customer email / WhatsApp delivery history (auto + manual)"
      />

      <Card className="flex flex-wrap gap-3 p-4">
        <label className="text-xs">
          Status
          <select
            className="ml-2 rounded border border-zinc-300 bg-white px-2 py-1 text-sm dark:border-zinc-700 dark:bg-zinc-950"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="">All</option>
            {["PENDING", "QUEUED", "SENDING", "SENT", "FAILED", "SKIPPED", "CANCELLED"].map(
              (s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ),
            )}
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
            {["INVOICE_ISSUED", "PAYMENT_REMINDER", "DUNNING_NOTICE"].map((s) => (
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
          <p className="p-4 text-sm text-rose-600">
            {(list.error as Error)?.message ?? "Failed to load notifications"}
          </p>
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
                        {n.errorMessage ? (
                          <p className="mt-0.5 max-w-xs truncate text-xs text-rose-600">
                            {n.errorMessage}
                          </p>
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
                            setExpandedId((cur) => (cur === n.id ? null : n.id))
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
                            <p className="text-xs text-zinc-500">Loading attempts…</p>
                          ) : attempts.isError ? (
                            <p className="text-xs text-rose-600">
                              {(attempts.error as Error).message}
                            </p>
                          ) : !(attempts.data ?? []).length ? (
                            <p className="text-xs text-zinc-500">No attempts yet</p>
                          ) : (
                            <ul className="space-y-1 text-xs font-mono">
                              {(attempts.data ?? []).map((a) => (
                                <li key={a.id}>
                                  #{a.attemptNumber} {a.status} {a.provider}{" "}
                                  {a.errorMessage ?? a.providerMessageId ?? ""}{" "}
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