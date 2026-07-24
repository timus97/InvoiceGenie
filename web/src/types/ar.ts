/** Hand-written DTOs aligned with Quarkus REST adapters (regenerate from OpenAPI later). */

export type CustomerStatus = "ACTIVE" | "BLOCKED" | "DELETED";

export type CustomerDto = {
  id: string;
  customerCode: string;
  legalName: string;
  displayName?: string | null;
  email?: string | null;
  phone?: string | null;
  billingAddress?: string | null;
  currency: string;
  creditLimit?: number | string | null;
  paymentTerms?: string | null;
  taxId?: string | null;
  status: CustomerStatus;
  createdAt?: string | null;
  updatedAt?: string | null;
  version?: number | null;
};

export type CreateCustomerRequest = {
  customerCode: string;
  legalName: string;
  currency?: string;
};

export type UpdateCustomerRequest = {
  displayName?: string | null;
  email?: string | null;
  phone?: string | null;
  billingAddress?: string | null;
  creditLimit?: number | null;
  paymentTerms?: string | null;
};

export type CreditCheckDto = {
  canInvoice: boolean;
  availableCredit: number | string | null;
  message: string;
};

export type CustomerStatsDto = {
  active: number;
  blocked: number;
  deleted: number;
};

export type InvoiceStatus =
  | "DRAFT"
  | "ISSUED"
  | "PARTIALLY_PAID"
  | "PAID"
  | "OVERDUE"
  | "WRITTEN_OFF";

export type InvoiceLineDto = {
  sequence?: number;
  description: string;
  amount: number | string;
};

export type InvoiceDto = {
  id: string;
  invoiceNumber: string;
  customerId: string;
  customerRef?: string | null;
  currencyCode: string;
  issueDate?: string | null;
  dueDate?: string | null;
  status: InvoiceStatus | string;
  total?: number | string | null;
  issuedAt?: string | null;
  writtenOffAt?: string | null;
  version?: number | null;
  lines?: InvoiceLineDto[];
};

export type InvoicePageDto = {
  items: InvoiceDto[];
  nextCursor?: string | null;
  total?: number;
};

export type CreateInvoiceRequest = {
  invoiceNumber: string;
  customerId: string;
  customerRef?: string;
  currencyCode?: string;
  dueDate?: string;
  lines: { sequence: number; description: string; amount: number }[];
  issueImmediately?: boolean;
};

export type InvoiceIdDto = { id: string };

export type PaymentMethod =
  | "BANK_TRANSFER"
  | "CARD"
  | "CASH"
  | "CHECK"
  | "OTHER";

export type CreatePaymentRequest = {
  paymentNumber: string;
  customerId: string;
  amount: number;
  currencyCode?: string;
  paymentDate?: string;
  method: PaymentMethod;
  reference?: string;
  notes?: string;
};

export type PaymentCreatedDto = {
  id: string;
  paymentNumber: string;
};

export type PaymentStatus = "RECEIVED" | "REVERSED" | "REFUNDED" | string;

export type PaymentDto = {
  id: string;
  paymentNumber: string;
  customerId: string;
  amount: number | string;
  currencyCode: string;
  amountUnallocated: number | string;
  paymentDate?: string | null;
  method: PaymentMethod | string;
  reference?: string | null;
  notes?: string | null;
  status: PaymentStatus;
  version?: number;
  allocations?: AllocationDetailDto[];
};

export type PaymentListDto = {
  items: PaymentDto[];
  count: number;
};

export type PaymentReversalDto = {
  paymentId: string;
  status: string;
  affectedInvoiceIds: string[];
  message: string;
};

export type AllocationDetailDto = {
  invoiceId: string;
  amount: number | string;
  allocationId: string;
};

export type AllocationResultDto = {
  paymentId: string;
  allocations: AllocationDetailDto[];
  totalAllocated: number | string;
  remainingUnallocated: number | string;
  errors: string[];
  version: number;
  fullyAllocated: boolean;
};

export type InvoiceAllocationsDto = {
  invoiceId: string;
  allocations: AllocationDetailDto[];
};

export type ChequeStatus =
  | "RECEIVED"
  | "DEPOSITED"
  | "CLEARED"
  | "BOUNCED";

export type ChequeDto = {
  id: string;
  chequeNumber: string;
  customerId: string;
  amount: number | string;
  currencyCode: string;
  bankName?: string | null;
  bankBranch?: string | null;
  chequeDate?: string | null;
  receivedDate?: string | null;
  depositedDate?: string | null;
  clearedDate?: string | null;
  bouncedDate?: string | null;
  bounceReason?: string | null;
  status: ChequeStatus | string;
  paymentId?: string | null;
  allocatedInvoiceIds?: string[];
  notes?: string | null;
};

export type CreateChequeRequest = {
  chequeNumber: string;
  customerId: string;
  amount: number;
  currencyCode?: string;
  bankName?: string;
  bankBranch?: string;
  chequeDate?: string;
  notes?: string;
};

export type AgingInvoiceDetailDto = {
  invoiceId: string;
  invoiceNumber: string;
  amountDue: number | string;
  dueDate: string;
  daysOverdue: number;
  bucket: string;
};

export type AgingReportDto = {
  asOfDate: string;
  currencyCode: string;
  grandTotal: number | string;
  total0To30: number | string;
  total31To60: number | string;
  total61To90: number | string;
  total90Plus: number | string;
  totalCount: number;
  count0To30: number;
  count31To60: number;
  count61To90: number;
  count90Plus: number;
  invoices: AgingInvoiceDetailDto[];
};

export type AgingBucketDto = {
  code: string;
  label: string;
  earlyPaymentEligible: boolean;
};

export type DiscountRequest = {
  amount: number;
  currencyCode?: string;
  dueDate?: string;
  today?: string;
};

export type DiscountResponseDto = {
  invoiceId: string;
  originalAmount: number | string;
  discountAmount: number | string;
  discountedAmount: number | string;
  eligible: boolean;
  reason: string;
  discountRate: string;
};

export type CreditNoteStatus = "ISSUED" | "APPLIED" | "EXPIRED" | "CANCELLED";

export type CreditNoteDto = {
  id: string;
  creditNoteNumber: string;
  customerId: string;
  amount: number | string;
  currencyCode: string;
  type: string;
  referenceInvoiceId?: string | null;
  description?: string | null;
  status: CreditNoteStatus | string;
  issueDate?: string | null;
  appliedDate?: string | null;
  expiryDate?: string | null;
  appliedToPaymentId?: string | null;
  notes?: string | null;
};

export type GenerateCreditNoteRequest = {
  customerId: string;
  discountAmount: number;
  currencyCode?: string;
  referenceInvoiceId?: string;
};

export type LedgerAccountDto = {
  code: string;
  name: string;
  type: string;
};

export type LedgerBalanceDto = {
  account: string;
  balance: number | string;
  currency: string;
};

export type LedgerEntryDto = {
  id: string;
  account: string;
  amount: number | string;
  entryType: string;
  description?: string | null;
  transactionId?: string;
  referenceType?: string | null;
  referenceId?: string | null;
  createdAt?: string | null;
};

export type TenantDto = {
  id: string;
  code: string;
  name: string;
  baseCurrency: string;
  status: string;
  settingsJson?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type ExchangeRateDto = {
  id: string;
  fromCurrency: string;
  toCurrency: string;
  rate: number | string;
  effectiveDate: string;
  source?: string | null;
};

export type AuditDto = {
  id: string;
  entityType: string;
  entityId?: string | null;
  entityRef?: string | null;
  action: string;
  actorType?: string | null;
  beforeState?: string | null;
  afterState?: string | null;
  createdAt?: string | null;
};

export type WebhookDto = {
  id: string;
  url: string;
  eventTypes: string;
  active: boolean;
  createdAt?: string | null;
};

export type ExtractedChequeDto = {
  sourceFile?: string | null;
  segmentIndex?: number;
  chequeNumber?: string | null;
  amount?: number | string | null;
  currencyCode?: string | null;
  bankName?: string | null;
  bankBranch?: string | null;
  chequeDate?: string | null;
  payeeHint?: string | null;
  notes?: string | null;
  confidence?: number;
  rawSnippet?: string | null;
  completeEnough?: boolean;
};

export type OcrParseResult = {
  cheques: ExtractedChequeDto[];
  count: number;
};

export type OcrUploadResult = {
  cheques: ExtractedChequeDto[];
  count: number;
  warnings?: string[];
};