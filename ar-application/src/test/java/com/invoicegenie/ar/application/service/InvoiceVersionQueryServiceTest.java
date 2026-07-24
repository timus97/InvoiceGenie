package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceVersion;
import com.invoicegenie.ar.domain.model.invoice.InvoiceVersionRepository;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("InvoiceVersionQueryService")
@ExtendWith(MockitoExtension.class)
class InvoiceVersionQueryServiceTest {

    @Mock private InvoiceVersionRepository invoiceVersionRepository;

    private InvoiceVersionQueryService service;
    private TenantId tenantId;
    private InvoiceId invoiceId;

    @BeforeEach
    void setUp() {
        service = new InvoiceVersionQueryService(invoiceVersionRepository);
        tenantId = TenantId.of(UUID.randomUUID());
        invoiceId = InvoiceId.generate();
    }

    @Test
    @DisplayName("list delegates to repository")
    void list() {
        when(invoiceVersionRepository.findByInvoice(tenantId, invoiceId)).thenReturn(List.of());
        assertTrue(service.list(tenantId, invoiceId).isEmpty());
        verify(invoiceVersionRepository).findByInvoice(tenantId, invoiceId);
    }

    @Test
    @DisplayName("get delegates to repository")
    void get() {
        when(invoiceVersionRepository.findByInvoiceAndVersion(tenantId, invoiceId, 2L))
                .thenReturn(Optional.empty());
        assertTrue(service.get(tenantId, invoiceId, 2L).isEmpty());
        verify(invoiceVersionRepository).findByInvoiceAndVersion(tenantId, invoiceId, 2L);
    }
}