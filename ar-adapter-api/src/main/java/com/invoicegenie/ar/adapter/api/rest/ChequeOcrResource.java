package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.application.port.inbound.ChequeOcrUseCase;
import com.invoicegenie.ar.domain.service.ChequeOcrParser;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cheque OCR endpoints: parse OCR text and upload multi-file / PDF bulk processing.
 */
@Path("/api/v1/cheques/ocr")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Cheque OCR", description = "OCR extraction for cheques (images via client text, PDFs server-side)")
public class ChequeOcrResource {

    private final ChequeOcrUseCase chequeOcrUseCase;

    public ChequeOcrResource(ChequeOcrUseCase chequeOcrUseCase) {
        this.chequeOcrUseCase = chequeOcrUseCase;
    }

    @POST
    @Path("/parse")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Parse cheque fields from OCR text blocks (browser Tesseract or external OCR)")
    public Response parseTexts(ParseRequestDto body) {
        if (body == null || body.blocks() == null || body.blocks().isEmpty()) {
            return Response.status(400)
                    .entity(new ErrorResponse("VALIDATION_ERROR", "blocks required"))
                    .build();
        }
        List<ChequeOcrUseCase.TextBlock> blocks = body.blocks().stream()
                .map(b -> new ChequeOcrUseCase.TextBlock(b.sourceName(), b.text()))
                .toList();
        List<ChequeOcrParser.ExtractedCheque> extracted = chequeOcrUseCase.parseTexts(blocks);
        return Response.ok(new OcrResultDto(extracted.stream().map(this::toDto).toList(), extracted.size())).build();
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Upload cheque images/PDFs for bulk OCR (PDF text layer + image placeholders)")
    public Response upload(@RestForm("files") List<FileUpload> files) {
        if (files == null || files.isEmpty()) {
            return Response.status(400)
                    .entity(new ErrorResponse("VALIDATION_ERROR", "files form field required (multipart)"))
                    .build();
        }
        List<ChequeOcrParser.ExtractedCheque> all = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (FileUpload file : files) {
            if (file == null) continue;
            String name = file.fileName() != null ? file.fileName() : "upload";
            String lower = name.toLowerCase(Locale.ROOT);
            try {
                byte[] bytes = Files.readAllBytes(file.uploadedFile());
                if (lower.endsWith(".pdf")) {
                    all.addAll(chequeOcrUseCase.parsePdf(name, bytes));
                } else if (isImage(lower)) {
                    // Images: server cannot run Tesseract without native binaries.
                    // Return a placeholder so UI can run client OCR and re-submit via /parse.
                    warnings.add(name + ": image requires client-side OCR (use browser Tesseract then /parse)");
                    all.add(new ChequeOcrParser.ExtractedCheque(
                            name, 1, null, null, "USD", null, null, null, null,
                            "IMAGE_PENDING_CLIENT_OCR", 0.0, ""));
                } else if (lower.endsWith(".txt") || lower.endsWith(".csv")) {
                    String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    all.addAll(chequeOcrUseCase.parseTexts(List.of(new ChequeOcrUseCase.TextBlock(name, text))));
                } else {
                    // Attempt PDF magic or text
                    String magic = new String(bytes, 0, Math.min(5, bytes.length));
                    if (magic.startsWith("%PDF")) {
                        all.addAll(chequeOcrUseCase.parsePdf(name, bytes));
                    } else {
                        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                        all.addAll(chequeOcrUseCase.parseTexts(List.of(new ChequeOcrUseCase.TextBlock(name, text))));
                    }
                }
            } catch (IOException e) {
                warnings.add(name + ": read failed — " + e.getMessage());
            }
        }

        return Response.ok(new OcrUploadResultDto(
                all.stream().map(this::toDto).toList(),
                all.size(),
                warnings
        )).build();
    }

    private static boolean isImage(String lower) {
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".tif")
                || lower.endsWith(".tiff") || lower.endsWith(".bmp");
    }

    private ExtractedChequeDto toDto(ChequeOcrParser.ExtractedCheque c) {
        return new ExtractedChequeDto(
                c.sourceFile(),
                c.segmentIndex(),
                c.chequeNumber(),
                c.amount(),
                c.currencyCode(),
                c.bankName(),
                c.bankBranch(),
                c.chequeDate() != null ? c.chequeDate().toString() : null,
                c.payeeHint(),
                c.notes(),
                c.confidence(),
                c.rawSnippet(),
                c.isCompleteEnough()
        );
    }

    public record ParseRequestDto(List<TextBlockDto> blocks) {}
    public record TextBlockDto(String sourceName, String text) {}
    public record ExtractedChequeDto(
            String sourceFile,
            int segmentIndex,
            String chequeNumber,
            java.math.BigDecimal amount,
            String currencyCode,
            String bankName,
            String bankBranch,
            String chequeDate,
            String payeeHint,
            String notes,
            double confidence,
            String rawSnippet,
            boolean completeEnough
    ) {}
    public record OcrResultDto(List<ExtractedChequeDto> cheques, int count) {}
    public record OcrUploadResultDto(List<ExtractedChequeDto> cheques, int count, List<String> warnings) {}
}