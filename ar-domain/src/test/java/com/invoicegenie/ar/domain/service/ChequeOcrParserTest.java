package com.invoicegenie.ar.domain.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ChequeOcrParserTest {

    @Test
    void parsesSingleCheque() {
        String text = """
                FIRST NATIONAL BANK
                Branch: Downtown
                Cheque No: 452198
                Date: 2026-07-15
                Pay to the order of: Acme Corp
                Amount: USD 1,250.50
                """;
        var result = ChequeOcrParser.parseDocument("check1.png", text);
        assertEquals(1, result.size());
        var c = result.get(0);
        assertEquals("452198", c.chequeNumber());
        assertEquals(0, c.amount().compareTo(new BigDecimal("1250.50")));
        assertEquals("USD", c.currencyCode());
        assertNotNull(c.chequeDate());
        assertTrue(c.isCompleteEnough());
    }

    @Test
    void parsesBulkTwoChequesSamePage() {
        String text = """
                Cheque No: 1001 Amount: $100.00 Bank: Alpha Bank Date: 2026-07-01
                Cheque No: 1002 Amount: $200.00 Bank: Beta Bank Date: 2026-07-02
                """;
        List<ChequeOcrParser.ExtractedCheque> list = ChequeOcrParser.parseDocument("bulk.txt", text);
        assertEquals(2, list.size(), "expected both cheques, got: " + summarize(list));
        assertEquals("1001", list.get(0).chequeNumber());
        assertEquals("1002", list.get(1).chequeNumber());
        assertEquals(0, list.get(0).amount().compareTo(new BigDecimal("100.00")));
        assertEquals(0, list.get(1).amount().compareTo(new BigDecimal("200.00")));
    }

    @Test
    void parsesBulkThreeChequesWithSeparators() {
        String text = """
                Cheque No: A100 Amount: USD 10.00 Bank Name: One Bank
                ---
                Cheque No: A200 Amount: USD 20.50 Bank Name: Two Bank
                ---
                Cheque No: A300 Amount: USD 30.00 Bank Name: Three Bank
                """;
        List<ChequeOcrParser.ExtractedCheque> list = ChequeOcrParser.parseDocument("sep.txt", text);
        assertEquals(3, list.size(), "expected 3 cheques, got: " + summarize(list));
        assertEquals(
                List.of("A100", "A200", "A300"),
                list.stream().map(ChequeOcrParser.ExtractedCheque::chequeNumber).collect(Collectors.toList()));
    }

    @Test
    void parsesBulkMultiPageFormFeed() {
        String text = "Cheque No: 9001 Amount: $15.00 Bank: PageOne Bank\f\n---\nPAGE 2\n"
                + "Cheque No: 9002 Amount: $25.00 Bank: PageTwo Bank\f\n---\nPAGE 3\n"
                + "Cheque No: 9003 Amount: $35.00 Bank: PageThree Bank";
        List<ChequeOcrParser.ExtractedCheque> list = ChequeOcrParser.parseDocument("multi.pdf", text);
        assertEquals(3, list.size(), "expected 3 page cheques, got: " + summarize(list));
    }

    @Test
    void parsesBulkCheckHashStyle() {
        String text = """
                Check # 555111 Amount: 40.00
                Check # 555222 Amount: 50.00
                Check # 555333 Amount: 60.00
                """;
        List<ChequeOcrParser.ExtractedCheque> list = ChequeOcrParser.parseDocument("hash.txt", text);
        assertEquals(3, list.size(), "expected 3 check # rows, got: " + summarize(list));
    }

    @Test
    void emptyTextReturnsEmpty() {
        assertTrue(ChequeOcrParser.parseDocument("x", "   ").isEmpty());
    }

    private static String summarize(List<ChequeOcrParser.ExtractedCheque> list) {
        return list.stream()
                .map(c -> c.chequeNumber() + "=" + c.amount())
                .collect(Collectors.joining(", "));
    }
}