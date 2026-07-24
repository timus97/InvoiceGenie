package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.ChequeOcrUseCase;
import com.invoicegenie.ar.domain.service.ChequeOcrParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Application service: orchestrates cheque OCR parsing.
 * PDF text extraction is delegated via {@link PdfTextExtractor} port adapter when present;
 * falls back to treating bytes as UTF-8 text for tests/simple dumps.
 */
public class ChequeOcrApplicationService implements ChequeOcrUseCase {

    public interface PdfTextExtractor {
        String extractText(String fileName, byte[] pdfBytes);
    }

    private final PdfTextExtractor pdfTextExtractor;

    public ChequeOcrApplicationService(PdfTextExtractor pdfTextExtractor) {
        this.pdfTextExtractor = pdfTextExtractor;
    }

    public ChequeOcrApplicationService() {
        this.pdfTextExtractor = null;
    }

    @Override
    public List<ChequeOcrParser.ExtractedCheque> parseTexts(List<TextBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        List<ChequeOcrParser.ExtractedCheque> all = new ArrayList<>();
        for (TextBlock block : blocks) {
            if (block == null || block.text() == null || block.text().isBlank()) {
                continue;
            }
            all.addAll(ChequeOcrParser.parseDocument(
                    block.sourceName() != null ? block.sourceName() : "text",
                    block.text()));
        }
        return all;
    }

    @Override
    public List<ChequeOcrParser.ExtractedCheque> parsePdf(String fileName, byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return List.of();
        }
        String name = fileName != null ? fileName : "upload.pdf";
        String text;
        if (pdfTextExtractor != null) {
            text = pdfTextExtractor.extractText(name, pdfBytes);
        } else {
            text = new String(pdfBytes, StandardCharsets.UTF_8);
        }
        if (text == null || text.isBlank()) {
            return List.of(new ChequeOcrParser.ExtractedCheque(
                    name, 1, null, null, "USD", null, null, null, null,
                    "No extractable text — scanned PDF requires image OCR (client Tesseract)",
                    0.05, ""));
        }
        return ChequeOcrParser.parseDocument(name, text);
    }
}