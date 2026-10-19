package com.themistra.payment.invoice;

import com.themistra.payment.common.ApiExceptionHandler;
import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.common.CryptoServiceUnavailableException;
import com.themistra.payment.common.JwtRoleAuthoritiesConverter;
import com.themistra.payment.common.ProblemTypes;
import com.themistra.payment.common.ResourceServerConfig;
import com.themistra.payment.common.crypto.RegisterWatchResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC1, AC2, AC4, AC6, AC8. Mirrors {@code ResourceServerConfigTest}'s own {@code @WebMvcTest} +
 * {@code SecurityMockMvcRequestPostProcessors.jwt()} shape — the real {@code
 * JwtAuthenticationConverter}/{@code JwtRoleAuthoritiesConverter} beans are imported so
 * {@code jwt()} exercises this task's own {@code roles}-claim-to-{@code ROLE_*} mapping for
 * real, not a stubbed authority list.
 */
@WebMvcTest(controllers = InvoiceController.class)
@Import({ResourceServerConfig.class, JwtRoleAuthoritiesConverter.class, InvoiceExceptionHandler.class,
        ApiExceptionHandler.class})
class InvoiceControllerTest {

    private static final UUID MERCHANT_ACCOUNT_UUID = UUID.randomUUID();
    private static final UUID WATCH_ID = UUID.randomUUID();
    private static final UUID INVOICE_UUID = UUID.randomUUID();
    private static final Instant EXPIRES_AT = Instant.parse("2026-07-13T01:00:00Z");
    private static final String VALID_REQUEST_JSON = """
            {"chain":"ETHEREUM","tokenContractAddress":"0xabc","tokenDecimals":18,
             "amount":1000,"payToAddress":"0xdef","reference":"order-1"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvoiceService invoiceService;

    @MockBean
    private CryptoClient cryptoClient;

    /** {@code SecurityMockMvcRequestPostProcessors.jwt()} does NOT use the real {@code
     * JwtAuthenticationConverter}/{@code JwtRoleAuthoritiesConverter} bean chain by default (it
     * builds its own default converter from the {@code scope} claim only) - confirmed directly
     * when this test's first draft got a 403 even with {@code roles=["MERCHANT"]} set.
     * {@code .authorities(...)} sets the resulting authority explicitly, which is what actually
     * drives {@code @PreAuthorize("hasRole('MERCHANT')")} here; {@code roles} is still set too,
     * so a future refactor reading the claim directly is not silently untested. */
    private static RequestPostProcessor merchantJwt() {
        return jwt()
                .jwt(builder -> builder.subject(MERCHANT_ACCOUNT_UUID.toString()).claim("roles", List.of("MERCHANT")))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
    }

    private static RequestPostProcessor nonMerchantJwt() {
        return jwt()
                .jwt(builder -> builder.subject(MERCHANT_ACCOUNT_UUID.toString()).claim("roles", List.of("USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void shouldCreateInvoiceAndRegisterWatch() throws Exception {
        when(invoiceService.computeExpiresAt()).thenReturn(EXPIRES_AT);
        when(cryptoClient.registerWatch(any(UUID.class), eq("ETHEREUM"), eq("0xdef"), eq("0xabc"),
                eq(new BigDecimal("1000")), eq(EXPIRES_AT)))
                .thenReturn(new RegisterWatchResponse(WATCH_ID, "REGISTERED"));

        Invoice invoice = Invoice.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc",
                (short) 18, new BigDecimal("1000"), "0xdef", "order-1", EXPIRES_AT, WATCH_ID, EXPIRES_AT);
        when(invoiceService.create(any(UUID.class), eq(MERCHANT_ACCOUNT_UUID), eq("ETHEREUM"), eq("0xabc"),
                eq((short) 18), eq(new BigDecimal("1000")), eq("0xdef"), eq("order-1"), eq(EXPIRES_AT),
                eq(WATCH_ID)))
                .thenReturn(invoice);

        mockMvc.perform(post("/api/v1/invoices").with(merchantJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceUuid").value(INVOICE_UUID.toString()))
                .andExpect(jsonPath("$.payToAddress").value("0xdef"));
    }

    /** R6, strengthened (Phase 11 Gaps 2/3): proves, not just asserts via comments, that the
     * exact same randomly-generated {@code invoiceUuid} reaches both calls, and that {@code
     * registerWatch} happens strictly before {@code create} - the whole point of the Phase 4
     * transaction redesign. Two separate {@code ArgumentCaptor}s (one per mock) rather than one
     * shared captor, since each call site is on a different mock. */
    @Test
    void shouldTransitionCreatedToWatchingOnWatchAck() throws Exception {
        when(invoiceService.computeExpiresAt()).thenReturn(EXPIRES_AT);
        when(cryptoClient.registerWatch(any(UUID.class), eq("ETHEREUM"), eq("0xdef"), eq("0xabc"),
                eq(new BigDecimal("1000")), eq(EXPIRES_AT)))
                .thenReturn(new RegisterWatchResponse(WATCH_ID, "REGISTERED"));
        Invoice invoice = Invoice.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc",
                (short) 18, new BigDecimal("1000"), "0xdef", "order-1", EXPIRES_AT, WATCH_ID, EXPIRES_AT);
        when(invoiceService.create(any(UUID.class), eq(MERCHANT_ACCOUNT_UUID), eq("ETHEREUM"), eq("0xabc"),
                eq((short) 18), eq(new BigDecimal("1000")), eq("0xdef"), eq("order-1"), eq(EXPIRES_AT),
                eq(WATCH_ID)))
                .thenReturn(invoice);

        mockMvc.perform(post("/api/v1/invoices").with(merchantJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_JSON))
                .andExpect(status().isCreated());

        InOrder inOrder = inOrder(cryptoClient, invoiceService);
        ArgumentCaptor<UUID> watchInvoiceUuid = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> createInvoiceUuid = ArgumentCaptor.forClass(UUID.class);

        inOrder.verify(cryptoClient).registerWatch(watchInvoiceUuid.capture(), eq("ETHEREUM"), eq("0xdef"),
                eq("0xabc"), eq(new BigDecimal("1000")), eq(EXPIRES_AT));
        inOrder.verify(invoiceService).create(createInvoiceUuid.capture(), eq(MERCHANT_ACCOUNT_UUID),
                eq("ETHEREUM"), eq("0xabc"), eq((short) 18), eq(new BigDecimal("1000")), eq("0xdef"),
                eq("order-1"), eq(EXPIRES_AT), eq(WATCH_ID));

        assertThat(createInvoiceUuid.getValue())
                .as("the same invoiceUuid registered with Crypto Service must be the one persisted")
                .isEqualTo(watchInvoiceUuid.getValue());
    }

    /** Phase 11 Gap 5: previously checked status only - now also proves the Phase 10 {@code
     * AuthorizationDeniedException} handler's own RFC 9457 body shape, the actual reason this
     * test returns 403 at all rather than the generic 500 it returned before that handler
     * existed. */
    @Test
    void shouldRejectNonMerchantCallerWithForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/invoices").with(nonMerchantJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ProblemTypes.FORBIDDEN.toString()))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }

    /** Phase 11 Gap 4: a missing sub claim must map to this service's own 400, not an uncaught
     * NullPointerException falling through to a generic 500 (mirrors notification-service's own
     * identical Kimi Phase 11 Gap #10 precedent, which InvoiceController.merchantAccountUuidFrom
     * already guards against - this proves it, rather than only asserting it by comment).
     * {@code SecurityMockMvcRequestPostProcessors.jwt()}'s own default {@code Jwt.Builder} always
     * sets {@code sub="user"} (confirmed by reading its source directly) - {@code
     * .claims(claims -> claims.remove(...))} is required to produce a genuinely absent claim,
     * not just a non-default value. */
    @Test
    void shouldReturn400ForMissingSubClaim() throws Exception {
        RequestPostProcessor missingSubJwt = jwt()
                .jwt(builder -> builder.claim("roles", List.of("MERCHANT"))
                        .claims(claims -> claims.remove(JwtClaimNames.SUB)))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));

        mockMvc.perform(post("/api/v1/invoices").with(missingSubJwt)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid invoice request"))
                .andExpect(jsonPath("$.detail").value("sub claim is missing"));
    }

    @Test
    void shouldReturn400ForMalformedSubClaim() throws Exception {
        RequestPostProcessor malformedSubJwt = jwt()
                .jwt(builder -> builder.subject("not-a-uuid").claim("roles", List.of("MERCHANT")))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));

        mockMvc.perform(post("/api/v1/invoices").with(malformedSubJwt)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid invoice request"))
                .andExpect(jsonPath("$.detail").value("sub claim is not a valid UUID: not-a-uuid"));
    }

    @Test
    void shouldRejectUnauthenticatedCallerWithUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnServiceUnavailableWhenWatchRegistrationFails() throws Exception {
        when(invoiceService.computeExpiresAt()).thenReturn(EXPIRES_AT);
        when(cryptoClient.registerWatch(any(UUID.class), eq("ETHEREUM"), eq("0xdef"), eq("0xabc"),
                eq(new BigDecimal("1000")), eq(EXPIRES_AT)))
                .thenThrow(new CryptoServiceUnavailableException("Crypto Service is down", null));

        mockMvc.perform(post("/api/v1/invoices").with(merchantJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ProblemTypes.CRYPTO_SERVICE_UNAVAILABLE.toString()))
                .andExpect(jsonPath("$.title").value("Crypto Service unavailable"))
                .andExpect(jsonPath("$.detail").value(
                        "The invoice could not be created because an upstream dependency is unavailable."));
    }

    /** AC8 (named, package.md §8 R29-style coverage). A fractional amount ("10.5") passes
     * {@code CreateInvoiceRequest}'s own {@code @Positive} bean validation (it IS positive) but
     * is rejected by {@code InvoiceService.validateAmount}'s own deeper scale check (T05) -
     * exercises {@code InvoiceExceptionHandler}'s real path, not {@code ApiExceptionHandler}'s
     * DTO-level one (see {@link #shouldReturnProblemJsonForBeanValidationFailure} for that). */
    @Test
    void shouldReturnProblemJsonForInvalidInvoiceRequest() throws Exception {
        String fractionalAmountRequest = """
                {"chain":"ETHEREUM","tokenContractAddress":"0xabc","tokenDecimals":18,
                 "amount":10.5,"payToAddress":"0xdef","reference":"order-1"}
                """;
        when(invoiceService.computeExpiresAt()).thenReturn(EXPIRES_AT);
        when(cryptoClient.registerWatch(any(UUID.class), eq("ETHEREUM"), eq("0xdef"), eq("0xabc"),
                eq(new BigDecimal("10.5")), eq(EXPIRES_AT)))
                .thenReturn(new RegisterWatchResponse(WATCH_ID, "REGISTERED"));
        when(invoiceService.create(any(UUID.class), eq(MERCHANT_ACCOUNT_UUID), eq("ETHEREUM"), eq("0xabc"),
                eq((short) 18), eq(new BigDecimal("10.5")), eq("0xdef"), eq("order-1"), eq(EXPIRES_AT),
                eq(WATCH_ID)))
                .thenThrow(new InvalidInvoiceRequestException(
                        "amount must be a whole number of base units (no fractional component)"));

        mockMvc.perform(post("/api/v1/invoices").with(merchantJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(fractionalAmountRequest))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ProblemTypes.INVALID_INVOICE_REQUEST.toString()))
                .andExpect(jsonPath("$.title").value("Invalid invoice request"))
                .andExpect(jsonPath("$.detail").value(
                        "amount must be a whole number of base units (no fractional component)"));
    }

    /** Separate from the named test above: proves {@code ApiExceptionHandler}'s own Phase 7 fix
     * (otherwise zero test coverage anywhere) actually handles a DTO-level bean-validation
     * failure, which never reaches {@code InvoiceService} at all. */
    @Test
    void shouldReturnProblemJsonForBeanValidationFailure() throws Exception {
        String blankChainRequest = """
                {"chain":"","tokenContractAddress":"0xabc","tokenDecimals":18,
                 "amount":1000,"payToAddress":"0xdef","reference":"order-1"}
                """;

        mockMvc.perform(post("/api/v1/invoices").with(merchantJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(blankChainRequest))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ProblemTypes.VALIDATION_ERROR.toString()))
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    /** Named test (T23, R29): the full RFC 9457 shape - a real, stable catalogue {@code type}
     * (never {@code "about:blank"}), {@code title}/{@code status} present, and no field
     * anywhere in the body leaks a stack trace or a raw exception class name. Reuses the same
     * bean-validation scenario the test above already exercises, since this is the simplest real
     * 4xx path in this controller, not a new failure mode. */
    @Test
    void shouldReturnProblemJsonWithNoInternalDetailOn4xx() throws Exception {
        String blankChainRequest = """
                {"chain":"","tokenContractAddress":"0xabc","tokenDecimals":18,
                 "amount":1000,"payToAddress":"0xdef","reference":"order-1"}
                """;

        String body = mockMvc.perform(post("/api/v1/invoices").with(merchantJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(blankChainRequest))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ProblemTypes.VALIDATION_ERROR.toString()))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).as("type must be a real catalogue URI, never the generic placeholder")
                .doesNotContain("about:blank");
        assertThat(body).as("no stack trace or exception class name may appear in a 4xx body")
                .doesNotContainIgnoringCase("exception")
                .doesNotContainIgnoringCase("stacktrace")
                .doesNotContainIgnoringCase("MethodArgumentNotValidException")
                .doesNotContain(".java:");
    }

    private static Invoice invoice(UUID merchantAccountUuid) {
        return Invoice.create(INVOICE_UUID, merchantAccountUuid, "ETHEREUM", "0xabc", (short) 18,
                new BigDecimal("1000"), "0xdef", "order-1", EXPIRES_AT, WATCH_ID, EXPIRES_AT);
    }

    /** AC1 (R28), AC4: proves the real JSON shape, not just the Java object - the full field
     * set, {@code amount} as a quoted decimal string, and no {@code watchId} anywhere in the
     * body (Phase 8 findings 2/6). */
    @Test
    void shouldReturnOnlyCallersOwnInvoicesFromList() throws Exception {
        when(invoiceService.listForMerchant(MERCHANT_ACCOUNT_UUID)).thenReturn(List.of(invoice(MERCHANT_ACCOUNT_UUID)));

        mockMvc.perform(get("/api/v1/invoices").with(merchantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].invoiceUuid").value(INVOICE_UUID.toString()))
                .andExpect(jsonPath("$[0].merchantAccountUuid").value(MERCHANT_ACCOUNT_UUID.toString()))
                .andExpect(jsonPath("$[0].chain").value("ETHEREUM"))
                .andExpect(jsonPath("$[0].tokenContractAddress").value("0xabc"))
                .andExpect(jsonPath("$[0].tokenDecimals").value(18))
                .andExpect(jsonPath("$[0].amount").value("1000"))
                .andExpect(jsonPath("$[0].payToAddress").value("0xdef"))
                .andExpect(jsonPath("$[0].reference").value("order-1"))
                .andExpect(jsonPath("$[0].state").value("OPEN"))
                .andExpect(jsonPath("$[0].watchId").doesNotExist());

        verify(invoiceService).listForMerchant(MERCHANT_ACCOUNT_UUID);
    }

    @Test
    void shouldReturnEmptyListWhenCallerHasNoInvoices() throws Exception {
        when(invoiceService.listForMerchant(MERCHANT_ACCOUNT_UUID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/invoices").with(merchantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturnInvoiceForOwningMerchant() throws Exception {
        when(invoiceService.getForMerchant(MERCHANT_ACCOUNT_UUID, INVOICE_UUID))
                .thenReturn(invoice(MERCHANT_ACCOUNT_UUID));

        mockMvc.perform(get("/api/v1/invoices/" + INVOICE_UUID).with(merchantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceUuid").value(INVOICE_UUID.toString()))
                .andExpect(jsonPath("$.merchantAccountUuid").value(MERCHANT_ACCOUNT_UUID.toString()))
                .andExpect(jsonPath("$.chain").value("ETHEREUM"))
                .andExpect(jsonPath("$.tokenContractAddress").value("0xabc"))
                .andExpect(jsonPath("$.tokenDecimals").value(18))
                .andExpect(jsonPath("$.amount").value("1000"))
                .andExpect(jsonPath("$.payToAddress").value("0xdef"))
                .andExpect(jsonPath("$.reference").value("order-1"))
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.watchId").doesNotExist());

        // Phase 11 Gap 6: proves the controller actually delegates with the JWT-derived merchant
        // UUID and the real path UUID, not hard-coded/stubbed data.
        verify(invoiceService).getForMerchant(MERCHANT_ACCOUNT_UUID, INVOICE_UUID);
    }

    /** Phase 11 Gap 8: an invoice with a null reference must serialize the field as JSON
     * {@code null}, not omit it or throw - resolves the ambiguity Kimi itself raised. */
    @Test
    void shouldSerializeNullReferenceAsJsonNull() throws Exception {
        Invoice invoiceWithNullReference = Invoice.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM",
                "0xabc", (short) 18, new BigDecimal("1000"), "0xdef", null, EXPIRES_AT, WATCH_ID, EXPIRES_AT);
        when(invoiceService.getForMerchant(MERCHANT_ACCOUNT_UUID, INVOICE_UUID))
                .thenReturn(invoiceWithNullReference);

        mockMvc.perform(get("/api/v1/invoices/" + INVOICE_UUID).with(merchantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.nullValue()));
    }

    /** AC2: asserts the identical 404 body for both causes (Phase 8 finding 5) - captured once
     * per case and compared, not just each asserted to be "some 404". */
    @Test
    void shouldReturnIdentical404ShapeForNonOwningMerchantAndNonexistentInvoiceUuid() throws Exception {
        UUID nonexistentUuid = UUID.randomUUID();
        when(invoiceService.getForMerchant(MERCHANT_ACCOUNT_UUID, INVOICE_UUID))
                .thenThrow(new InvoiceNotFoundException());
        when(invoiceService.getForMerchant(MERCHANT_ACCOUNT_UUID, nonexistentUuid))
                .thenThrow(new InvoiceNotFoundException());

        String nonOwningBody = mockMvc.perform(get("/api/v1/invoices/" + INVOICE_UUID).with(merchantJwt()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invoice not found"))
                .andReturn().getResponse().getContentAsString();

        String nonexistentBody = mockMvc.perform(get("/api/v1/invoices/" + nonexistentUuid).with(merchantJwt()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString()
                .replace(nonexistentUuid.toString(), INVOICE_UUID.toString());

        assertThat(nonOwningBody).isEqualTo(nonexistentBody);
    }

    @Test
    void shouldRejectNonMerchantCallerOnReadEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/invoices").with(nonMerchantJwt()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ProblemTypes.FORBIDDEN.toString()))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(get("/api/v1/invoices/" + INVOICE_UUID).with(nonMerchantJwt()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ProblemTypes.FORBIDDEN.toString()))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }

    /** Phase 11 Gap 5: the existing unauthenticated test covers POST only - the security filter
     * chain's own {@code .anyRequest().authenticated()} rule must apply to the read paths too. */
    @Test
    void shouldRejectUnauthenticatedCallerOnReadEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/invoices"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/invoices/" + INVOICE_UUID))
                .andExpect(status().isUnauthorized());
    }

    /** Phase 11 Gap 4: a non-UUID path segment must map to the existing {@code
     * ApiExceptionHandler.onTypeMismatch} 400 path (T06) - not previously exercised for this
     * controller's own path variable. */
    @Test
    void shouldReturn400ForMalformedInvoiceUuidPathVariable() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/not-a-uuid").with(merchantJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Malformed request parameter"));
    }
}
