package com.themistra.payment.invoice;

import com.themistra.payment.common.ApiExceptionHandler;
import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.common.CryptoServiceUnavailableException;
import com.themistra.payment.common.JwtRoleAuthoritiesConverter;
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
                .andExpect(jsonPath("$.type").value("about:blank"))
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
                .andExpect(jsonPath("$.type").value("about:blank"))
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
                .andExpect(jsonPath("$.type").value("about:blank"))
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
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }
}
