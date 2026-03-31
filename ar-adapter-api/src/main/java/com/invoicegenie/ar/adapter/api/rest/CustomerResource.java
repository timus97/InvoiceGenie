package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.customer.CustomerStatus;
import com.invoicegenie.ar.domain.service.CustomerService;
import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST adapter: Customer management operations.
 */
@Path("/api/v1/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Customers", description = "Customer management")
public class CustomerResource {

    private final CustomerRepository customerRepository;
    private final CustomerService customerService;

    public CustomerResource(CustomerRepository customerRepository, CustomerService customerService) {
        this.customerRepository = customerRepository;
        this.customerService = customerService;
    }

    @POST
    @Operation(summary = "Create a new customer")
    public Response createCustomer(CreateCustomerDto dto) {
        var tenantId = TenantContext.getCurrentTenant();
        
        var result = customerService.createCustomer(
                tenantId, 
                customerRepository,
                dto.customerCode(),
                dto.legalName(),
                dto.currency() != null ? dto.currency() : "USD"
        );
        
        if (result.success()) {
            return Response.status(201).entity(toDto(result.customer())).build();
        } else {
            return Response.status(400).entity(new ErrorDto("VALIDATION_ERROR", result.message())).build();
        }
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get customer by ID")
    public Response getCustomer(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        var customerId = CustomerId.of(UUID.fromString(id));
        
        return customerRepository.findByTenantAndId(tenantId, customerId)
                .map(customer -> Response.ok(toDto(customer)).build())
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Customer not found")).build());
    }

    @GET
    @Operation(summary = "List customers with optional filters")
    public Response listCustomers(
            @QueryParam("status") String status,
            @QueryParam("search") String search,
            @QueryParam("includeDeleted") @DefaultValue("false") boolean includeDeleted) {
        
        var tenantId = TenantContext.getCurrentTenant();
        List<Customer> customers;
        
        if (search != null && !search.isBlank()) {
            customers = customerService.searchCustomers(tenantId, customerRepository, search);
        } else if (status != null) {
            try {
                CustomerStatus customerStatus = CustomerStatus.valueOf(status.toUpperCase());
                customers = customerRepository.findByTenantAndStatus(tenantId, customerStatus);
            } catch (IllegalArgumentException e) {
                return Response.status(400).entity(new ErrorDto("INVALID_STATUS", "Unknown status: " + status)).build();
            }
        } else {
            customers = customerRepository.findAllByTenant(tenantId, includeDeleted);
        }
        
        return Response.ok(customers.stream().map(this::toDto).collect(Collectors.toList())).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update customer details")
    public Response updateCustomer(@PathParam("id") String id, UpdateCustomerDto dto) {
        var tenantId = TenantContext.getCurrentTenant();
        var customerId = CustomerId.of(UUID.fromString(id));
        
        return customerRepository.findByTenantAndId(tenantId, customerId)
                .map(customer -> {
                    try {
                        // Update fields
                        if (dto.displayName() != null) customer.updateDisplayName(dto.displayName());
                        if (dto.email() != null || dto.phone() != null || dto.billingAddress() != null) {
                            customer.updateContact(dto.email(), dto.phone(), dto.billingAddress());
                        }
                        if (dto.creditLimit() != null) customer.setCreditLimit(dto.creditLimit());
                        if (dto.paymentTerms() != null) customer.setPaymentTerms(dto.paymentTerms());
                        
                        customerRepository.save(tenantId, customer);
                        return Response.ok(toDto(customer)).build();
                    } catch (Exception e) {
                        return Response.status(400).entity(new ErrorDto("VALIDATION_ERROR", e.getMessage())).build();
                    }
                })
                .orElse(Response.status(404).entity(new ErrorDto("NOT_FOUND", "Customer not found")).build());
    }

    @POST
    @Path("/{id}/block")
    @Operation(summary = "Block a customer (prevent new invoices)")
    public Response blockCustomer(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        var customerId = CustomerId.of(UUID.fromString(id));
        
        var result = customerService.blockCustomer(tenantId, customerRepository, customerId);
        
        if (result.success()) {
            return Response.ok(toDto(result.customer())).build();
        } else {
            return Response.status(400).entity(new ErrorDto("BLOCK_FAILED", result.message())).build();
        }
    }

    @POST
    @Path("/{id}/unblock")
    @Operation(summary = "Unblock a customer (allow invoicing)")
    public Response unblockCustomer(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        var customerId = CustomerId.of(UUID.fromString(id));
        
        var result = customerService.unblockCustomer(tenantId, customerRepository, customerId);
        
        if (result.success()) {
            return Response.ok(toDto(result.customer())).build();
        } else {
            return Response.status(400).entity(new ErrorDto("UNBLOCK_FAILED", result.message())).build();
        }
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a customer (soft delete)")
    public Response deleteCustomer(@PathParam("id") String id) {
        var tenantId = TenantContext.getCurrentTenant();
        var customerId = CustomerId.of(UUID.fromString(id));
        
        var result = customerService.deleteCustomer(tenantId, customerRepository, customerId);
        
        if (result.success()) {
            return Response.ok(toDto(result.customer())).build();
        } else {
            return Response.status(400).entity(new ErrorDto("DELETE_FAILED", result.message())).build();
        }
    }

    @GET
    @Path("/{id}/credit-check")
    @Operation(summary = "Check if customer can be invoiced for an amount")
    public Response checkCredit(
            @PathParam("id") String id,
            @QueryParam("outstanding") @DefaultValue("0") BigDecimal outstanding,
            @QueryParam("invoiceAmount") BigDecimal invoiceAmount) {
        
        var tenantId = TenantContext.getCurrentTenant();
        var customerId = CustomerId.of(UUID.fromString(id));
        
        if (invoiceAmount == null) {
            return Response.status(400).entity(new ErrorDto("MISSING_PARAM", "invoiceAmount is required")).build();
        }
        
        var result = customerService.checkCreditLimit(tenantId, customerRepository, customerId, outstanding, invoiceAmount);
        
        return Response.ok(new CreditCheckDto(
                result.canInvoice(),
                result.availableCredit(),
                result.message()
        )).build();
    }

    @GET
    @Path("/stats")
    @Operation(summary = "Get customer statistics")
    public Response getStats() {
        var tenantId = TenantContext.getCurrentTenant();
        
        long active = customerRepository.countByTenantAndStatus(tenantId, CustomerStatus.ACTIVE);
        long blocked = customerRepository.countByTenantAndStatus(tenantId, CustomerStatus.BLOCKED);
        long deleted = customerRepository.countByTenantAndStatus(tenantId, CustomerStatus.DELETED);
        
        return Response.ok(new CustomerStatsDto(active, blocked, deleted)).build();
    }

    // DTOs
    private CustomerDto toDto(Customer customer) {
        return new CustomerDto(
                customer.getId().getValue().toString(),
                customer.getCustomerCode(),
                customer.getLegalName(),
                customer.getDisplayName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getBillingAddress(),
                customer.getCurrency(),
                customer.getCreditLimit(),
                customer.getPaymentTerms(),
                customer.getTaxId(),
                customer.getStatus().name(),
                customer.getCreatedAt().toString(),
                customer.getUpdatedAt().toString(),
                customer.getVersion()
        );
    }

    public record CreateCustomerDto(String customerCode, String legalName, String currency) {}
    
    public record UpdateCustomerDto(String displayName, String email, String phone, 
            String billingAddress, BigDecimal creditLimit, String paymentTerms) {}
    
    public record CustomerDto(String id, String customerCode, String legalName, String displayName,
            String email, String phone, String billingAddress, String currency,
            BigDecimal creditLimit, String paymentTerms, String taxId, String status,
            String createdAt, String updatedAt, long version) {}
    
    public record CreditCheckDto(boolean canInvoice, BigDecimal availableCredit, String message) {}
    
    public record CustomerStatsDto(long active, long blocked, long deleted) {}
    
    public record ErrorDto(String code, String message) {}
}
