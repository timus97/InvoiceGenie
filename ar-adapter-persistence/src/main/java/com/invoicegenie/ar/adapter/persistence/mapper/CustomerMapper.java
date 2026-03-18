package com.invoicegenie.ar.adapter.persistence.mapper;

import com.invoicegenie.ar.adapter.persistence.entity.CustomerEntity;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerStatus;
import com.invoicegenie.shared.domain.TenantId;

/**
 * Maps Customer aggregate ↔ JPA entity.
 */
public final class CustomerMapper {

    public CustomerEntity toEntity(TenantId tenantId, Customer customer) {
        CustomerEntity e = new CustomerEntity();
        e.setId(customer.getId().getValue());
        e.setTenantId(tenantId.getValue());
        e.setCustomerCode(customer.getCustomerCode());
        e.setLegalName(customer.getLegalName());
        e.setDisplayName(customer.getDisplayName());
        e.setEmail(customer.getEmail());
        e.setPhone(customer.getPhone());
        e.setBillingAddress(customer.getBillingAddress());
        e.setCurrency(customer.getCurrency());
        e.setCreditLimit(customer.getCreditLimit());
        e.setPaymentTerms(customer.getPaymentTerms());
        e.setTaxId(customer.getTaxId());
        e.setStatus(customer.getStatus());
        e.setCreatedAt(customer.getCreatedAt());
        e.setUpdatedAt(customer.getUpdatedAt());
        e.setVersion(customer.getVersion());
        return e;
    }

    public Customer toDomain(CustomerEntity e) {
        return new Customer(
                CustomerId.of(e.getId()),
                e.getCustomerCode(),
                e.getLegalName(),
                e.getDisplayName(),
                e.getEmail(),
                e.getPhone(),
                e.getBillingAddress(),
                e.getCurrency(),
                e.getCreditLimit(),
                e.getPaymentTerms(),
                e.getTaxId(),
                e.getStatus() == null ? CustomerStatus.ACTIVE : e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion()
        );
    }
}
