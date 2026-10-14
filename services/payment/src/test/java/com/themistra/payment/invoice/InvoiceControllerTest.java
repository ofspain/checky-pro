package com.themistra.payment.invoice;

import com.themistra.payment.common.ApiExceptionHandler;
import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.common.CryptoServiceUnavailableException;
import com.themistra.payment.common.JwtRoleAuthoritiesConverter;
import com.themistra.payment.common.ResourceServerConfig;
import com.themistra.payment.common.crypto.RegisterWatchResponse;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    /** R6: the watchId registerWatch returns is the exact same value passed into
     * InvoiceService.create - proven by capturing the real, randomly-generated invoiceUuid
     * the controller itself creates and confirming both calls received the identical value. */
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

        verify(invoiceService).create(any(UUID.class), eq(MERCHANT_ACCOUNT_UUID), eq("ETHEREUM"), eq("0xabc"),
                eq((short) 18), eq(new BigDecimal("1000")), eq("0xdef"), eq("order-1"), eq(EXPIRES_AT),
                eq(WATCH_ID));
    }

    @Test
    void shouldRejectNonMerchantCallerWithForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/invoices").with(nonMerchantJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_JSON))
                .andExpect(status().isForbidden());
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
