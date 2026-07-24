import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import type { WebhookDto } from "@/types/ar";

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