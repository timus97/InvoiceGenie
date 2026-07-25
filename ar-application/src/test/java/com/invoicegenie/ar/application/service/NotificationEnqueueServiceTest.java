package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.notification.Notification;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationEventType;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicy;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicyRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationPreference;
import com.invoicegenie.ar.domain.model.notification.NotificationPreferenceRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationSkipReason;
import com.invoicegenie.ar.domain.model.notification.NotificationStatus;
import com.invoicegenie.ar.domain.model.notification.NotificationTemplate;
import com.invoicegenie.ar.domain.model.notification.NotificationTemplateRepository;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("NotificationEnqueueService")
@ExtendWith(MockitoExtension.class)
class NotificationEnqueueServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationPolicyRepository policyRepository;
    @Mock NotificationPreferenceRepository preferenceRepository;
    @Mock NotificationTemplateRepository templateRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock CustomerRepository customerRepository;

    NotificationEnqueueService service;
    TenantId tenantId;
    CustomerId customerId;
    InvoiceId invoiceId;
    Invoice invoice;
    Customer customer;

    @BeforeEach
    void setUp() {
        service = new NotificationEnqueueService(notificationRepository, policyRepository, preferenceRepository,
                templateRepository, invoiceRepository, customerRepository, true, 5);
        tenantId = TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        customerId = CustomerId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        invoiceId = InvoiceId.of(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        invoice = new Invoice(invoiceId, "INV-100", customerId, "Acme", "USD",
                LocalDate.now(), LocalDate.now().plusDays(30), List.of());
        invoice.addLine(new InvoiceLine(1, "Service", Money.of("100.00", "USD")));
        invoice.issue();
        customer = new Customer(customerId, "C-1", "Acme Corp", "USD");
        customer.updateContact("billing@acme.test", "+15551212", null);
    }

    @Test
    @DisplayName("skips when customer opted out")
    void skipOptOut() {
        when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));
        when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));
        when(policyRepository.findByTenant(tenantId)).thenReturn(Optional.of(NotificationPolicy.defaults(tenantId)));
        when(notificationRepository.findByIdempotencyKey(eq(tenantId), anyString())).thenReturn(Optional.empty());
        NotificationPreference optedOut = NotificationPreference.create(
                tenantId, customerId, NotificationChannel.EMAIL, false, null);
        when(preferenceRepository.findByCustomerAndChannel(tenantId, customerId, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(optedOut));

        List<Notification> result = service.enqueueForInvoice(tenantId, invoiceId,
                NotificationEventType.INVOICE_ISSUED, List.of(NotificationChannel.EMAIL),
                null, null, false, true);

        assertEquals(1, result.size());
        assertEquals(NotificationStatus.SKIPPED, result.get(0).getStatus());
        assertEquals(NotificationSkipReason.OPTED_OUT, result.get(0).getSkipReason());
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        assertEquals(NotificationSkipReason.OPTED_OUT, cap.getValue().getSkipReason());
        verify(templateRepository, never()).findActive(any(), any(), any(), any());
    }

    @Test
    @DisplayName("idempotency returns existing without double-save race path")
    void idempotencyReturnsExisting() {
        when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));
        when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));
        when(policyRepository.findByTenant(tenantId)).thenReturn(Optional.of(NotificationPolicy.defaults(tenantId)));

        Notification existing = Notification.enqueue(tenantId, customerId, invoiceId,
                NotificationEventType.INVOICE_ISSUED, NotificationChannel.EMAIL,
                "notify:INVOICE_ISSUED:33333333-3333-3333-3333-333333333333:EMAIL",
                "billing@acme.test", "subj", "body", UUID.randomUUID(), null, 5);
        when(notificationRepository.findByIdempotencyKey(eq(tenantId), anyString()))
                .thenReturn(Optional.of(existing));

        List<Notification> result = service.enqueueForInvoice(tenantId, invoiceId,
                NotificationEventType.INVOICE_ISSUED, List.of(NotificationChannel.EMAIL),
                null, null, false, true);

        assertEquals(1, result.size());
        assertEquals(existing.getId(), result.get(0).getId());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("enqueues PENDING when policy, prefs, template, destination ok")
    void enqueueSuccess() {
        when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));
        when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));
        when(policyRepository.findByTenant(tenantId)).thenReturn(Optional.of(NotificationPolicy.defaults(tenantId)));
        when(notificationRepository.findByIdempotencyKey(eq(tenantId), anyString())).thenReturn(Optional.empty());
        when(preferenceRepository.findByCustomerAndChannel(tenantId, customerId, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());
        NotificationTemplate tmpl = new NotificationTemplate(
                UUID.randomUUID(), null, NotificationEventType.INVOICE_ISSUED, NotificationChannel.EMAIL,
                "en", "Invoice {{invoiceNumber}}", "Hi {{customerName}} total {{total}}", null, true,
                Instant.now(), Instant.now());
        when(templateRepository.findActive(tenantId, NotificationEventType.INVOICE_ISSUED,
                NotificationChannel.EMAIL, "en")).thenReturn(Optional.of(tmpl));

        List<Notification> result = service.enqueueForInvoice(tenantId, invoiceId,
                NotificationEventType.INVOICE_ISSUED, List.of(NotificationChannel.EMAIL),
                null, null, false, true);

        assertEquals(1, result.size());
        assertEquals(NotificationStatus.PENDING, result.get(0).getStatus());
        assertEquals("billing@acme.test", result.get(0).getDestination());
        assertTrue(result.get(0).getSubject().contains("INV-100"));
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("SKIPPED NO_DESTINATION then add email re-enqueues PENDING (QA-001)")
    void skippedRecoverableAllowsResend() {
        when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));
        when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));
        when(policyRepository.findByTenant(tenantId)).thenReturn(Optional.of(NotificationPolicy.defaults(tenantId)));
        when(preferenceRepository.findByCustomerAndChannel(tenantId, customerId, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());

        Notification skipped = Notification.skipped(tenantId, customerId, invoiceId,
                NotificationEventType.INVOICE_ISSUED, NotificationChannel.EMAIL,
                "notify:INVOICE_ISSUED:33333333-3333-3333-3333-333333333333:EMAIL",
                NotificationSkipReason.NO_DESTINATION, null);
        when(notificationRepository.findByIdempotencyKey(eq(tenantId), anyString()))
                .thenReturn(Optional.of(skipped))
                .thenReturn(Optional.empty());
        NotificationTemplate tmpl = new NotificationTemplate(
                UUID.randomUUID(), null, NotificationEventType.INVOICE_ISSUED, NotificationChannel.EMAIL,
                "en", "Invoice {{invoiceNumber}}", "Hi {{customerName}}", null, true,
                Instant.now(), Instant.now());
        when(templateRepository.findActive(tenantId, NotificationEventType.INVOICE_ISSUED,
                NotificationChannel.EMAIL, "en")).thenReturn(Optional.of(tmpl));

        List<Notification> result = service.enqueueForInvoice(tenantId, invoiceId,
                NotificationEventType.INVOICE_ISSUED, List.of(NotificationChannel.EMAIL),
                null, null, false, true);

        assertEquals(1, result.size());
        assertEquals(NotificationStatus.PENDING, result.get(0).getStatus());
        verify(notificationRepository).delete(eq(tenantId), eq(skipped.getId()));
        verify(notificationRepository, atLeastOnce()).save(any(Notification.class));
    }

    @Test
    @DisplayName("force does not bypass policy.enabled (QA-002)")
    void forceDoesNotBypassPolicyDisabled() {
        when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));
        when(customerRepository.findByTenantAndId(tenantId, customerId)).thenReturn(Optional.of(customer));
        NotificationPolicy disabled = NotificationPolicy.defaults(tenantId);
        // defaults may be enabled — force-disable via update if available; use mock
        NotificationPolicy policy = mock(NotificationPolicy.class);
        when(policy.isEnabled()).thenReturn(false);
        when(policyRepository.findByTenant(tenantId)).thenReturn(Optional.of(policy));
        when(notificationRepository.findByIdempotencyKey(eq(tenantId), anyString())).thenReturn(Optional.empty());

        List<Notification> result = service.enqueueForInvoice(tenantId, invoiceId,
                NotificationEventType.INVOICE_ISSUED, List.of(NotificationChannel.EMAIL),
                null, null, true, false);

        assertEquals(1, result.size());
        assertEquals(NotificationStatus.SKIPPED, result.get(0).getStatus());
        assertEquals(NotificationSkipReason.POLICY_DISABLED, result.get(0).getSkipReason());
    }

    @Test
    @DisplayName("missing invoice throws INVOICE_NOT_FOUND (QA-005)")
    void missingInvoiceThrows() {
        when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.empty());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.enqueueForInvoice(tenantId, invoiceId, NotificationEventType.INVOICE_ISSUED,
                        List.of(NotificationChannel.EMAIL), null, null, false, true));
        assertEquals("INVOICE_NOT_FOUND", ex.getMessage());
    }
}
