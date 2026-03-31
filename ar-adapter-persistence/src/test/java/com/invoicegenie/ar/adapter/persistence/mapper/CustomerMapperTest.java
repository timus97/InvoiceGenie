package com.invoicegenie.ar.adapter.persistence.mapper;

import com.invoicegenie.ar.adapter.persistence.entity.CustomerEntity;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerStatus;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerMapperTest {

    @Test
    void mapsCustomerToEntityAndBack() {
        TenantId tenantId = TenantId.of(UUID.randomUUID());
        Customer customer = new Customer(
                CustomerId.of(UUID.randomUUID()),
                "CUST-100",
                "Acme Corp",
                "Acme",
                "billing@acme.com",
                "123-456",
                "{\"line1\":\"Main\"}",
                "USD",
                new BigDecimal("10000.00"),
                "NET30",
                "TAX-999",
                CustomerStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                1L
        );

        CustomerMapper mapper = new CustomerMapper();
        CustomerEntity entity = mapper.toEntity(tenantId, customer);
        Customer restored = mapper.toDomain(entity);

        assertEquals(customer.getCustomerCode(), restored.getCustomerCode());
        assertEquals(customer.getCurrency(), restored.getCurrency());
        assertEquals(customer.getStatus(), restored.getStatus());
        assertEquals(customer.getCreditLimit(), restored.getCreditLimit());
    }
}
