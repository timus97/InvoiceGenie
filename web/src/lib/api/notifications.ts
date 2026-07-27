import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import { ApiError } from "@/lib/errors";
import { logClientError } from "@/lib/log-client-error";
import type {
  NotificationDto,
  NotificationPageDto,
  NotificationAttemptDto,
  NotificationPreferenceDto,
  NotificationPolicyDto,
  NotificationMetricsDto,
  NotificationMetricBucketDto,
  NotificationTemplatePreviewRequest,
  NotificationTemplatePreviewDto,
  SendNotificationRequest,
} from "@/types/ar";

export type {
  NotificationDto,
  NotificationPageDto,
  NotificationAttemptDto,
  NotificationPreferenceDto,
  NotificationPolicyDto,
  NotificationMetricsDto,
  NotificationTemplatePreviewRequest,
  NotificationTemplatePreviewDto,
  SendNotificationRequest,
};

/** Unwrap list response: page envelope `{ items }` (current) or bare array (legacy). */
export function unwrapNotificationList(
  data: NotificationPageDto | NotificationDto[] | null | undefined,
): NotificationDto[] {
  if (!data) return [];
  if (Array.isArray(data)) return data;
  if (Array.isArray(data.items)) return data.items;
  return [];
}

export async function listNotifications(
  tenantId: string,
  limit = 100,
  signal?: AbortSignal,
): Promise<NotificationDto[]> {
  const q = new URLSearchParams({ limit: String(limit) });
  const data = await apiFetch<NotificationPageDto | NotificationDto[]>(
    `${apiPaths.notifications}?${q.toString()}`,
    { tenantId, signal },
  );
  return unwrapNotificationList(data);
}

export function getNotification(
  tenantId: string,
  id: string,
  signal?: AbortSignal,
) {
  return apiFetch<NotificationDto>(apiPaths.notification(id), {
    tenantId,
    signal,
  });
}

export function listNotificationAttempts(
  tenantId: string,
  id: string,
  signal?: AbortSignal,
) {
  return apiFetch<NotificationAttemptDto[]>(
    apiPaths.notificationAttempts(id),
    { tenantId, signal },
  );
}

export function listInvoiceNotifications(
  tenantId: string,
  invoiceId: string,
  limit = 50,
  signal?: AbortSignal,
) {
  const q = new URLSearchParams({ limit: String(limit) });
  return apiFetch<NotificationDto[]>(
    `${apiPaths.invoiceNotifications(invoiceId)}?${q.toString()}`,
    { tenantId, signal },
  );
}

export function sendNotification(
  tenantId: string,
  body: SendNotificationRequest,
) {
  return apiFetch<NotificationDto[]>(apiPaths.notificationsSend, {
    method: "POST",
    tenantId,
    body,
  });
}

export function getCustomerNotificationPreferences(
  tenantId: string,
  customerId: string,
  signal?: AbortSignal,
) {
  return apiFetch<NotificationPreferenceDto[]>(
    apiPaths.customerNotificationPreferences(customerId),
    { tenantId, signal },
  );
}

export function putCustomerNotificationPreferences(
  tenantId: string,
  customerId: string,
  body: NotificationPreferenceDto[],
) {
  return apiFetch<NotificationPreferenceDto[]>(
    apiPaths.customerNotificationPreferences(customerId),
    { method: "PUT", tenantId, body },
  );
}

export function getNotificationPolicy(tenantId: string, signal?: AbortSignal) {
  return apiFetch<NotificationPolicyDto>(apiPaths.notificationPolicy, {
    tenantId,
    signal,
  });
}

export function putNotificationPolicy(
  tenantId: string,
  body: NotificationPolicyDto,
) {
  return apiFetch<NotificationPolicyDto>(apiPaths.notificationPolicy, {
    method: "PUT",
    tenantId,
    body,
  });
}

/**
 * Notification delivery metrics.
 * Returns null on missing endpoint or soft failures (logged); rethrows aborts only.
 */
export async function getNotificationMetrics(
  tenantId: string,
  signal?: AbortSignal,
): Promise<NotificationMetricsDto | null> {
  try {
    return await apiFetch<NotificationMetricsDto>(apiPaths.notificationMetrics, {
      tenantId,
      signal,
    });
  } catch (e) {
    if (
      (e instanceof DOMException && e.name === "AbortError") ||
      (e instanceof Error && e.name === "AbortError")
    ) {
      throw e;
    }
    logClientError("getNotificationMetrics", e);
    // Soft-fail: UI shows zeros instead of an error banner
    return null;
  }
}

/**
 * Render a template with sample variables (no send).
 * Returns null if API missing or failed (logged); rethrows only invalid-request handling upstream.
 */
export async function previewNotificationTemplate(
  tenantId: string,
  body: NotificationTemplatePreviewRequest,
): Promise<NotificationTemplatePreviewDto | null> {
  try {
    return await apiFetch<NotificationTemplatePreviewDto>(
      apiPaths.notificationTemplatePreview,
      { method: "POST", tenantId, body },
    );
  } catch (e) {
    if (
      (e instanceof DOMException && e.name === "AbortError") ||
      (e instanceof Error && e.name === "AbortError")
    ) {
      throw e;
    }
    if (e instanceof ApiError && (e.status === 404 || e.status === 501)) {
      return null;
    }
    logClientError("previewNotificationTemplate", e);
    return null;
  }
}

function bucketMap(
  byStatus: NotificationMetricsDto["byStatus"] | undefined,
): Record<string, number> {
  if (!byStatus) return {};
  if (Array.isArray(byStatus)) {
    const out: Record<string, number> = {};
    for (const b of byStatus as NotificationMetricBucketDto[]) {
      if (b && typeof b.key === "string") {
        out[b.key.toUpperCase()] = Number(b.count) || 0;
      }
    }
    return out;
  }
  const out: Record<string, number> = {};
  for (const [k, v] of Object.entries(byStatus)) {
    if (typeof v === "number") out[k.toUpperCase()] = v;
  }
  return out;
}

/** Normalize metrics DTO into SENT/FAILED/PENDING/SKIPPED counts. */
export function normalizeMetricsCounts(m: NotificationMetricsDto | null | undefined): {
  SENT: number;
  FAILED: number;
  PENDING: number;
  SKIPPED: number;
} {
  if (!m) {
    return { SENT: 0, FAILED: 0, PENDING: 0, SKIPPED: 0 };
  }
  try {
    const by = {
      ...bucketMap(m.last24h as NotificationMetricsDto["byStatus"]),
      ...bucketMap(m.byStatus),
    };
    const pick = (key: string, alt?: number) => {
      const upper = key.toUpperCase();
      const fromBy = by[upper];
      if (typeof fromBy === "number") return fromBy;
      if (typeof alt === "number") return alt;
      const rec = m as Record<string, unknown>;
      const v = rec[upper] ?? rec[key.toLowerCase()];
      return typeof v === "number" ? v : 0;
    };
    return {
      SENT: pick("SENT", m.sent ?? m.SENT),
      FAILED: pick("FAILED", m.failed ?? m.FAILED),
      PENDING: pick("PENDING", m.pending ?? m.PENDING),
      SKIPPED: pick("SKIPPED", m.skipped ?? m.SKIPPED),
    };
  } catch (e) {
    logClientError("normalizeMetricsCounts", e, { metrics: m });
    return { SENT: 0, FAILED: 0, PENDING: 0, SKIPPED: 0 };
  }
}

/**
 * Public unsubscribe (no tenant session). Uses same-origin BFF public path.
 */
export async function publicUnsubscribe(token: string): Promise<void> {
  const q = new URLSearchParams({ token });
  const res = await fetch(`${apiPaths.notificationUnsubscribe}?${q.toString()}`, {
    method: "POST",
    headers: { Accept: "application/json" },
    cache: "no-store",
    credentials: "omit",
  });
  if (!res.ok) {
    const { parseApiError } = await import("@/lib/errors");
    throw await parseApiError(res);
  }
}
