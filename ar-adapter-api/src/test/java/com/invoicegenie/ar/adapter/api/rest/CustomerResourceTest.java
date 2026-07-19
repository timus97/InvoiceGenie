package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.CustomerUseCase;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.service.CustomerService;
import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CustomerResource")
@ExtendWith(MockitoExtension.class)
class CustomerResourceTest {

    @Mock private CustomerUseCase customerUseCase;
    private CustomerResource resource;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        resource = new CustomerResource(customerUseCase);
        tenantId = TenantId.of(UUID.randomUUID());
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Customer sample(CustomerId id) {
        return new Customer(id, "C1", "Acme", "USD");
    }

    @Test
    void getFound() {
        CustomerId id = CustomerId.of(UUID.randomUUID());
        when(customerUseCase.get(tenantId, id)).thenReturn(Optional.of(sample(id)));
        assertEquals(200, resource.getCustomer(id.getValue().toString()).getStatus());
    }

    @Test
    void getMissing() {
        CustomerId id = CustomerId.of(UUID.randomUUID());
        when(customerUseCase.get(tenantId, id)).thenReturn(Optional.empty());
        assertEquals(404, resource.getCustomer(id.getValue().toString()).getStatus());
    }

    @Test
    void createOk() {
        CustomerId id = CustomerId.of(UUID.randomUUID());
        when(customerUseCase.create(eq(tenantId), eq("C1"), eq("Acme"), eq("USD")))
                .thenReturn(new CustomerService.CreateResult(sample(id), true, "ok"));
        assertEquals(201, resource.createCustomer(new CustomerResource.CreateCustomerDto("C1", "Acme", "USD")).getStatus());
    }

    @Test
    void createFail() {
        when(customerUseCase.create(eq(tenantId), any(), any(), any()))
                .thenReturn(new CustomerService.CreateResult(null, false, "duplicate"));
        assertEquals(400, resource.createCustomer(new CustomerResource.CreateCustomerDto("C1", "Acme", "USD")).getStatus());
    }

    @Test
    void listOk() {
        when(customerUseCase.list(eq(tenantId), isNull(), isNull(), eq(false)))
                .thenReturn(CustomerUseCase.ListResult.ok(List.of(sample(CustomerId.of(UUID.randomUUID())))));
        assertEquals(200, resource.listCustomers(null, null, false).getStatus());
    }

    @Test
    void blockOk() {
        CustomerId id = CustomerId.of(UUID.randomUUID());
        when(customerUseCase.block(tenantId, id))
                .thenReturn(new CustomerService.StatusResult(sample(id), true, "ok"));
        assertEquals(200, resource.blockCustomer(id.getValue().toString()).getStatus());
    }

    @Test
    void unblockOk() {
        CustomerId id = CustomerId.of(UUID.randomUUID());
        when(customerUseCase.unblock(tenantId, id))
                .thenReturn(new CustomerService.StatusResult(sample(id), true, "ok"));
        assertEquals(200, resource.unblockCustomer(id.getValue().toString()).getStatus());
    }

    @Test
    void deleteOk() {
        CustomerId id = CustomerId.of(UUID.randomUUID());
        when(customerUseCase.delete(tenantId, id))
                .thenReturn(new CustomerService.StatusResult(sample(id), true, "ok"));
        assertEquals(200, resource.deleteCustomer(id.getValue().toString()).getStatus());
    }

    @Test
    void creditCheckRequiresAmount() {
        assertEquals(400, resource.checkCredit(UUID.randomUUID().toString(), BigDecimal.ZERO, null).getStatus());
    }

    @Test
    void creditCheckOk() {
        CustomerId id = CustomerId.of(UUID.randomUUID());
        when(customerUseCase.checkCredit(eq(tenantId), eq(id), any(), any()))
                .thenReturn(new CustomerService.CreditCheckResult(true, new BigDecimal("1000"), "ok"));
        assertEquals(200, resource.checkCredit(id.getValue().toString(), BigDecimal.ZERO, new BigDecimal("100")).getStatus());
    }
}