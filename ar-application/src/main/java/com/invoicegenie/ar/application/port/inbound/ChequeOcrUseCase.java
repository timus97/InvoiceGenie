package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.service.ChequeOcrParser;

import java.util.List;

/**
 * Inbound port: cheque OCR extraction from text / uploaded documents.
 */
public interface ChequeOcrUseCase {

    /**
     * Parse already-extracted OCR text blocks (e.g. from browser Tesseract or PDF pages).
     */
    List<ChequeOcrParser.ExtractedCheque> parseTexts(List<TextBlock> blocks);

    /**
     * Extract text from PDF bytes (digital or text-layer PDFs) and parse cheques.
     * Scanned image-only PDFs return empty extraction with a note — use client OCR then parseTexts.
     */
    List<ChequeOcrParser.ExtractedCheque> parsePdf(String fileName, byte[] pdfBytes);

    record TextBlock(String sourceName, String text) {}
}