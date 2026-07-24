# Cheque OCR production path (STORY-018)

## Supported modes

| Mode | Where | Input | Notes |
|------|--------|-------|--------|
| **Client OCR (product path)** | Browser (`web/src/lib/cheque-ocr-client.ts` + Tesseract.js) | PNG/JPG/WebP/TIFF scans | Text is sent to `POST /api/v1/cheques/ocr/parse`. **No Tesseract in the API image.** |
| **Server PDF text** | API (`PdfBoxTextExtractor`) | PDF with a text layer | `POST /api/v1/cheques/ocr/upload` extracts text via PDFBox. Image-only PDFs return low-confidence / empty fields. |
| **Plain text / CSV upload** | API | `.txt` / `.csv` | Parsed by the same heuristic `ChequeOcrParser`. |
| **Future server OCR** | Deferred | Raster images on server | Prefer a dedicated OCR service; do **not** ship native Tesseract binaries in `ar-bootstrap`. |

Images uploaded to `/upload` receive `IMAGE_PENDING_CLIENT_OCR` placeholders so the console can re-run client OCR and call `/parse`.

## Bulk receive gates

1. **Customer match** — every bulk row requires an existing **ACTIVE** customer for the tenant (`customerId`). Unknown or blocked customers are rejected with `400 VALIDATION_ERROR`.
2. **Confidence threshold** — when `ocrConfidence` is present on a bulk item, the API rejects values below `invoicegenie.ocr.min-confidence` (default **0.45**, env `INVOICEGENIE_OCR_MIN_CONFIDENCE`).
3. Operators should correct low-confidence fields in the review grid before receive (or deselect those rows).

## Metrics / logging

Each `/parse` and `/upload` batch logs a structured line:

```
OCR metrics mode=parse batchExtracted=N batchComplete=M batchCompleteRate=R lifetimeTotal=… lifetimeCompleteRate=…
```

- **complete** = extracted cheque has both cheque number and positive amount (and is not an image-pending placeholder).
- Grep application logs for `OCR metrics` to track parse success rate over time.
- Optional: wire Micrometer counters later at bootstrap without bloating the API module.

## Operator correction UX

1. Upload scans/PDFs on **Cheques → OCR bulk**.
2. Review grid: fix cheque number, amount, bank, date; assign customer (or apply default / auto-match from payee hint).
3. Confidence % is shown per row; rows below the configured floor are **not selected** by default and blocked on bulk submit.
4. Receive selected → `POST /api/v1/cheques/bulk` with optional `ocrConfidence` per item.
5. If the API rejects low confidence, re-scan or raise confidence by filling missing fields and clearing `ocrConfidence` only after manual verification (prefer fixing fields and re-OCR rather than spoofing confidence).

## Config

```yaml
invoicegenie:
  ocr:
    min-confidence: ${INVOICEGENIE_OCR_MIN_CONFIDENCE:0.45}
```
