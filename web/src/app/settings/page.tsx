"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { useTenant } from "@/components/tenant-provider";
import { useAuth } from "@/components/auth-provider";
import { checkBackendHealth } from "@/lib/api/client";
import {
  getNotificationPolicy,
  putNotificationPolicy,
} from "@/lib/api/notifications";
import { DEFAULT_TENANT_ID, isValidTenantId } from "@/lib/tenant";
import { isAuthRequired } from "@/lib/auth-session";
import { ApiError } from "@/lib/errors";
import type { NotificationPolicyDto } from "@/types/ar";

export default function SettingsPage() {
  const { tenantId, setTenantId, ready } = useTenant();
  const { session, logout, hasRole } = useAuth();
  const queryClient = useQueryClient();
  const [value, setValue] = useState(tenantId);
  const allowOverride =
    process.env.NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE === "true";
  const isTenantAdmin = hasRole("TENANT_ADMIN");

  const health = useQuery({
    queryKey: ["backend-health"],
    queryFn: checkBackendHealth,
    refetchInterval: 15000,
  });

  const policyQ = useQuery({
    queryKey: ["notification-policy", tenantId],
    enabled: ready && isTenantAdmin,
    queryFn: ({ signal }) => getNotificationPolicy(tenantId, signal),
  });

  const [policy, setPolicy] = useState<NotificationPolicyDto | null>(null);
  useEffect(() => {
    if (policyQ.data) setPolicy(policyQ.data);
  }, [policyQ.data]);

  const policyMut = useMutation({
    mutationFn: () => {
      if (!policy) throw new Error("No policy loaded");
      return putNotificationPolicy(tenantId, policy);
    },
    onSuccess: (saved) => {
      setPolicy(saved);
      toast.success("Notification policy saved");
      void queryClient.invalidateQueries({
        queryKey: ["notification-policy", tenantId],
      });
    },
    onError: (e: Error) =>
      toast.error(e instanceof ApiError ? e.message : e.message),
  });

  const onSave = () => {
    if (!allowOverride) {
      toast.error("Tenant override is disabled");
      return;
    }
    if (!isValidTenantId(value)) {
      toast.error("Enter a valid UUID for X-Tenant-Id");
      return;
    }
    const ok = setTenantId(value);
    if (ok) toast.success("Tenant updated — caches cleared");
  };

  return (
    <div>
      <PageHeader
        title="Settings"
        description={
          session
            ? "Session tenant is bound from login. Tokens stay in httpOnly cookies and are attached only by the server-side BFF."
            : "Sign in required. Credentials never appear as Authorization headers in the browser network panel."
        }
      />

      <div className="grid max-w-3xl gap-6">
        <Card>
          <h2 className="mb-2 text-sm font-semibold">Session</h2>
          {session ? (
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between gap-4">
                <dt className="text-zinc-500">Subject</dt>
                <dd className="font-mono text-xs">{session.subject}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-zinc-500">Method</dt>
                <dd>{session.method}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-zinc-500">Roles</dt>
                <dd className="font-mono text-xs">
                  {session.roles.join(", ") || "—"}
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-zinc-500">Tenant</dt>
                <dd className="font-mono text-xs">{session.tenantId}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-zinc-500">Credential storage</dt>
                <dd className="text-xs text-emerald-700 dark:text-emerald-400">
                  Server-side session; browser only holds opaque httpOnly ig_sid
                </dd>
              </div>
            </dl>
          ) : (
            <p className="text-sm text-zinc-500">
              Not signed in.{" "}
              <Link
                href="/login"
                className="text-indigo-600 hover:underline dark:text-indigo-400"
              >
                Sign in
              </Link>
              .
            </p>
          )}
          <div className="mt-4 flex gap-2">
            {session ? (
              <Button
                type="button"
                variant="secondary"
                onClick={() => void logout()}
              >
                Sign out
              </Button>
            ) : (
              <Link href="/login">
                <Button type="button">Sign in</Button>
              </Link>
            )}
          </div>
        </Card>

        {allowOverride ? (
          <Card>
            <Label htmlFor="tenant">Active tenant ID</Label>
            <Input
              id="tenant"
              className="mt-2 font-mono text-sm"
              value={value}
              onChange={(e) => setValue(e.target.value)}
              spellCheck={false}
            />
            <p className="mt-2 text-xs text-zinc-500">
              Default smoke tenant:{" "}
              <button
                type="button"
                className="font-mono text-indigo-600 hover:underline dark:text-indigo-400"
                onClick={() => setValue(DEFAULT_TENANT_ID)}
              >
                {DEFAULT_TENANT_ID}
              </button>
            </p>
            <div className="mt-4 flex gap-2">
              <Button type="button" onClick={onSave}>
                Save tenant
              </Button>
            </div>
            <p className="mt-3 text-xs text-amber-700 dark:text-amber-300">
              Dev/demo only. Prefer login-bound tenant in secured environments.
            </p>
          </Card>
        ) : (
          <Card>
            <h2 className="mb-2 text-sm font-semibold">Active tenant ID</h2>
            <code className="block rounded-md bg-zinc-100 px-2 py-1 font-mono text-xs dark:bg-zinc-900">
              {session?.tenantId ?? tenantId}
            </code>
            <p className="mt-3 text-xs text-amber-700 dark:text-amber-300">
              Tenant override is disabled. Tenant is bound from authentication
              only.
            </p>
          </Card>
        )}

        <Card>
          <h2 className="mb-2 text-sm font-semibold">Backend health</h2>
          <p className="text-sm">
            Status:{" "}
            {health.isLoading ? (
              "Checking..."
            ) : health.data?.ok ? (
              <span className="font-medium text-emerald-600">
                OK ({health.data.status})
              </span>
            ) : (
              <span className="font-medium text-rose-600">
                Down ({health.data?.status ?? 0}) {health.data?.detail}
              </span>
            )}
          </p>
          <p className="mt-2 text-xs text-zinc-500">
            Browser calls same-origin <code className="font-mono">/api/v1/*</code>{" "}
            (BFF + cookies) and <code className="font-mono">/q/*</code> (health
            rewrite).
          </p>
          <Button
            type="button"
            variant="secondary"
            className="mt-3"
            onClick={() => health.refetch()}
          >
            Recheck
          </Button>
        </Card>

        {isTenantAdmin ? (
          <Card>
            <h2 className="mb-2 text-sm font-semibold">
              Notification policy
            </h2>
            <p className="mb-3 text-xs text-zinc-500">
              Tenant defaults for auto invoice send, pre-due reminders, and
              dunning notices. Channels are comma-separated (EMAIL, WHATSAPP).
            </p>
            {policyQ.isLoading || !policy ? (
              <p className="text-sm text-zinc-500">Loading policy…</p>
            ) : (
              <div className="grid gap-3 text-sm">
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={policy.enabled}
                    onChange={(e) =>
                      setPolicy({ ...policy, enabled: e.target.checked })
                    }
                  />
                  Notifications enabled
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={policy.emailEnabled}
                    onChange={(e) =>
                      setPolicy({ ...policy, emailEnabled: e.target.checked })
                    }
                  />
                  Email channel
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={policy.whatsappEnabled}
                    onChange={(e) =>
                      setPolicy({
                        ...policy,
                        whatsappEnabled: e.target.checked,
                      })
                    }
                  />
                  WhatsApp channel
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={policy.autoSendOnIssue}
                    onChange={(e) =>
                      setPolicy({
                        ...policy,
                        autoSendOnIssue: e.target.checked,
                      })
                    }
                  />
                  Auto-send on invoice issue
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={policy.preDueReminderEnabled}
                    onChange={(e) =>
                      setPolicy({
                        ...policy,
                        preDueReminderEnabled: e.target.checked,
                      })
                    }
                  />
                  Pre-due payment reminders
                </label>
                <div>
                  <Label htmlFor="preDueDays">Pre-due days</Label>
                  <Input
                    id="preDueDays"
                    type="number"
                    min={0}
                    max={90}
                    value={policy.preDueDays}
                    onChange={(e) =>
                      setPolicy({
                        ...policy,
                        preDueDays: Number(e.target.value) || 0,
                      })
                    }
                  />
                </div>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={policy.dunningNoticeEnabled}
                    onChange={(e) =>
                      setPolicy({
                        ...policy,
                        dunningNoticeEnabled: e.target.checked,
                      })
                    }
                  />
                  Dunning notices
                </label>
                <div>
                  <Label htmlFor="chIssue">Channels: invoice issued</Label>
                  <Input
                    id="chIssue"
                    value={policy.channelsInvoiceIssued}
                    onChange={(e) =>
                      setPolicy({
                        ...policy,
                        channelsInvoiceIssued: e.target.value,
                      })
                    }
                  />
                </div>
                <div>
                  <Label htmlFor="chRem">Channels: payment reminder</Label>
                  <Input
                    id="chRem"
                    value={policy.channelsPaymentReminder}
                    onChange={(e) =>
                      setPolicy({
                        ...policy,
                        channelsPaymentReminder: e.target.value,
                      })
                    }
                  />
                </div>
                <div>
                  <Label htmlFor="chDun">Channels: dunning notice</Label>
                  <Input
                    id="chDun"
                    value={policy.channelsDunningNotice}
                    onChange={(e) =>
                      setPolicy({
                        ...policy,
                        channelsDunningNotice: e.target.value,
                      })
                    }
                  />
                </div>
                <Button
                  type="button"
                  disabled={policyMut.isPending}
                  onClick={() => policyMut.mutate()}
                >
                  {policyMut.isPending ? "Saving…" : "Save notification policy"}
                </Button>
              </div>
            )}
          </Card>
        ) : null}

        <Card>
          <h2 className="mb-2 text-sm font-semibold">Environment</h2>
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between gap-4">
              <dt className="text-zinc-500">App name</dt>
              <dd className="font-medium">
                {process.env.NEXT_PUBLIC_APP_NAME ?? "InvoiceGenie AR"}
              </dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-zinc-500">Default tenant</dt>
              <dd className="font-mono text-xs">{DEFAULT_TENANT_ID}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-zinc-500">Tenant override</dt>
              <dd>{allowOverride ? "enabled" : "disabled"}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-zinc-500">Auth required</dt>
              <dd>{isAuthRequired() ? "yes" : "no"}</dd>
            </div>
          </dl>
          <p className="mt-4 text-xs text-zinc-500">
            Signing out clears React Query caches so rows never leak across
            sessions.
          </p>
        </Card>
      </div>
    </div>
  );
}
