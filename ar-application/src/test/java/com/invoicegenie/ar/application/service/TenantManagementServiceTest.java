package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.TenantUseCase;
import com.invoicegenie.ar.domain.model.ledger.ChartOfAccountsRepository;
import com.invoicegenie.ar.domain.model.tenant.Tenant;
import com.invoicegenie.ar.domain.model.tenant.TenantRepository;
import com.invoicegenie.ar.domain.model.tenant.TenantStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TenantManagementService")
@ExtendWith(MockitoExtension.class)
class TenantManagementServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private ChartOfAccountsRepository chartOfAccountsRepository;

    private TenantManagementService service;

    @BeforeEach
    void setUp() {
        service = new TenantManagementService(tenantRepository, chartOfAccountsRepository);
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("creates tenant and seeds COA")
        void createSeeds() {
            when(tenantRepository.existsByCode("acme")).thenReturn(false);
            when(chartOfAccountsRepository.seedSystemAccounts(any())).thenReturn(5);

            Tenant t = service.create(new TenantUseCase.CreateTenantCommand("acme", "Acme Inc", "USD", null));

            assertEquals("ACME", t.getCode());
            assertEquals(TenantStatus.ACTIVE, t.getStatus());
            assertEquals("{}", t.getSettingsJson());
            verify(tenantRepository).save(any(Tenant.class));
            verify(chartOfAccountsRepository).seedSystemAccounts(t.getId());
        }

        @Test
        @DisplayName("create without COA repo still works")
        void createNoCoa() {
            service = new TenantManagementService(tenantRepository);
            when(tenantRepository.existsByCode("x")).thenReturn(false);

            Tenant t = service.create(new TenantUseCase.CreateTenantCommand("x", "X Co", null, "{\"a\":1}"));

            assertEquals("USD", t.getBaseCurrency());
            assertEquals("{\"a\":1}", t.getSettingsJson());
        }

        @Test
        @DisplayName("duplicate code rejected")
        void duplicate() {
            when(tenantRepository.existsByCode("dup")).thenReturn(true);
            assertThrows(IllegalArgumentException.class, () ->
                    service.create(new TenantUseCase.CreateTenantCommand("dup", "Dup", "USD", null)));
        }

        @Test
        @DisplayName("COA seed failure does not fail create")
        void coaFail() {
            when(tenantRepository.existsByCode("z")).thenReturn(false);
            when(chartOfAccountsRepository.seedSystemAccounts(any())).thenThrow(new RuntimeException("no table"));

            assertDoesNotThrow(() ->
                    service.create(new TenantUseCase.CreateTenantCommand("z", "Z Co", "EUR", "{}")));
            verify(tenantRepository).save(any());
        }
    }

    @Nested
    @DisplayName("get / list / update / lifecycle")
    class Crud {
        @Test
        @DisplayName("get by id and code")
        void get() {
            Tenant t = new Tenant(UUID.randomUUID(), "A", "Name", "USD");
            when(tenantRepository.findById(t.getId())).thenReturn(Optional.of(t));
            when(tenantRepository.findByCode("A")).thenReturn(Optional.of(t));

            assertTrue(service.get(t.getId()).isPresent());
            assertTrue(service.getByCode("A").isPresent());
        }

        @Test
        @DisplayName("list all and by status")
        void list() {
            when(tenantRepository.findAll()).thenReturn(List.of());
            when(tenantRepository.findByStatus(TenantStatus.ACTIVE)).thenReturn(List.of());

            assertTrue(service.list(null).isEmpty());
            assertTrue(service.list("  ").isEmpty());
            assertTrue(service.list("active").isEmpty());
            assertThrows(IllegalArgumentException.class, () -> service.list("NOPE"));
        }

        @Test
        @DisplayName("update renames and sets currency/settings")
        void update() {
            Tenant t = new Tenant(UUID.randomUUID(), "B", "Old", "USD");
            when(tenantRepository.findById(t.getId())).thenReturn(Optional.of(t));

            var result = service.update(t.getId(),
                    new TenantUseCase.UpdateTenantCommand("New Name", "EUR", "{\"x\":1}"));

            assertTrue(result.isPresent());
            assertEquals("New Name", result.get().getName());
            assertEquals("EUR", result.get().getBaseCurrency());
            verify(tenantRepository).save(t);
        }

        @Test
        @DisplayName("update empty when missing")
        void updateMissing() {
            when(tenantRepository.findById(any())).thenReturn(Optional.empty());
            assertTrue(service.update(UUID.randomUUID(),
                    new TenantUseCase.UpdateTenantCommand("N", null, null)).isEmpty());
        }

        @Test
        @DisplayName("activate and suspend")
        void activateSuspend() {
            Tenant t = new Tenant(UUID.randomUUID(), "C", "C Co", "USD");
            t.suspend();
            when(tenantRepository.findById(t.getId())).thenReturn(Optional.of(t));

            assertTrue(service.activate(t.getId()).isPresent());
            assertEquals(TenantStatus.ACTIVE, t.getStatus());

            assertTrue(service.suspend(t.getId()).isPresent());
            assertEquals(TenantStatus.SUSPENDED, t.getStatus());
        }
    }
}