"use client";

import { useCallback, useRef, useState } from "react";
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
import type { CreateChequeRequest, ExtractedChequeDto } from "@/types/ar";

const STATUSES = ["RECEIVED", "DEPOSITED", "CLEARED", "BOUNCED"] as const;

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
  selected: boolean;
};

function toReviewRow(c: ExtractedChequeDto, idx: number): ReviewRow {
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
    confidence: c.confidence ?? 0,
    selected: !!(c.chequeNumber && c.amount),
  };
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

        const rows = extracted.map((c, i) => {
          const row = toReviewRow(c, i);
          if (defaultCustomerId) row.customerId = defaultCustomerId;
          return row;
        });
        setReviewRows((prev) => [...prev, ...rows]);
        setShowOcr(true);
        toast.success(
          `Extracted ${rows.length} cheque candidate(s). Review and receive.`,
        );
      } catch (e) {
        onErr(e instanceof Error ? e : new Error(String(e)));
      } finally {
        setOcrBusy(false);
        setOcrProgress("");
        if (fileRef.current) fileRef.current.value = "";
      }
    },
    [tenantId, defaultCustomerId],
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
        toast.error("Each selected row needs a customer");
        return;
      }
      if (Number.isNaN(n) || n <= 0) {
        toast.error(`Invalid amount on cheque ${r.chequeNumber}`);
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

  const clearMut = useMutation({
    mutationFn: (id: string) => clearCheque(tenantId, id),
    onSuccess: () => {
      toast.success("Cheque cleared");
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
      invalidate();
    },
    onError: onErr,
  });

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

      {bounceId ? (
        <Card className="mb-6 border-rose-200 dark:border-rose-900">
          <h2 className="mb-2 text-sm font-semibold text-rose-700 dark:text-rose-300">
            Bounce cheque
          </h2>
          <p className="mb-3 font-mono text-xs text-zinc-500">{bounceId}</p>
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
                        {c.status === "DEPOSITED" ? (
                          <>
                            <Button
                              type="button"
                              disabled={clearMut.isPending}
                              onClick={() => clearMut.mutate(c.id)}
                            >
                              Clear
                            </Button>
                            <Button
                              type="button"
                              variant="danger"
                              onClick={() => setBounceId(c.id)}
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