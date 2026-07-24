"use client";

import { useCallback, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Plus, Upload, FileScan, Loader2 } from "lucide-react";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusBadge } from "@/components/ui/status-badge";
import { Button } from "@/components/ui/button";
import { Input, Label, Select } from "@/components/ui/input";
import { TableSkeleton } from "@/components/ui/skeleton";
import { useTenant } from "@/components/tenant-provider";
import { listCustomers } from "@/lib/api/customers";
import { listInvoices } from "@/lib/api/invoices";
import {
  bounceCheque,
  bulkCreateCheques,
  clearCheque,
  createCheque,
  depositCheque,
  listCheques,
  parseChequeOcrText,
  uploadChequeOcrFiles,
} from "@/lib/api/cheques";
import {
  isImageFile,
  isPdfFile,
  ocrImageFiles,
} from "@/lib/cheque-ocr-client";
import { formatMoney } from "@/lib/money";
import { ApiError } from "@/lib/errors";
import type {
  ChequeDto,
  CreateChequeRequest,
  ExtractedChequeDto,
} from "@/types/ar";

const STATUSES = ["RECEIVED", "DEPOSITED", "CLEARED", "BOUNCED"] as const;

/** Mirrors invoicegenie.ocr.min-confidence default; overridable via NEXT_PUBLIC_OCR_MIN_CONFIDENCE. */
const MIN_OCR_CONFIDENCE = (() => {
  const raw = process.env.NEXT_PUBLIC_OCR_MIN_CONFIDENCE;
  const n = raw != null && raw !== "" ? Number(raw) : 0.45;
  return Number.isFinite(n) ? n : 0.45;
})();

type ReviewRow = {
  key: string;
  sourceFile: string;
  chequeNumber: string;
  customerId: string;
  amount: string;
  currencyCode: string;
  bankName: string;
  bankBranch: string;
  chequeDate: string;
  notes: string;
  confidence: number;
  payeeHint: string;
  selected: boolean;
};

function toReviewRow(c: ExtractedChequeDto, idx: number): ReviewRow {
  const confidence = c.confidence ?? 0;
  return {
    key: `${c.sourceFile ?? "src"}-${c.segmentIndex ?? idx}-${idx}`,
    sourceFile: c.sourceFile ?? "",
    chequeNumber: c.chequeNumber ?? "",
    customerId: "",
    amount: c.amount != null ? String(c.amount) : "",
    currencyCode: (c.currencyCode || "USD").toUpperCase(),
    bankName: c.bankName ?? "",
    bankBranch: c.bankBranch ?? "",
    chequeDate: c.chequeDate ?? new Date().toISOString().slice(0, 10),
    notes: c.notes ?? "",
    confidence,
    payeeHint: c.payeeHint ?? "",
    selected: !!(c.chequeNumber && c.amount) && confidence >= MIN_OCR_CONFIDENCE,
  };
}

function matchCustomerId(
  payeeHint: string,
  customers: { id: string; displayName?: string | null; legalName?: string | null; code?: string | null }[],
): string {
  if (!payeeHint?.trim() || !customers?.length) return "";
  const hint = payeeHint.trim().toLowerCase();
  const exact = customers.find((c) => {
    const names = [c.displayName, c.legalName, c.code]
      .filter(Boolean)
      .map((s) => String(s).trim().toLowerCase());
    return names.some((n) => n === hint);
  });
  if (exact) return exact.id;
  const partial = customers.find((c) => {
    const names = [c.displayName, c.legalName, c.code]
      .filter(Boolean)
      .map((s) => String(s).trim().toLowerCase());
    return names.some((n) => n.includes(hint) || hint.includes(n));
  });
  return partial?.id ?? "";
}

export default function ChequesPage() {
  const { tenantId, ready } = useTenant();
  const queryClient = useQueryClient();
  const fileRef = useRef<HTMLInputElement>(null);
  const [status, setStatus] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [showOcr, setShowOcr] = useState(false);
  const [bounceId, setBounceId] = useState<string | null>(null);
  const [bounceReason, setBounceReason] = useState("");
  const [bounceImpact, setBounceImpact] = useState<string[]>([]);
  const [clearChequeRow, setClearChequeRow] = useState<ChequeDto | null>(null);
  const [clearInvoiceIds, setClearInvoiceIds] = useState<string[]>([]);
  const [chequeNumber, setChequeNumber] = useState("");
  const [customerId, setCustomerId] = useState("");
  const [amount, setAmount] = useState("");
  const [currencyCode, setCurrencyCode] = useState("USD");
  const [bankName, setBankName] = useState("");
  const [bankBranch, setBankBranch] = useState("");
  const [chequeDate, setChequeDate] = useState(
    () => new Date().toISOString().slice(0, 10),
  );
  const [notes, setNotes] = useState("");
  const [reviewRows, setReviewRows] = useState<ReviewRow[]>([]);
  const [ocrBusy, setOcrBusy] = useState(false);
  const [ocrProgress, setOcrProgress] = useState("");
  const [defaultCustomerId, setDefaultCustomerId] = useState("");

  const cheques = useQuery({
    queryKey: ["cheques", tenantId, status],
    enabled: ready,
    queryFn: ({ signal }) =>
      listCheques(tenantId, { status: status || undefined, signal }),
  });

  const customers = useQuery({
    queryKey: ["customers", tenantId, "ACTIVE"],
    enabled: ready && (showCreate || showOcr || reviewRows.length > 0),
    queryFn: ({ signal }) =>
      listCustomers(tenantId, { status: "ACTIVE", signal }),
  });

  const invalidate = () =>
    void queryClient.invalidateQueries({ queryKey: ["cheques", tenantId] });

  const onErr = (err: Error) =>
    toast.error(err instanceof ApiError ? err.message : err.message);

  const createMut = useMutation({
    mutationFn: () => {
      const n = Number(amount);
      if (!chequeNumber.trim()) throw new Error("Cheque number required");
      if (!customerId) throw new Error("Customer required");
      if (Number.isNaN(n) || n <= 0) throw new Error("Valid amount required");
      return createCheque(tenantId, {
        chequeNumber: chequeNumber.trim(),
        customerId,
        amount: n,
        currencyCode: currencyCode || "USD",
        bankName: bankName || undefined,
        bankBranch: bankBranch || undefined,
        chequeDate: chequeDate || undefined,
        notes: notes || undefined,
      });
    },
    onSuccess: (c) => {
      toast.success(`Cheque ${c.chequeNumber} received`);
      setShowCreate(false);
      setChequeNumber("");
      setAmount("");
      invalidate();
    },
    onError: onErr,
  });

  const bulkMut = useMutation({
    mutationFn: (items: CreateChequeRequest[]) =>
      bulkCreateCheques(tenantId, items),
    onSuccess: (created) => {
      toast.success(`Received ${created.length} cheque(s) from OCR`);
      setReviewRows([]);
      setShowOcr(false);
      invalidate();
    },
    onError: onErr,
  });

  const processFiles = useCallback(
    async (fileList: FileList | File[]) => {
      const files = Array.from(fileList);
      if (!files.length) return;
      setOcrBusy(true);
      setOcrProgress("Preparing uploads…");
      try {
        const pdfsAndText = files.filter(
          (f) => isPdfFile(f) || /\.(txt|csv)$/i.test(f.name),
        );
        const images = files.filter((f) => isImageFile(f));
        const extracted: ExtractedChequeDto[] = [];

        if (pdfsAndText.length) {
          setOcrProgress(
            `Uploading ${pdfsAndText.length} PDF/text file(s) for server OCR…`,
          );
          const up = await uploadChequeOcrFiles(tenantId, pdfsAndText);
          extracted.push(
            ...up.cheques.filter((c) => c.notes !== "IMAGE_PENDING_CLIENT_OCR"),
          );
          if (up.warnings?.length) {
            toast.message(up.warnings.slice(0, 3).join(" · "));
          }
        }

        if (images.length) {
          setOcrProgress(`Running browser OCR on ${images.length} image(s)…`);
          const blocks = await ocrImageFiles(images, (name, pct) => {
            setOcrProgress(`OCR ${name}: ${pct}%`);
          });
          if (blocks.length) {
            setOcrProgress("Parsing OCR text into cheque fields…");
            const parsed = await parseChequeOcrText(tenantId, blocks);
            extracted.push(...parsed.cheques);
          }
        }

        if (!extracted.length) {
          toast.error(
            "No cheque fields detected. Try a clearer scan or fill the form manually.",
          );
          return;
        }

        const list = customers.data ?? [];
        const rows = extracted.map((c, i) => {
          const row = toReviewRow(c, i);
          if (defaultCustomerId) {
            row.customerId = defaultCustomerId;
          } else if (row.payeeHint) {
            row.customerId = matchCustomerId(
              row.payeeHint,
              list.map((c) => ({
                id: c.id,
                displayName: c.displayName,
                legalName: c.legalName,
                code: c.customerCode,
              })),
            );
          }
          return row;
        });
        const low = rows.filter((r) => r.confidence < MIN_OCR_CONFIDENCE).length;
        setReviewRows((prev) => [...prev, ...rows]);
        setShowOcr(true);
        toast.success(
          `Extracted ${rows.length} cheque candidate(s). Review and receive.`,
        );
        if (low > 0) {
          toast.message(
            `${low} row(s) below confidence ${(MIN_OCR_CONFIDENCE * 100).toFixed(0)}% — correct fields or leave unselected.`,
          );
        }
      } catch (e) {
        onErr(e instanceof Error ? e : new Error(String(e)));
      } finally {
        setOcrBusy(false);
        setOcrProgress("");
        if (fileRef.current) fileRef.current.value = "";
      }
    },
    [tenantId, defaultCustomerId, customers.data],
  );

  const applyDefaultCustomer = () => {
    if (!defaultCustomerId) {
      toast.error("Select a default customer first");
      return;
    }
    setReviewRows((rows) =>
      rows.map((r) => ({ ...r, customerId: defaultCustomerId })),
    );
    toast.success("Default customer applied to all rows");
  };

  const submitBulk = () => {
    const selected = reviewRows.filter((r) => r.selected);
    if (!selected.length) {
      toast.error("Select at least one cheque row");
      return;
    }
    const payload: CreateChequeRequest[] = [];
    for (const r of selected) {
      const n = Number(r.amount);
      if (!r.chequeNumber.trim()) {
        toast.error("Each selected row needs a cheque number");
        return;
      }
      if (!r.customerId) {
        toast.error("Each selected row needs a matched customer");
        return;
      }
      if (Number.isNaN(n) || n <= 0) {
        toast.error(`Invalid amount on cheque ${r.chequeNumber}`);
        return;
      }
      if (r.confidence < MIN_OCR_CONFIDENCE) {
        toast.error(
          `Cheque ${r.chequeNumber}: confidence ${(r.confidence * 100).toFixed(0)}% below ${(MIN_OCR_CONFIDENCE * 100).toFixed(0)}% threshold — correct fields after re-OCR or unselect`,
        );
        return;
      }
      payload.push({
        chequeNumber: r.chequeNumber.trim(),
        customerId: r.customerId,
        amount: n,
        currencyCode: r.currencyCode || "USD",
        bankName: r.bankName || undefined,
        bankBranch: r.bankBranch || undefined,
        chequeDate: r.chequeDate || undefined,
        notes: r.notes || undefined,
        ocrConfidence: r.confidence,
      });
    }
    bulkMut.mutate(payload);
  };

  const depositMut = useMutation({
    mutationFn: (id: string) => depositCheque(tenantId, id),
    onSuccess: () => {
      toast.success("Cheque deposited");
      invalidate();
    },
    onError: onErr,
  });

  const clearOpenInvoices = useQuery({
    queryKey: [
      "invoices",
      tenantId,
      "clear-cheque",
      clearChequeRow?.customerId,
      clearChequeRow?.currencyCode,
    ],
    enabled: ready && !!clearChequeRow?.customerId,
    queryFn: async ({ signal }) => {
      const pages = await Promise.all(
        (["ISSUED", "PARTIALLY_PAID", "OVERDUE"] as const).map((status) =>
          listInvoices(tenantId, { status, limit: 50, signal }),
        ),
      );
      const ccy = (clearChequeRow?.currencyCode || "USD").toUpperCase();
      const cust = clearChequeRow?.customerId;
      return pages
        .flatMap((p) => p.items)
        .filter((inv) => {
          if (cust && inv.customerId && inv.customerId !== cust) return false;
          if (
            inv.currencyCode &&
            inv.currencyCode.toUpperCase() !== ccy
          ) {
            return false;
          }
          return true;
        });
    },
  });

  const clearMut = useMutation({
    mutationFn: () => {
      if (!clearChequeRow) throw new Error("No cheque selected");
      return clearCheque(
        tenantId,
        clearChequeRow.id,
        clearInvoiceIds.length ? clearInvoiceIds : undefined,
      );
    },
    onSuccess: (r) => {
      const pay = r.paymentId ? ` Payment ${r.paymentId.slice(0, 8)}…` : "";
      toast.success(`Cheque cleared.${pay}`);
      setClearChequeRow(null);
      setClearInvoiceIds([]);
      invalidate();
    },
    onError: onErr,
  });

  const bounceMut = useMutation({
    mutationFn: () => {
      if (!bounceId) throw new Error("No cheque selected");
      if (!bounceReason.trim()) throw new Error("Bounce reason required");
      return bounceCheque(tenantId, bounceId, bounceReason.trim());
    },
    onSuccess: (r) => {
      const affected = r.affectedInvoices?.length
        ? ` Affected invoices: ${r.affectedInvoices.length}`
        : "";
      toast.success(`Cheque bounced.${affected}`);
      setBounceId(null);
      setBounceReason("");
      setBounceImpact([]);
      invalidate();
    },
    onError: onErr,
  });

  const bounceTarget = useMemo(
    () => cheques.data?.find((c) => c.id === bounceId) ?? null,
    [cheques.data, bounceId],
  );

  return (
    <div>
      <PageHeader
        title="Cheques"
        description="Cheque lifecycle, OCR upload (images / multi-PDF bulk), receive to clear or bounce."
        actions={
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setShowOcr(true);
                fileRef.current?.click();
              }}
              disabled={ocrBusy}
            >
              {ocrBusy ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Upload className="h-4 w-4" />
              )}
              Upload & OCR
            </Button>
            <Button type="button" onClick={() => setShowCreate((v) => !v)}>
              <Plus className="h-4 w-4" />
              New cheque
            </Button>
          </div>
        }
      />

      <input
        ref={fileRef}
        type="file"
        className="hidden"
        accept="image/*,.pdf,.txt,.csv,application/pdf"
        multiple
        onChange={(e) => {
          if (e.target.files?.length) void processFiles(e.target.files);
        }}
      />

      {ocrBusy ? (
        <Card className="mb-6 border-indigo-200 bg-indigo-50/50 dark:border-indigo-900 dark:bg-indigo-950/30">
          <div className="flex items-center gap-3 text-sm text-indigo-800 dark:text-indigo-200">
            <Loader2 className="h-5 w-5 shrink-0 animate-spin" />
            <div>
              <p className="font-medium">Processing cheques…</p>
              <p className="text-xs opacity-80">{ocrProgress || "Working"}</p>
            </div>
          </div>
        </Card>
      ) : null}

      {showOcr || reviewRows.length > 0 ? (
        <Card className="mb-6">
          <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h2 className="flex items-center gap-2 text-sm font-semibold">
                <FileScan className="h-4 w-4" />
                OCR review & bulk receive
              </h2>
              <p className="mt-1 text-xs text-zinc-500">
                Multiple cheque images and multi-page PDFs supported. Images use
                browser OCR; PDFs use server text extraction.
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                variant="secondary"
                onClick={() => fileRef.current?.click()}
                disabled={ocrBusy}
              >
                <Upload className="h-4 w-4" />
                Add files
              </Button>
              <Button
                type="button"
                variant="ghost"
                onClick={() => {
                  setShowOcr(false);
                  setReviewRows([]);
                }}
              >
                Close
              </Button>
            </div>
          </div>

          <div
            className="mb-4 rounded-xl border-2 border-dashed border-zinc-300 bg-zinc-50 px-4 py-8 text-center dark:border-zinc-700 dark:bg-zinc-900/40"
            onDragOver={(e) => {
              e.preventDefault();
              e.stopPropagation();
            }}
            onDrop={(e) => {
              e.preventDefault();
              e.stopPropagation();
              if (e.dataTransfer.files?.length) {
                void processFiles(e.dataTransfer.files);
              }
            }}
          >
            <Upload className="mx-auto mb-2 h-8 w-8 text-zinc-400" />
            <p className="text-sm font-medium">
              Drag & drop cheque images or PDF here
            </p>
            <p className="mt-1 text-xs text-zinc-500">
              PNG, JPG, WebP, multi-page PDF, multi-file bulk
            </p>
            <Button
              type="button"
              className="mt-3"
              variant="secondary"
              onClick={() => fileRef.current?.click()}
              disabled={ocrBusy}
            >
              Choose files
            </Button>
          </div>

          <div className="mb-4 grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
            <div>
              <Label htmlFor="ocr-default-cust">
                Default customer for OCR rows
              </Label>
              <Select
                id="ocr-default-cust"
                value={defaultCustomerId}
                onChange={(e) => setDefaultCustomerId(e.target.value)}
              >
                <option value="">Select…</option>
                {(customers.data ?? []).map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.customerCode} - {c.displayName || c.legalName}
                  </option>
                ))}
              </Select>
            </div>
            <Button
              type="button"
              variant="secondary"
              onClick={applyDefaultCustomer}
              disabled={!defaultCustomerId || !reviewRows.length}
            >
              Apply to all rows
            </Button>
          </div>

          {reviewRows.length ? (
            <>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[56rem] text-left text-sm">
                  <thead className="border-b border-zinc-200 text-xs uppercase text-zinc-500 dark:border-zinc-800">
                    <tr>
                      <th className="pb-2 pr-2 font-medium">Use</th>
                      <th className="pb-2 pr-2 font-medium">Source</th>
                      <th className="pb-2 pr-2 font-medium">Number</th>
                      <th className="pb-2 pr-2 font-medium">Customer</th>
                      <th className="pb-2 pr-2 font-medium">Amount</th>
                      <th className="pb-2 pr-2 font-medium">CCY</th>
                      <th className="pb-2 pr-2 font-medium">Bank</th>
                      <th className="pb-2 pr-2 font-medium">Branch</th>
                      <th className="pb-2 pr-2 font-medium">Date</th>
                      <th className="pb-2 font-medium">Conf.</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-zinc-100 dark:divide-zinc-900">
                    {reviewRows.map((row, idx) => (
                      <tr key={row.key}>
                        <td className="py-2 pr-2">
                          <input
                            type="checkbox"
                            className="h-4 w-4"
                            checked={row.selected}
                            onChange={(e) =>
                              setReviewRows((rs) =>
                                rs.map((r, i) =>
                                  i === idx
                                    ? { ...r, selected: e.target.checked }
                                    : r,
                                ),
                              )
                            }
                          />
                        </td>
                        <td className="max-w-[7rem] truncate py-2 pr-2 font-mono text-xs text-zinc-500">
                          {row.sourceFile || "—"}
                        </td>
                        <td className="py-2 pr-2">
                          <Input
                            className="min-w-[6rem]"
                            value={row.chequeNumber}
                            onChange={(e) =>
                              setReviewRows((rs) =>
                                rs.map((r, i) =>
                                  i === idx
                                    ? { ...r, chequeNumber: e.target.value }
                                    : r,
                                ),
                              )
                            }
                          />
                        </td>
                        <td className="py-2 pr-2">
                          <Select
                            className="min-w-[9rem]"
                            value={row.customerId}
                            onChange={(e) =>
                              setReviewRows((rs) =>
                                rs.map((r, i) =>
                                  i === idx
                                    ? { ...r, customerId: e.target.value }
                                    : r,
                                ),
                              )
                            }
                          >
                            <option value="">Select…</option>
                            {(customers.data ?? []).map((c) => (
                              <option key={c.id} value={c.id}>
                                {c.customerCode}
                              </option>
                            ))}
                          </Select>
                        </td>
                        <td className="py-2 pr-2">
                          <Input
                            className="w-28"
                            type="number"
                            step="0.01"
                            value={row.amount}
                            onChange={(e) =>
                              setReviewRows((rs) =>
                                rs.map((r, i) =>
                                  i === idx
                                    ? { ...r, amount: e.target.value }
                                    : r,
                                ),
                              )
                            }
                          />
                        </td>
                        <td className="py-2 pr-2">
                          <Input
                            className="w-16"
                            maxLength={3}
                            value={row.currencyCode}
                            onChange={(e) =>
                              setReviewRows((rs) =>
                                rs.map((r, i) =>
                                  i === idx
                                    ? {
                                        ...r,
                                        currencyCode:
                                          e.target.value.toUpperCase(),
                                      }
                                    : r,
                                ),
                              )
                            }
                          />
                        </td>
                        <td className="py-2 pr-2">
                          <Input
                            className="min-w-[6rem]"
                            value={row.bankName}
                            onChange={(e) =>
                              setReviewRows((rs) =>
                                rs.map((r, i) =>
                                  i === idx
                                    ? { ...r, bankName: e.target.value }
                                    : r,
                                ),
                              )
                            }
                          />
                        </td>
                        <td className="py-2 pr-2">
                          <Input
                            className="min-w-[5rem]"
                            value={row.bankBranch}
                            onChange={(e) =>
                              setReviewRows((rs) =>
                                rs.map((r, i) =>
                                  i === idx
                                    ? { ...r, bankBranch: e.target.value }
                                    : r,
                                ),
                              )
                            }
                          />
                        </td>
                        <td className="py-2 pr-2">
                          <Input
                            type="date"
                            className="min-w-[9rem]"
                            value={row.chequeDate}
                            onChange={(e) =>
                              setReviewRows((rs) =>
                                rs.map((r, i) =>
                                  i === idx
                                    ? { ...r, chequeDate: e.target.value }
                                    : r,
                                ),
                              )
                            }
                          />
                        </td>
                        <td className="py-2 tabular-nums text-xs text-zinc-500">
                          {Math.round(row.confidence * 100)}%
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="mt-4 flex flex-wrap gap-2">
                <Button
                  type="button"
                  disabled={bulkMut.isPending}
                  onClick={submitBulk}
                >
                  {bulkMut.isPending
                    ? "Saving…"
                    : `Receive selected (${reviewRows.filter((r) => r.selected).length})`}
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setReviewRows([])}
                >
                  Clear review
                </Button>
              </div>
            </>
          ) : (
            <p className="text-center text-sm text-zinc-500">
              No extracted cheques yet — upload files above.
            </p>
          )}
        </Card>
      ) : null}

      {showCreate ? (
        <Card className="mb-6">
          <h2 className="mb-4 text-sm font-semibold">Receive cheque</h2>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <div>
              <Label htmlFor="chq-num">Cheque number</Label>
              <Input
                id="chq-num"
                value={chequeNumber}
                onChange={(e) => setChequeNumber(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="chq-cust">Customer</Label>
              <Select
                id="chq-cust"
                value={customerId}
                onChange={(e) => setCustomerId(e.target.value)}
              >
                <option value="">Select...</option>
                {(customers.data ?? []).map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.customerCode} - {c.displayName || c.legalName}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <Label htmlFor="chq-amt">Amount</Label>
              <Input
                id="chq-amt"
                type="number"
                min="0"
                step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="chq-ccy">Currency</Label>
              <Input
                id="chq-ccy"
                value={currencyCode}
                onChange={(e) => setCurrencyCode(e.target.value.toUpperCase())}
                maxLength={3}
              />
            </div>
            <div>
              <Label htmlFor="chq-bank">Bank</Label>
              <Input
                id="chq-bank"
                value={bankName}
                onChange={(e) => setBankName(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="chq-branch">Branch</Label>
              <Input
                id="chq-branch"
                value={bankBranch}
                onChange={(e) => setBankBranch(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="chq-date">Cheque date</Label>
              <Input
                id="chq-date"
                type="date"
                value={chequeDate}
                onChange={(e) => setChequeDate(e.target.value)}
              />
            </div>
            <div className="sm:col-span-2">
              <Label htmlFor="chq-notes">Notes</Label>
              <Input
                id="chq-notes"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </div>
          </div>
          <div className="mt-4 flex gap-2">
            <Button
              type="button"
              disabled={createMut.isPending}
              onClick={() => createMut.mutate()}
            >
              {createMut.isPending ? "Saving..." : "Receive cheque"}
            </Button>
            <Button
              type="button"
              variant="secondary"
              onClick={() => setShowCreate(false)}
            >
              Cancel
            </Button>
          </div>
        </Card>
      ) : null}

      {clearChequeRow ? (
        <Card className="mb-6 border-indigo-200 dark:border-indigo-900">
          <h2 className="mb-2 text-sm font-semibold">Clear cheque</h2>
          <p className="mb-1 text-sm">
            {clearChequeRow.chequeNumber} ·{" "}
            {formatMoney(clearChequeRow.amount, clearChequeRow.currencyCode)}
          </p>
          <p className="mb-3 font-mono text-xs text-zinc-500">
            {clearChequeRow.id}
          </p>
          <p className="mb-2 text-xs text-zinc-500">
            Select invoices to allocate (optional). Leave empty for FIFO
            against open invoices for this customer.
          </p>
          {clearOpenInvoices.isLoading ? (
            <p className="text-xs text-zinc-500">Loading open invoices…</p>
          ) : !clearOpenInvoices.data?.length ? (
            <p className="mb-3 text-xs text-amber-700 dark:text-amber-300">
              No open same-currency invoices found — clear will create an
              unallocated CHECK payment.
            </p>
          ) : (
            <ul className="mb-3 max-h-48 space-y-1 overflow-y-auto rounded-lg border border-zinc-200 p-2 text-sm dark:border-zinc-800">
              {clearOpenInvoices.data.map((inv) => {
                const checked = clearInvoiceIds.includes(inv.id);
                return (
                  <li key={inv.id}>
                    <label className="flex cursor-pointer items-center gap-2 rounded px-1 py-1 hover:bg-zinc-50 dark:hover:bg-zinc-900">
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => {
                          setClearInvoiceIds((prev) =>
                            checked
                              ? prev.filter((x) => x !== inv.id)
                              : [...prev, inv.id],
                          );
                        }}
                      />
                      <span className="font-medium">{inv.invoiceNumber}</span>
                      <span className="text-xs text-zinc-500">
                        {formatMoney(inv.total, inv.currencyCode)} ·{" "}
                        {String(inv.status)}
                      </span>
                    </label>
                  </li>
                );
              })}
            </ul>
          )}
          <div className="mt-3 flex gap-2">
            <Button
              type="button"
              disabled={clearMut.isPending}
              onClick={() => clearMut.mutate()}
            >
              {clearMut.isPending ? "Clearing…" : "Confirm clear"}
            </Button>
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setClearChequeRow(null);
                setClearInvoiceIds([]);
              }}
            >
              Cancel
            </Button>
          </div>
        </Card>
      ) : null}

      {bounceId ? (
        <Card className="mb-6 border-rose-200 dark:border-rose-900">
          <h2 className="mb-2 text-sm font-semibold text-rose-700 dark:text-rose-300">
            Bounce cheque
          </h2>
          <p className="mb-1 text-sm">
            {bounceTarget?.chequeNumber ?? "Cheque"} · status{" "}
            {bounceTarget?.status ?? "—"}
          </p>
          <p className="mb-3 font-mono text-xs text-zinc-500">{bounceId}</p>
          {(bounceImpact.length > 0 ||
            bounceTarget?.paymentId ||
            (bounceTarget?.allocatedInvoiceIds?.length ?? 0) > 0) && (
            <div className="mb-3 rounded-lg border border-rose-200 bg-rose-50/60 p-3 text-xs dark:border-rose-900 dark:bg-rose-950/40">
              <p className="mb-1 font-semibold text-rose-800 dark:text-rose-200">
                Impact if bounced
              </p>
              {bounceTarget?.paymentId ? (
                <p className="text-rose-700 dark:text-rose-300">
                  Linked payment will reverse:{" "}
                  <span className="font-mono">{bounceTarget.paymentId}</span>
                </p>
              ) : (
                <p className="text-zinc-600 dark:text-zinc-400">
                  No linked payment (status-only bounce if not cleared).
                </p>
              )}
              {(bounceTarget?.allocatedInvoiceIds?.length ?? 0) > 0 ||
              bounceImpact.length > 0 ? (
                <ul className="mt-1 list-inside list-disc text-rose-700 dark:text-rose-300">
                  {(bounceImpact.length
                    ? bounceImpact
                    : bounceTarget?.allocatedInvoiceIds ?? []
                  ).map((id) => (
                    <li key={id} className="font-mono">
                      Invoice {id}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-1 text-zinc-600 dark:text-zinc-400">
                  No allocated invoices on this cheque.
                </p>
              )}
            </div>
          )}
          <Label htmlFor="bounce-reason">Reason (required)</Label>
          <Input
            id="bounce-reason"
            value={bounceReason}
            onChange={(e) => setBounceReason(e.target.value)}
            placeholder="NSF / stop payment"
          />
          <div className="mt-3 flex gap-2">
            <Button
              type="button"
              variant="danger"
              disabled={bounceMut.isPending || !bounceReason.trim()}
              onClick={() => bounceMut.mutate()}
            >
              Confirm bounce
            </Button>
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setBounceId(null);
                setBounceReason("");
                setBounceImpact([]);
              }}
            >
              Cancel
            </Button>
          </div>
        </Card>
      ) : null}

      <Card>
        <div className="mb-4 w-full sm:w-52">
          <Label htmlFor="chq-status">Status</Label>
          <Select
            id="chq-status"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
          >
            <option value="">All</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </Select>
        </div>

        {cheques.isLoading ? (
          <TableSkeleton />
        ) : cheques.isError ? (
          <p className="py-8 text-center text-sm text-rose-600">
            {(cheques.error as Error).message}
          </p>
        ) : !cheques.data?.length ? (
          <EmptyState
            title="No cheques"
            description="Receive a cheque manually or upload scans/PDF for OCR bulk processing."
            action={
              <div className="flex flex-wrap justify-center gap-2">
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => {
                    setShowOcr(true);
                    fileRef.current?.click();
                  }}
                >
                  <Upload className="h-4 w-4" />
                  Upload & OCR
                </Button>
                <Button type="button" onClick={() => setShowCreate(true)}>
                  <Plus className="h-4 w-4" />
                  New cheque
                </Button>
              </div>
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-zinc-200 text-xs uppercase text-zinc-500 dark:border-zinc-800">
                <tr>
                  <th className="pb-2 pr-3 font-medium">Number</th>
                  <th className="pb-2 pr-3 font-medium">Status</th>
                  <th className="pb-2 pr-3 font-medium">Bank</th>
                  <th className="pb-2 pr-3 text-right font-medium">Amount</th>
                  <th className="pb-2 font-medium">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-900">
                {cheques.data.map((c) => (
                  <tr key={c.id}>
                    <td className="py-3 pr-3">
                      <div className="font-medium">{c.chequeNumber}</div>
                      <div className="font-mono text-xs text-zinc-500">
                        {c.id.slice(0, 8)}...
                      </div>
                    </td>
                    <td className="py-3 pr-3">
                      <StatusBadge status={String(c.status)} />
                      {c.bounceReason ? (
                        <div className="mt-1 text-xs text-rose-600">
                          {c.bounceReason}
                        </div>
                      ) : null}
                    </td>
                    <td className="py-3 pr-3 text-zinc-600 dark:text-zinc-400">
                      {c.bankName || "-"}
                      {c.bankBranch ? " / " + c.bankBranch : ""}
                    </td>
                    <td className="py-3 pr-3 text-right tabular-nums">
                      {formatMoney(c.amount, c.currencyCode)}
                    </td>
                    <td className="py-3">
                      <div className="flex flex-wrap gap-1">
                        {c.status === "RECEIVED" ? (
                          <Button
                            type="button"
                            variant="secondary"
                            disabled={depositMut.isPending}
                            onClick={() => depositMut.mutate(c.id)}
                          >
                            Deposit
                          </Button>
                        ) : null}
                        {c.status === "DEPOSITED" || c.status === "CLEARED" ? (
                          <>
                            {c.status === "DEPOSITED" ? (
                              <Button
                                type="button"
                                disabled={clearMut.isPending}
                                onClick={() => {
                                  setClearChequeRow(c);
                                  setClearInvoiceIds(
                                    c.allocatedInvoiceIds ?? [],
                                  );
                                }}
                              >
                                Clear
                              </Button>
                            ) : null}
                            <Button
                              type="button"
                              variant="danger"
                              onClick={() => {
                                setBounceId(c.id);
                                setBounceImpact(c.allocatedInvoiceIds ?? []);
                                setBounceReason("");
                              }}
                            >
                              Bounce
                            </Button>
                          </>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}