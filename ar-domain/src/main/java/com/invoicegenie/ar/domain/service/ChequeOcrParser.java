package com.invoicegenie.ar.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts structured cheque fields from OCR / PDF text.
 *
 * <p>Bulk documents are segmented by locating every cheque-number marker (and
 * page breaks). Each segment is parsed independently with the matched cheque
 * number forced, so later cheques are never overwritten by earlier ones.
 */
public final class ChequeOcrParser {

    /**
     * Explicit cheque-number labels only (not bare "cheque date").
     * Group 1 = primary label form, group 2 = Check # form, group 3 = bare No/# form.
     */
    private static final Pattern CHEQUE_NO_MARK = Pattern.compile(
            "(?i)(?:cheque|check|chq)\\s*(?:no\\.?|number)\\s*[:.]?\\s*([A-Za-z0-9-]{3,32})"
                    + "|(?i)(?:cheque|check|chq)\\s*#\\s*([A-Za-z0-9-]{3,32})"
                    + "|(?i)\\b(?:no\\.?|#)\\s*([0-9]{4,16})\\b");

    private static final Pattern CHEQUE_NO = Pattern.compile(
            "(?i)(?:cheque|check|chq)\\s*(?:no\\.?|number|#)?\\s*[:.]?\\s*([A-Za-z0-9-]{3,32})");
    private static final Pattern CHEQUE_NO_ALT = Pattern.compile(
            "(?i)\\b(?:no\\.?|#)\\s*([0-9]{4,16})\\b");
    private static final Pattern AMOUNT_CURRENCY = Pattern.compile(
            "(?i)(?:amount|sum|total|pay(?:able)?)\\s*[:.]?\\s*(?:USD|EUR|GBP|CAD|AUD|INR|\\$|€|£)?\\s*"
                    + "([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern AMOUNT_PLAIN = Pattern.compile(
            "(?:USD|EUR|GBP|CAD|AUD|INR|\\$|€|£)\\s*"
                    + "([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern AMOUNT_STARS = Pattern.compile(
            "\\*{0,4}\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})|[0-9]+\\.[0-9]{2})\\s*\\*{0,4}");
    private static final Pattern CURRENCY = Pattern.compile(
            "\\b(USD|EUR|GBP|CAD|AUD|INR)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BANK = Pattern.compile(
            "(?i)(?:bank(?:\\s*name)?|drawn\\s*on|payor\\s*bank)\\s*[:.]?\\s*"
                    + "([A-Za-z0-9 .,&'-]{3,80}?)(?=\\s*(?:date|amount|branch|cheque|check|chq|pay\\s*to|\\n|$))");
    private static final Pattern BRANCH = Pattern.compile(
            "(?i)(?:branch|branch\\s*name|routing\\s*branch)\\s*[:.]?\\s*"
                    + "([A-Za-z0-9 .,&'-]{2,80}?)(?=\\s*(?:date|amount|bank|cheque|check|chq|\\n|$))");
    private static final Pattern DATE_LABELED = Pattern.compile(
            "(?i)(?:date|cheque\\s*date|check\\s*date|dated)\\s*[:.]?\\s*"
                    + "([0-9]{1,4}[-/\\.][0-9]{1,2}[-/\\.][0-9]{1,4}|[0-9]{1,2}\\s+[A-Za-z]{3,9}\\s+[0-9]{2,4})");
    private static final Pattern PAYEE = Pattern.compile(
            "(?i)(?:pay\\s*to(?:\\s*the\\s*order\\s*of)?|payee)\\s*[:.]?\\s*([A-Za-z0-9 .,&'-]{3,100})");
    private static final Pattern MICR = Pattern.compile(
            "([0-9]{6,12})\\s*[❚|:]?\\s*([0-9]{6,12})\\s*[❚|:]?\\s*([0-9]{4,12})");
    private static final Pattern PAGE_BREAK = Pattern.compile(
            "\\f|\\n\\s*-{3,}\\s*\\n|\\n\\s*={3,}\\s*\\n|\\n\\s*PAGE\\s+\\d+\\s*\\n",
            Pattern.CASE_INSENSITIVE);

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    );

    private record NumberHit(int start, int end, String number) {}

    private ChequeOcrParser() {}

    /**
     * Parse one or more cheques from OCR / PDF text (single or bulk).
     */
    public static List<ExtractedCheque> parseDocument(String sourceName, String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        String normalized = rawText.replace("\r\n", "\n").replace('\r', '\n');

        List<ExtractedCheque> results = new ArrayList<>();

        // 1) Preferred: one segment per explicit cheque-number marker
        List<NumberHit> hits = findChequeNumberHits(normalized);
        if (hits.size() >= 2) {
            results.addAll(parseByHits(sourceName, normalized, hits));
        }

        // 2) Page / separator based bulk (PDF pages joined with form-feed or ---)
        if (results.size() < 2 && PAGE_BREAK.matcher(normalized).find()) {
            String[] pages = PAGE_BREAK.split(normalized);
            List<ExtractedCheque> pageResults = new ArrayList<>();
            int pageIdx = 0;
            for (String page : pages) {
                if (page == null || page.isBlank() || page.trim().length() < 8) {
                    continue;
                }
                pageIdx++;
                List<NumberHit> pageHits = findChequeNumberHits(page);
                if (pageHits.size() >= 2) {
                    pageResults.addAll(parseByHits(sourceName + "#p" + pageIdx, page, pageHits));
                } else if (pageHits.size() == 1) {
                    pageResults.add(parseSegment(
                            sourceName, page, pageIdx, pageHits.get(0).number()));
                } else {
                    ExtractedCheque one = parseSingle(sourceName, page, pageIdx);
                    if (one.hasAnyField()) {
                        pageResults.add(one);
                    }
                }
            }
            if (pageResults.size() > results.size()) {
                results = pageResults;
            }
        }

        // 3) Single-cheque fallback (also covers exactly one marker)
        if (results.isEmpty()) {
            if (hits.size() == 1) {
                results.add(parseSegment(sourceName, normalized, 1, hits.get(0).number()));
            } else {
                ExtractedCheque single = parseSingle(sourceName, normalized, 1);
                if (single.hasAnyField()) {
                    results.add(single);
                }
            }
        }

        return dedupeByChequeNumber(results);
    }

    private static List<NumberHit> findChequeNumberHits(String text) {
        List<NumberHit> hits = new ArrayList<>();
        Matcher m = CHEQUE_NO_MARK.matcher(text);
        while (m.find()) {
            String num = firstNonNullGroup(m);
            if (num == null || num.isBlank()) {
                continue;
            }
            hits.add(new NumberHit(m.start(), m.end(), num.trim()));
        }
        return hits;
    }

    private static List<ExtractedCheque> parseByHits(String sourceName, String text, List<NumberHit> hits) {
        List<ExtractedCheque> out = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            NumberHit hit = hits.get(i);
            // Body starts at this cheque marker — never include previous cheque amount/number
            int from = hit.start();
            int to = (i + 1 < hits.size()) ? hits.get(i + 1).start() : text.length();
            String body = text.substring(from, to);
            // Optional header only for bank letterhead (before this marker, after previous)
            int prevEnd = (i > 0) ? hits.get(i - 1).end() : 0;
            int headerFrom = Math.max(prevEnd, from - 160);
            String header = text.substring(headerFrom, from);
            ExtractedCheque extracted = parseSegment(sourceName, body, header, i + 1, hit.number());
            if (extracted.hasAnyField()) {
                out.add(extracted);
            }
        }
        return out;
    }

    /**
     * Parse fields from a segment, forcing the cheque number from the bulk marker.
     * Amount/date/payee come only from {@code body}; bank may also use {@code header}.
     */
    private static ExtractedCheque parseSegment(
            String sourceName, String body, String header, int index, String forcedNumber) {
        ExtractedCheque base = parseSingle(sourceName, body, index);
        String number = (forcedNumber != null && !forcedNumber.isBlank())
                ? forcedNumber.trim()
                : base.chequeNumber();
        BigDecimal amount = base.amount();
        String bank = base.bankName();
        if (bank == null || bank.isBlank()) {
            bank = firstGroup(BANK, header != null ? header : "").map(String::trim).orElse(null);
        }
        String branch = base.bankBranch();
        if (branch == null || branch.isBlank()) {
            branch = firstGroup(BRANCH, header != null ? header : "").map(String::trim).orElse(null);
        }
        double confidence = scoreConfidence(number, amount, bank, base.chequeDate());
        return new ExtractedCheque(
                sourceName,
                index,
                number,
                amount,
                base.currencyCode(),
                bank,
                branch,
                base.chequeDate(),
                base.payeeHint(),
                base.notes(),
                confidence,
                base.rawSnippet()
        );
    }

    private static ExtractedCheque parseSegment(
            String sourceName, String segment, int index, String forcedNumber) {
        return parseSegment(sourceName, segment, "", index, forcedNumber);
    }

    private static String firstNonNullGroup(Matcher m) {
        for (int g = 1; g <= m.groupCount(); g++) {
            String v = m.group(g);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static List<ExtractedCheque> dedupeByChequeNumber(List<ExtractedCheque> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        Map<String, ExtractedCheque> byNumber = new LinkedHashMap<>();
        List<ExtractedCheque> noNumber = new ArrayList<>();
        for (ExtractedCheque c : input) {
            if (c.chequeNumber() == null || c.chequeNumber().isBlank()) {
                noNumber.add(c);
                continue;
            }
            String key = c.chequeNumber().trim().toUpperCase(Locale.ROOT);
            ExtractedCheque existing = byNumber.get(key);
            if (existing == null || c.confidence() > existing.confidence()) {
                byNumber.put(key, c);
            }
        }
        List<ExtractedCheque> out = new ArrayList<>(byNumber.values());
        out.addAll(noNumber);
        return out;
    }

    public static ExtractedCheque parseSingle(String sourceName, String text, int pageOrIndex) {
        String t = text == null ? "" : text;
        String chequeNumber = firstGroup(CHEQUE_NO, t)
                .or(() -> firstGroup(CHEQUE_NO_ALT, t))
                .or(() -> micrChequeNumber(t))
                .orElse(null);
        BigDecimal amount = firstGroup(AMOUNT_CURRENCY, t)
                .or(() -> firstGroup(AMOUNT_PLAIN, t))
                .or(() -> firstGroup(AMOUNT_STARS, t))
                .map(ChequeOcrParser::parseAmount)
                .orElse(null);
        String currency = firstGroup(CURRENCY, t).map(s -> s.toUpperCase(Locale.ROOT)).orElse("USD");
        String bankName = firstGroup(BANK, t).map(String::trim).orElse(null);
        String bankBranch = firstGroup(BRANCH, t).map(String::trim).orElse(null);
        LocalDate chequeDate = firstGroup(DATE_LABELED, t).flatMap(ChequeOcrParser::parseDate).orElse(null);
        String payee = firstGroup(PAYEE, t).map(String::trim).orElse(null);

        String notes = "OCR source=" + (sourceName == null ? "upload" : sourceName)
                + (pageOrIndex > 0 ? " segment=" + pageOrIndex : "");
        double confidence = scoreConfidence(chequeNumber, amount, bankName, chequeDate);

        return new ExtractedCheque(
                sourceName,
                pageOrIndex,
                chequeNumber,
                amount,
                currency,
                bankName,
                bankBranch,
                chequeDate,
                payee,
                notes,
                confidence,
                t.length() > 400 ? t.substring(0, 400) + "…" : t
        );
    }

    private static Optional<String> micrChequeNumber(String text) {
        Matcher m = MICR.matcher(text);
        if (m.find()) {
            return Optional.ofNullable(m.group(3));
        }
        return Optional.empty();
    }

    private static Optional<String> firstGroup(Pattern p, String text) {
        Matcher m = p.matcher(text);
        if (m.find()) {
            return Optional.ofNullable(m.group(1)).map(String::trim);
        }
        return Optional.empty();
    }

    private static BigDecimal parseAmount(String raw) {
        String cleaned = raw.replace(",", "").replace(" ", "");
        return new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
    }

    private static Optional<LocalDate> parseDate(String raw) {
        String s = raw.trim();
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(s, f));
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return Optional.empty();
    }

    private static double scoreConfidence(String number, BigDecimal amount, String bank, LocalDate date) {
        double score = 0.15;
        if (number != null && !number.isBlank()) score += 0.3;
        if (amount != null && amount.signum() > 0) score += 0.3;
        if (bank != null && !bank.isBlank()) score += 0.15;
        if (date != null) score += 0.1;
        return Math.min(1.0, score);
    }

    public record ExtractedCheque(
            String sourceFile,
            int segmentIndex,
            String chequeNumber,
            BigDecimal amount,
            String currencyCode,
            String bankName,
            String bankBranch,
            LocalDate chequeDate,
            String payeeHint,
            String notes,
            double confidence,
            String rawSnippet
    ) {
        public ExtractedCheque {
            Objects.requireNonNull(currencyCode, "currencyCode");
        }

        public boolean hasAnyField() {
            return (chequeNumber != null && !chequeNumber.isBlank())
                    || (amount != null && amount.signum() > 0)
                    || (bankName != null && !bankName.isBlank())
                    || chequeDate != null;
        }

        public boolean isCompleteEnough() {
            return chequeNumber != null && !chequeNumber.isBlank()
                    && amount != null && amount.signum() > 0;
        }
    }
}