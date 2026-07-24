import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import type {
  CreateCustomerRequest,
  CreditCheckDto,
  CustomerDto,
  CustomerStatsDto,
  UpdateCustomerRequest,
} from "@/types/ar";

export function listCustomers(
  tenantId: string,
  opts?: {
    status?: string;
    search?: string;
    includeDeleted?: boolean;
    signal?: AbortSignal;
  },
) {
  return apiFetch<CustomerDto[]>(apiPaths.customers, {
    tenantId,
    signal: opts?.signal,
    query: {
      status: opts?.status,
      search: opts?.search,
      includeDeleted: opts?.includeDeleted ? true : undefined,
    },
  });
}

export function getCustomer(tenantId: string, id: string, signal?: AbortSignal) {
  return apiFetch<CustomerDto>(apiPaths.customer(id), { tenantId, signal });
}

export function createCustomer(tenantId: string, body: CreateCustomerRequest) {
  return apiFetch<CustomerDto>(apiPaths.customers, {
    method: "POST",
    tenantId,
    body,
  });
}

export function updateCustomer(
  tenantId: string,
  id: string,
  body: UpdateCustomerRequest,
) {
  return apiFetch<CustomerDto>(apiPaths.customer(id), {
    method: "PUT",
    tenantId,
    body,
  });
}

export function blockCustomer(tenantId: string, id: string) {
  return apiFetch<CustomerDto>(apiPaths.customerBlock(id), {
    method: "POST",
    tenantId,
  });
}

export function unblockCustomer(tenantId: string, id: string) {
  return apiFetch<CustomerDto>(apiPaths.customerUnblock(id), {
    method: "POST",
    tenantId,
  });
}

export function deleteCustomer(tenantId: string, id: string) {
  return apiFetch<CustomerDto>(apiPaths.customer(id), {
    method: "DELETE",
    tenantId,
  });
}

export function creditCheck(
  tenantId: string,
  id: string,
  invoiceAmount: number,
  outstanding?: number,
) {
  return apiFetch<CreditCheckDto>(apiPaths.customerCreditCheck(id), {
    tenantId,
    query: {
      invoiceAmount,
      // omit outstanding to use system AR (STORY-014)
      outstanding: outstanding != null && outstanding > 0 ? outstanding : undefined,
    },
  });
}

export type CustomerArSummaryDto = {
  customerId: string;
  openInvoiceCount: number;
  byCurrency: {
    currency: string;
    openCount: number;
    totalBilled: number | string;
    totalPaid: number | string;
    balance: number | string;
  }[];
  totalBalance: number | string;
  baseCurrency: string;
};

export function getCustomerArSummary(
  tenantId: string,
  id: string,
  signal?: AbortSignal,
) {
  return apiFetch<CustomerArSummaryDto>(apiPaths.customerArSummary(id), {
    tenantId,
    signal,
  });
}

export function customerStats(tenantId: string, signal?: AbortSignal) {
  return apiFetch<CustomerStatsDto>(apiPaths.customerStats, {
    tenantId,
    signal,
  });
}