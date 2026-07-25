import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import type {
  NotificationDto,
  NotificationAttemptDto,
  NotificationPreferenceDto,
  NotificationPolicyDto,
  SendNotificationRequest,
} from "@/types/ar";

export function listNotifications(
  tenantId: string,
  limit = 100,
  signal?: AbortSignal,
) {
  const q = new URLSearchParams({ limit: String(limit) });
  return apiFetch<NotificationDto[]>(
    `${apiPaths.notifications}?${q.toString()}`,
    { tenantId, signal },
  );
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
