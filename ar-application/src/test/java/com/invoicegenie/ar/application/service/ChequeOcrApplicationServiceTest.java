package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.ChequeOcrUseCase;
import com.invoicegenie.ar.domain.service.ChequeOcrParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ChequeOcrApplicationService")
class ChequeOcrApplicationServiceTest {

    @Nested
    @DisplayName("parseTexts")
    class ParseTexts {
        @Test
        @DisplayName("empty or null blocks return empty")
        void empty() {
            ChequeOcrApplicationService service = new ChequeOcrApplicationService();
            assertTrue(service.parseTexts(null).isEmpty());
            assertTrue(service.parseTexts(List.of()).isEmpty());
        }

        @Test
        @DisplayName("skips blank blocks and parses text")
        void parses() {
            ChequeOcrApplicationService service = new ChequeOcrApplicationService();
            String text = "Cheque No: CHQ-999\nAmount: USD 250.00\nBank: Test Bank\nDate: 2025-01-15";
            List<ChequeOcrParser.ExtractedCheque> result = service.parseTexts(Arrays.asList(
                    null,
                    new ChequeOcrUseCase.TextBlock("scan1", "   "),
                    new ChequeOcrUseCase.TextBlock(null, text),
                    new ChequeOcrUseCase.TextBlock("scan2", text)
            ));
            assertFalse(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("parsePdf")
    class ParsePdf {
        @Test
        @DisplayName("empty bytes return empty")
        void emptyBytes() {
            ChequeOcrApplicationService service = new ChequeOcrApplicationService();
            assertTrue(service.parsePdf("x.pdf", null).isEmpty());
            assertTrue(service.parsePdf("x.pdf", new byte[0]).isEmpty());
        }

        @Test
        @DisplayName("UTF-8 fallback when no extractor")
        void utf8Fallback() {
            ChequeOcrApplicationService service = new ChequeOcrApplicationService();
            String text = "Cheque No: 123456\nAmount: $100.00\nBank Name: First Bank";
            List<ChequeOcrParser.ExtractedCheque> result =
                    service.parsePdf("cheque.txt", text.getBytes(StandardCharsets.UTF_8));
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("blank extract returns placeholder note")
        void blankExtract() {
            ChequeOcrApplicationService.PdfTextExtractor extractor = mock(ChequeOcrApplicationService.PdfTextExtractor.class);
            when(extractor.extractText(anyString(), any())).thenReturn("   ");
            ChequeOcrApplicationService service = new ChequeOcrApplicationService(extractor);

            List<ChequeOcrParser.ExtractedCheque> result =
                    service.parsePdf(null, "pdf".getBytes(StandardCharsets.UTF_8));

            assertEquals(1, result.size());
            assertTrue(result.get(0).notes() != null && result.get(0).notes().contains("No extractable text")
                    || result.get(0).toString().contains("No extractable text")
                    || result.get(0).confidence() < 0.1);
        }

        @Test
        @DisplayName("uses injected PdfTextExtractor")
        void usesExtractor() {
            ChequeOcrApplicationService.PdfTextExtractor extractor = mock(ChequeOcrApplicationService.PdfTextExtractor.class);
            String text = "Cheque Number: ABC-1\nAmount: USD 50.00\nBank: Co";
            when(extractor.extractText(eq("doc.pdf"), any())).thenReturn(text);
            ChequeOcrApplicationService service = new ChequeOcrApplicationService(extractor);

            List<ChequeOcrParser.ExtractedCheque> result =
                    service.parsePdf("doc.pdf", new byte[]{1, 2, 3});

            assertFalse(result.isEmpty());
            verify(extractor).extractText("doc.pdf", new byte[]{1, 2, 3});
        }
    }
}