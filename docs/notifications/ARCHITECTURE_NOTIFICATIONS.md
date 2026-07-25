# Architecture: Customer Notifications (Email + WhatsApp)

See Product Owner doc: PRODUCT_OWNER_NOTIFICATIONS.md

## Principle
Extend existing hexagonal modules — no new deployable. Pattern after WebhookDispatcher + DunningJob + OutboxWorker.

## Modules
- Domain: ar-domain/.../model/notification/
- Application: NotificationUseCase, EnqueueService, Preference/Policy use cases
- Persistence: entities + Flyway V11
- Messaging: NotificationDispatchWorker, PaymentReminderJob, NotificationOutboxBridge
- API: NotificationResource, preference/policy endpoints
- Web: /notifications, invoice send, customer prefs, settings policy

## Tables (V11)
ar_notification_template, ar_notification_preference, ar_notification_policy,
ar_notification, ar_notification_attempt (+ RLS, indexes, seeds)

## Status
PENDING/QUEUED → SENDING → SENT | FAILED | SKIPPED | CANCELLED

## Idempotency
notify:{eventType}:{invoiceId}:{channel}[:qualifier]
- INVOICE_ISSUED: no qualifier
- PAYMENT_REMINDER: due:yyyy-MM-dd
- DUNNING_NOTICE: L{level}

## Channels
Email: logging (default) | smtp
WhatsApp: logging | meta Cloud API templates

## REST
POST /api/v1/notifications/send
GET /api/v1/notifications
GET /api/v1/notifications/{id}
GET/PUT /api/v1/customers/{id}/notification-preferences
GET/PUT /api/v1/notification-policy

## Implementation order
1. V11 + domain + persistence
2. Enqueue + templates + prefs/policy
3. Logging adapters + dispatch worker
4. Outbox bridge + reminder job + dunning enqueue
5. REST + RBAC
6. Next.js UI
7. SMTP/Meta behind flags + config

## Critical existing files
- WebhookDispatcher.java (retry pattern)
- OutboxWorker.java (bridge InvoiceIssued)
- DunningJob.java (tenant loop)
- ArApplication.java (CDI)
- WebhookResource.java (REST pattern)