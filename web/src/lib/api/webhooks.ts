import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import { ApiError } from "@/lib/errors";
import type { WebhookDto, WebhookDeliveryDto } from "@/types/ar";

export function listWebhooks(tenantId: string, signal?: AbortSignal) {
  return apiFetch<WebhookDto[]>(apiPaths.webhooks, { tenantId, signal });
}

export function createWebhook(
  tenantId: string,
  body: { url: string; secret?: string; eventTypes?: string },
) {
  return apiFetch<WebhookDto>(apiPaths.webhooks, { method: "POST", tenantId, body });
}

export function deactivateWebhook(tenantId: string, id: string) {
  return apiFetch<WebhookDto>(apiPaths.webhook(id) + "/deactivate", {
    method: "POST",
    tenantId,
  });
}

export function deleteWebhook(tenantId: string, id: string) {
  return apiFetch<void>(apiPaths.webhook(id), { method: "DELETE", tenantId });
}

/**
 * List recent webhook delivery attempts. Returns empty array when endpoint
 * is unavailable (404/501) so the page degrades gracefully.
 */
export async function listWebhookDeliveries(
  tenantId: string,
  limit = 50,
  signal?: AbortSignal,
): Promise<WebhookDeliveryDto[]> {
  try {
    return await apiFetch<WebhookDeliveryDto[]>(apiPaths.webhookDeliveries, {
      tenantId,
      signal,
      query: { limit },
    });
  } catch (e) {
    if (e instanceof ApiError && (e.status === 404 || e.status === 501)) {
      return [];
    }
    throw e;
  }
}

/** Redrive a failed/dead webhook delivery (PP-035 / PP-026). */
export function redriveWebhookDelivery(tenantId: string, deliveryId: string) {
  return apiFetch<WebhookDeliveryDto | void>(
    apiPaths.webhookDeliveryRedrive(deliveryId),
    { method: "POST", tenantId },
  );
}