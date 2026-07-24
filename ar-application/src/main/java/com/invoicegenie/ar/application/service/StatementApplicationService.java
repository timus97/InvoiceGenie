package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.StatementUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.domain.event.StatementGenerated;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Application service: customer open-item statements (STORY-015).
 */
public class StatementApplicationService implements StatementUseCase {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final EventPublisher eventPublisher;

    public StatementApplicationService(CustomerRepository customerRepository,
                                       InvoiceRepository invoiceRepository,
                                       EventPublisher eventPublisher) {
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<CustomerStatement> generate(TenantId tenantId, CustomerId customerId, LocalDate asOf) {
        Optional<Customer> customerOpt = customerRepository.findByTenantAndId(tenantId, customerId);
        if (customerOpt.isEmpty()) {
            return Optional.empty();
        }
        Customer customer = customerOpt.get();
        LocalDate asOfDate = asOf != null ? asOf : LocalDate.now();

        List<Invoice> open = invoiceRepository.findOpenByTenantAndCustomer(tenantId, customerId);
        List<OpenItem> items = new ArrayList<>();
        BigDecimal totalBalance = BigDecimal.ZERO;
        String currency = customer.getCurrency() != null ? customer.getCurrency() : "USD";

        for (Invoice inv : open) {
            // as-of: include invoices issued on or before asOf with remaining balance
            if (inv.getIssueDate() != null && inv.getIssueDate().isAfter(asOfDate)) {
                continue;
            }
            BigDecimal balance = inv.getBalanceDue().getAmount();
            if (balance.signum() <= 0) {
                continue;
            }
            int daysPastDue = 0;
            if (inv.getDueDate() != null && inv.getDueDate().isBefore(asOfDate)) {
                daysPastDue = (int) ChronoUnit.DAYS.between(inv.getDueDate(), asOfDate);
            }
            items.add(new OpenItem(
                    inv.getId().getValue().toString(),
                    inv.getInvoiceNumber(),
                    inv.getIssueDate(),
                    inv.getDueDate(),
                    daysPastDue,
                    inv.getTotal().getAmount(),
                    inv.getAmountPaid() != null ? inv.getAmountPaid().getAmount() : BigDecimal.ZERO,
                    balance,
                    inv.getCurrencyCode(),
                    inv.getStatus().name()
            ));
            totalBalance = totalBalance.add(balance);
            currency = inv.getCurrencyCode();
        }

        items.sort(Comparator.comparing(OpenItem::dueDate, Comparator.nullsLast(Comparator.naturalOrder())));

        CustomerStatement statement = new CustomerStatement(
                customerId.getValue().toString(),
                customer.getCustomerCode(),
                customer.getLegalName(),
                asOfDate,
                items,
                totalBalance,
                currency
        );

        eventPublisher.publish(new StatementGenerated(
                tenantId, customerId, asOfDate, items.size(),
                totalBalance.toPlainString(), currency));

        return Optional.of(statement);
    }

    @Override
    public String toCsv(CustomerStatement statement) {
        StringBuilder sb = new StringBuilder();
        sb.append("invoiceNumber,invoiceId,issueDate,dueDate,daysPastDue,total,amountPaid,balanceDue,currency,status\n");
        for (OpenItem item : statement.openItems()) {
            sb.append(csv(item.invoiceNumber())).append(',')
                    .append(csv(item.invoiceId())).append(',')
                    .append(item.issueDate()).append(',')
                    .append(item.dueDate()).append(',')
                    .append(item.daysPastDue()).append(',')
                    .append(item.total().toPlainString()).append(',')
                    .append(item.amountPaid().toPlainString()).append(',')
                    .append(item.balanceDue().toPlainString()).append(',')
                    .append(csv(item.currency())).append(',')
                    .append(csv(item.status())).append('\n');
        }
        return sb.toString();
    }

    private static String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}