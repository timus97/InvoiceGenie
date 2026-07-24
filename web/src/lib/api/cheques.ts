import { apiFetch } from "@/lib/api/client";
import { apiPaths } from "@/lib/api/paths";
import type {
  ChequeDto,
  CreateChequeRequest,
  ExtractedChequeDto,
  OcrParseResult,
  OcrUploadResult,
} from "@/types/ar";

export function listCheques(
  tenantId: string,
  opts?: { status?: string; signal?: AbortSignal },
) {
  return apiFetch<ChequeDto[]>(apiPaths.cheques, {
    tenantId,
    signal: opts?.signal,
    query: { status: opts?.status },
  });
}

export function getCheque(tenantId: string, id: string, signal?: AbortSignal) {
  return apiFetch<ChequeDto>(apiPaths.cheque(id), { tenantId, signal });
}

export function createCheque(tenantId: string, body: CreateChequeRequest) {
  return apiFetch<ChequeDto>(apiPaths.cheques, {
    method: "POST",
    tenantId,
    body,
  });
}

export function bulkCreateCheques(
  tenantId: string,
  cheques: CreateChequeRequest[],
) {
  return apiFetch<ChequeDto[]>(apiPaths.chequeBulk, {
    method: "POST",
    tenantId,
    body: { cheques },
  });
}

export function parseChequeOcrText(
  tenantId: string,
  blocks: { sourceName: string; text: string }[],
) {
  return apiFetch<OcrParseResult>(apiPaths.chequeOcrParse, {
    method: "POST",
    tenantId,
    body: { blocks },
  });
}

/** Multipart PDF/image/text upload for bulk OCR. */
export async function uploadChequeOcrFiles(
  tenantId: string,
  files: File[],
): Promise<OcrUploadResult> {
  const form = new FormData();
  for (const f of files) {
    form.append("files", f, f.name);
  }
  const headers: Record<string, string> = {
    Accept: "application/json",
    "X-Tenant-Id": tenantId.trim(),
  };
  const apiKey = process.env.NEXT_PUBLIC_API_KEY?.trim();
  if (apiKey) headers["X-API-Key"] = apiKey;

  const res = await fetch(apiPaths.chequeOcrUpload, {
    method: "POST",
    headers,
    body: form,
    cache: "no-store",
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `OCR upload failed (${res.status})`);
  }
  return (await res.json()) as OcrUploadResult;
}

export function depositCheque(tenantId: string, id: string) {
  return apiFetch<ChequeDto>(apiPaths.chequeDeposit(id), {
    method: "POST",
    tenantId,
  });
}

export function clearCheque(tenantId: string, id: string) {
  return apiFetch<{ cheque: ChequeDto; ledgerEntries?: unknown[] }>(
    apiPaths.chequeClear(id),
    { method: "POST", tenantId },
  );
}

export function bounceCheque(tenantId: string, id: string, reason: string) {
  return apiFetch<{
    cheque: ChequeDto;
    reverseEntries?: unknown[];
    affectedInvoices?: string[];
  }>(apiPaths.chequeBounce(id), {
    method: "POST",
    tenantId,
    body: { reason },
  });
}

export type { ExtractedChequeDto };