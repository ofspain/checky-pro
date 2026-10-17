package com.themistra.payment.export;

import com.themistra.payment.common.ApiExceptionHandler;
import com.themistra.payment.common.JwtRoleAuthoritiesConverter;
import com.themistra.payment.common.ResourceServerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T22 (R27). Mirrors {@code InvoiceControllerTest}'s own {@code @WebMvcTest} +
 * {@code SecurityMockMvcRequestPostProcessors.jwt()} shape exactly - the real security beans are
 * imported so {@code jwt()} exercises the real {@code ROLE_MERCHANT} mapping, not a stub.
 */
@WebMvcTest(controllers = ExportController.class)
@Import({ResourceServerConfig.class, JwtRoleAuthoritiesConverter.class, ExportExceptionHandler.class,
        ApiExceptionHandler.class})
class ExportControllerTest {

    private static final UUID MERCHANT_ACCOUNT_UUID = UUID.randomUUID();
    private static final UUID OTHER_UUID = UUID.randomUUID();
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-12-31T23:59:59Z");
    private static final String PATH = "/api/v1/exports/transactions";

    private static final TransactionExportRow ROW = new TransactionExportRow(
            OTHER_UUID, OTHER_UUID, OTHER_UUID, "ETHEREUM", "0xhash", "0xtoken", (short) 6,
            "1000", "0xdest", Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T01:00:00Z"),
            "s3://bucket/prefix.json");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionExportService exportService;

    private static RequestPostProcessor merchantJwt() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(builder -> builder.subject(MERCHANT_ACCOUNT_UUID.toString()).claim("roles", List.of("MERCHANT")))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
    }

    private static RequestPostProcessor nonMerchantJwt() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(builder -> builder.subject(MERCHANT_ACCOUNT_UUID.toString()).claim("roles", List.of("USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static String query() {
        return "?from=" + FROM + "&to=" + TO;
    }

    /** Named test (R27). Full eleven-field JSON shape, amount as a quoted decimal string (L6),
     * not a JSON number. */
    @Test
    void shouldExportTaxReadyHistoryForMerchantAndDateRange() throws Exception {
        when(exportService.export(eq(MERCHANT_ACCOUNT_UUID), eq(FROM), eq(TO))).thenReturn(List.of(ROW));

        mockMvc.perform(get(PATH + query()).with(merchantJwt()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].invoiceUuid").value(OTHER_UUID.toString()))
                .andExpect(jsonPath("$[0].paymentUuid").value(OTHER_UUID.toString()))
                .andExpect(jsonPath("$[0].receiptUuid").value(OTHER_UUID.toString()))
                .andExpect(jsonPath("$[0].chain").value("ETHEREUM"))
                .andExpect(jsonPath("$[0].txHash").value("0xhash"))
                .andExpect(jsonPath("$[0].tokenContractAddress").value("0xtoken"))
                .andExpect(jsonPath("$[0].tokenDecimals").value(6))
                .andExpect(jsonPath("$[0].amount").value("1000"))
                .andExpect(jsonPath("$[0].payToAddress").value("0xdest"))
                .andExpect(jsonPath("$[0].finalizedAt").value("2026-06-01T00:00:00Z"))
                .andExpect(jsonPath("$[0].attestedAt").value("2026-06-01T01:00:00Z"))
                .andExpect(jsonPath("$[0].receiptUri").value("s3://bucket/prefix.json"));

        verify(exportService).export(MERCHANT_ACCOUNT_UUID, FROM, TO);
    }

    @Test
    void shouldReturnEmptyJsonArrayWhenNoMatchingRows() throws Exception {
        when(exportService.export(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get(PATH + query()).with(merchantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturnCsvWhenFormatParamIsCsv() throws Exception {
        when(exportService.export(any(), any(), any())).thenReturn(List.of(ROW));

        mockMvc.perform(get(PATH + query() + "&format=csv").with(merchantJwt()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith(
                        "invoiceUuid,paymentUuid,receiptUuid,chain,txHash,tokenContractAddress,"
                                + "tokenDecimals,amount,payToAddress,finalizedAt,attestedAt,receiptUri\n")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(OTHER_UUID + ",")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ETHEREUM,0xhash,0xtoken,6,1000,0xdest")));
    }

    /** Phase 9 Kimi Finding 6: a CSV response must carry a download-triggering header. Phase 11
     * Kimi Gap 8 (corrected): asserts the exact header, not just a prefix/suffix - Kimi's own
     * suggested exact string assumed the raw ISO instant form, but {@code ExportController} uses
     * epoch seconds specifically to avoid the colons an ISO instant contains, which Windows
     * forbids in filenames (see the controller's own comment at the filename-building line). */
    @Test
    void shouldAddContentDispositionHeaderForCsvResponse() throws Exception {
        when(exportService.export(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get(PATH + query() + "&format=csv").with(merchantJwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"transactions-"
                        + FROM.getEpochSecond() + "-" + TO.getEpochSecond() + ".csv\""));
    }

    /** Phase 11 Kimi Gap 1: AC6 requires a header-only CSV (not an error) when nothing matches -
     * previously only the empty-JSON path was tested. */
    @Test
    void shouldReturnCsvHeaderOnlyWhenNoMatchingRows() throws Exception {
        when(exportService.export(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get(PATH + query() + "&format=csv").with(merchantJwt()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(
                        "invoiceUuid,paymentUuid,receiptUuid,chain,txHash,tokenContractAddress,"
                                + "tokenDecimals,amount,payToAddress,finalizedAt,attestedAt,receiptUri\n"));
    }

    @Test
    void shouldReturnJsonWhenFormatParamIsJson() throws Exception {
        when(exportService.export(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get(PATH + query() + "&format=json").with(merchantJwt())
                        .header("Accept", "text/csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void shouldReturnCsvWhenAcceptHeaderIsTextCsv() throws Exception {
        when(exportService.export(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get(PATH + query()).with(merchantJwt()).header("Accept", "text/csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    /** Phase 9 Kimi Finding 2's own regression check: a bare wildcard must still select the
     * JSON default (Phase 4 frozen brief), not CSV - a wildcard is compatible with everything,
     * which is exactly the bug the fix had to avoid reintroducing. */
    @Test
    void shouldReturnJsonForWildcardAcceptHeader() throws Exception {
        when(exportService.export(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get(PATH + query()).with(merchantJwt()).header("Accept", "*/*"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void shouldReturnJsonForMalformedAcceptHeader() throws Exception {
        when(exportService.export(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get(PATH + query()).with(merchantJwt()).header("Accept", "garbage!!!"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void shouldPreferFormatParamOverConflictingAcceptHeader() throws Exception {
        when(exportService.export(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get(PATH + query() + "&format=csv").with(merchantJwt()).header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    void shouldReturn400ForInvalidFormatValue() throws Exception {
        mockMvc.perform(get(PATH + query() + "&format=xml").with(merchantJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid export request"));
    }

    @Test
    void shouldReturn400ForMissingFromParam() throws Exception {
        mockMvc.perform(get(PATH + "?to=" + TO).with(merchantJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Missing request parameter"))
                .andExpect(jsonPath("$.detail").value("'from' is required"));
    }

    @Test
    void shouldReturn400ForMissingToParam() throws Exception {
        mockMvc.perform(get(PATH + "?from=" + FROM).with(merchantJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing request parameter"))
                .andExpect(jsonPath("$.detail").value("'to' is required"));
    }

    @Test
    void shouldReturn400ForFromAfterTo() throws Exception {
        mockMvc.perform(get(PATH + "?from=" + TO + "&to=" + FROM).with(merchantJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid export request"))
                .andExpect(jsonPath("$.detail").value("'from' must not be after 'to'"));
    }

    @Test
    void shouldReturn400ForMalformedFromValue() throws Exception {
        mockMvc.perform(get(PATH + "?from=not-an-instant&to=" + TO).with(merchantJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request parameter"));
    }

    /** Phase 11 Kimi Gap 5: the existing malformed-value test only covered `from` - a Spring-
     * binding bug affecting only the second parameter would not otherwise be caught. */
    @Test
    void shouldReturn400ForMalformedToValue() throws Exception {
        mockMvc.perform(get(PATH + "?from=" + FROM + "&to=not-an-instant").with(merchantJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request parameter"));
    }

    @Test
    void shouldReturn400ForMissingSubClaim() throws Exception {
        RequestPostProcessor missingSubJwt = SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(builder -> builder.claim("roles", List.of("MERCHANT"))
                        .claims(claims -> claims.remove(org.springframework.security.oauth2.jwt.JwtClaimNames.SUB)))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));

        mockMvc.perform(get(PATH + query()).with(missingSubJwt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid export request"))
                .andExpect(jsonPath("$.detail").value("sub claim is missing"));
    }

    /** Phase 11 Kimi Gap 2: the missing-sub branch was tested, but
     * {@code merchantAccountUuidFrom}'s second branch (a present but non-UUID subject) was not -
     * a regression that broke that catch block would otherwise return 500, not the typed 400
     * AC7 requires. */
    @Test
    void shouldReturn400ForMalformedSubClaim() throws Exception {
        RequestPostProcessor malformedSubJwt = SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(builder -> builder.subject("not-a-uuid").claim("roles", List.of("MERCHANT")))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));

        mockMvc.perform(get(PATH + query()).with(malformedSubJwt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid export request"))
                .andExpect(jsonPath("$.detail").value("sub claim is not a valid UUID: not-a-uuid"));
    }

    @Test
    void shouldRejectNonMerchantCallerWithForbidden() throws Exception {
        mockMvc.perform(get(PATH + query()).with(nonMerchantJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void shouldRejectUnauthenticatedCallerWithUnauthorized() throws Exception {
        mockMvc.perform(get(PATH + query()))
                .andExpect(status().isUnauthorized());
    }

    /** Phase 9 Kimi Finding 1 (widened at Phase 4/9): a comma, quote, newline, or bare carriage
     * return in any field must still produce a structurally valid CSV row via escaping, not
     * silently shift columns. Exercised at the controller layer with a stubbed row - no need to
     * insert a malformed row into a real database to prove the escaping logic itself. */
    @Test
    void shouldEscapeCsvFieldsContainingSpecialCharacters() throws Exception {
        String dirtyValue = "0xdest,with\"quote\nand\rbreaks";
        TransactionExportRow dirtyRow = new TransactionExportRow(
                OTHER_UUID, OTHER_UUID, OTHER_UUID, "ETHEREUM", "0xhash", "0xtoken", (short) 6,
                "1000", dirtyValue, Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T01:00:00Z"), "s3://bucket/prefix.json");
        when(exportService.export(any(), any(), any())).thenReturn(List.of(dirtyRow));

        String body = mockMvc.perform(get(PATH + query() + "&format=csv").with(merchantJwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String expectedEscapedField = "\"0xdest,with\"\"quote\nand\rbreaks\"";
        org.assertj.core.api.Assertions.assertThat(body).contains(expectedEscapedField);

        // Phase 11 Kimi Gap 6: a substring match alone would still pass if an extra, unescaped
        // comma shifted every column after it - parse the body structurally (respecting quoted
        // newlines/commas, RFC 4180) and assert the exact column count and the exact round-trip
        // of the original, unescaped value, not just that the escaped text appears somewhere.
        List<List<String>> records = parseCsv(body);
        assertThat(records).hasSize(2);
        List<String> dataRow = records.get(1);
        assertThat(dataRow).hasSize(12);
        assertThat(dataRow.get(8)).isEqualTo(dirtyValue);
    }

    /** Minimal RFC 4180 parser, test-only: respects quoted fields that themselves contain a
     * comma or a literal newline/carriage-return - a naive {@code split("\n")} would break the
     * dirty row above apart incorrectly, since its one quoted field contains a real {@code \n}. */
    private static List<List<String>> parseCsv(String body) {
        List<List<String>> records = new java.util.ArrayList<>();
        List<String> fields = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < body.length() && body.charAt(i + 1) == '"') {
                    current.append('"');
                    i += 2;
                } else if (c == '"') {
                    inQuotes = false;
                    i++;
                } else {
                    current.append(c);
                    i++;
                }
            } else if (c == '"') {
                inQuotes = true;
                i++;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
                i++;
            } else if (c == '\n') {
                fields.add(current.toString());
                current.setLength(0);
                records.add(new java.util.ArrayList<>(fields));
                fields.clear();
                i++;
            } else {
                current.append(c);
                i++;
            }
        }
        if (current.length() > 0 || !fields.isEmpty()) {
            fields.add(current.toString());
            records.add(fields);
        }
        return records;
    }
}
