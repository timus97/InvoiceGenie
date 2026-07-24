package com.invoicegenie.ar.adapter.api.ocr;

import com.invoicegenie.ar.application.service.ChequeOcrApplicationService;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;

/**
 * PDF text extraction via Apache PDFBox.
 * Multi-page PDFs are joined with form-feed so bulk cheque pages stay separable.
 */
@ApplicationScoped
public class PdfBoxTextExtractor implements ChequeOcrApplicationService.PdfTextExtractor {

    private static final Logger LOG = Logger.getLogger(PdfBoxTextExtractor.class);

    @Override
    public String extractText(String fileName, byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length < 5) {
            return "";
        }
        String magic = new String(pdfBytes, 0, Math.min(5, pdfBytes.length));
        if (!magic.startsWith("%PDF")) {
            return new String(pdfBytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            int pages = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            StringBuilder all = new StringBuilder();
            for (int p = 1; p <= pages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String pageText = stripper.getText(doc);
                if (pageText != null && !pageText.isBlank()) {
                    if (all.length() > 0) {
                        // Form-feed + explicit separator for bulk multi-page cheques
                        all.append('\f').append("\n---\n").append("PAGE ").append(p).append('\n');
                    }
                    all.append(pageText.trim()).append('\n');
                }
            }
            LOG.infof("Extracted %d chars from PDF %s (%d pages)", all.length(), fileName, pages);
            return all.toString();
        } catch (Exception e) {
            LOG.warnf(e, "PDF text extraction failed for %s", fileName);
            return "";
        }
    }
}