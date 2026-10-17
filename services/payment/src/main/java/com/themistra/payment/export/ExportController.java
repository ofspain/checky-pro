package com.themistra.payment.export;

import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/exports/transactions} (R27). {@code ROLE_MERCHANT} only (L10); every row
 * returned belongs to the caller's own {@code merchantAccountUuid} by construction (R28's own
 * rule "extends here" per {@code design.md} O5 - there is no per-resource lookup to get wrong,
 * unlike {@code InvoiceController}'s {@code GET /invoices/{invoiceUuid}}).
 *
 * <p>Format selection (Phase 2/4, no existing platform precedent to mirror - invented here):
 * {@code format} query param, if present, must be exactly {@code csv} or {@code json}
 * (case-insensitive) or this is a typed 400; if absent, {@code Accept: text/csv} selects CSV and
 * anything else (including absent/{@code *}/{@code *}/{@code application/json}) selects JSON.</p>
 */
@RestController
@RequestMapping("/api/v1/exports/transactions")
public class ExportController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv");

    private final TransactionExportService exportService;

    public ExportController(TransactionExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<?> export(@RequestParam Instant from, @RequestParam Instant to,
                                     @RequestParam(required = false) String format,
                                     @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
                                     @AuthenticationPrincipal Jwt jwt) {
        if (from.isAfter(to)) {
            throw new InvalidExportRequestException("'from' must not be after 'to'");
        }
        UUID merchantAccountUuid = merchantAccountUuidFrom(jwt);
        boolean csv = wantsCsv(format, accept);

        List<TransactionExportRow> rows = exportService.export(merchantAccountUuid, from, to);

        if (csv) {
            // Phase 9 Kimi Finding 6, accepted: without Content-Disposition, a browser renders
            // the CSV inline rather than downloading it as a file. Epoch seconds, not
            // Instant.toString()'s own colon-containing ISO form, which Windows filenames forbid.
            String filename = "transactions-" + from.getEpochSecond() + "-" + to.getEpochSecond() + ".csv";
            return ResponseEntity.ok()
                    .contentType(CSV)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(toCsv(rows));
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rows);
    }

    private static boolean wantsCsv(String format, String accept) {
        if (format != null) {
            if (format.equalsIgnoreCase("csv")) {
                return true;
            }
            if (format.equalsIgnoreCase("json")) {
                return false;
            }
            throw new InvalidExportRequestException(
                    "'format' must be 'csv' or 'json' if present, got: " + format);
        }
        if (accept == null) {
            return false;
        }
        // Phase 9 Kimi Finding 2, accepted: proper media-type matching (respects q=0 exclusion),
        // not a naive substring check that would also match e.g. "application/text/csv-foo".
        // equalsTypeAndSubtype, not isCompatibleWith - the frozen brief (Phase 4) says a bare
        // "*/*" (or any other wildcard) must still select the JSON default, and isCompatibleWith
        // would wrongly treat "*/*" as a CSV match since a wildcard is compatible with anything.
        // A malformed Accept value is not itself a validation error worth a 400 for - fall back
        // to the JSON default exactly as an absent header would, rather than let
        // InvalidMediaTypeException escape unhandled.
        try {
            return MediaType.parseMediaTypes(accept).stream()
                    .anyMatch(candidate -> CSV.equalsTypeAndSubtype(candidate) && !"0".equals(candidate.getParameter("q")));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }

    private static String toCsv(List<TransactionExportRow> rows) {
        StringBuilder csv = new StringBuilder(
                "invoiceUuid,paymentUuid,receiptUuid,chain,txHash,tokenContractAddress,"
                        + "tokenDecimals,amount,payToAddress,finalizedAt,attestedAt,receiptUri\n");
        for (TransactionExportRow row : rows) {
            csv.append(csvField(row.invoiceUuid().toString())).append(',')
                    .append(csvField(row.paymentUuid().toString())).append(',')
                    .append(csvField(row.receiptUuid().toString())).append(',')
                    .append(csvField(row.chain())).append(',')
                    .append(csvField(row.txHash())).append(',')
                    .append(csvField(row.tokenContractAddress())).append(',')
                    .append(row.tokenDecimals()).append(',')
                    .append(csvField(row.amount())).append(',')
                    .append(csvField(row.payToAddress())).append(',')
                    .append(csvField(row.finalizedAt().toString())).append(',')
                    .append(csvField(row.attestedAt().toString())).append(',')
                    .append(csvField(row.receiptUri())).append('\n');
        }
        return csv.toString();
    }

    /** Minimal RFC 4180 escaping (Phase 3 Kimi Finding 1, accepted at Phase 4; widened at Phase
     * 9 Kimi Finding 1 to also catch a bare {@code \r}, not just {@code \n} - RFC 4180 treats
     * CRLF as the record terminator and many parsers treat a lone CR as a line break too): every
     * real export value is constitutionally comma/newline-free, but nothing in the schema
     * enforces that at the database level - a tampered or raw-inserted row must still produce a
     * structurally valid CSV row, not silently shift columns. */
    private static String csvField(String value) {
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Mirrors {@code InvoiceController.merchantAccountUuidFrom} exactly (duplicated per-module
     * helper, not shared plumbing - established precedent). */
    private static UUID merchantAccountUuidFrom(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidExportRequestException("sub claim is missing");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new InvalidExportRequestException("sub claim is not a valid UUID: " + subject);
        }
    }
}
