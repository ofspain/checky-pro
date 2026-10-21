package com.themistra.payment.history;

import com.themistra.payment.common.ApiExceptionHandler;
import com.themistra.payment.common.JwtRoleAuthoritiesConverter;
import com.themistra.payment.common.ProblemTypes;
import com.themistra.payment.common.ResourceServerConfig;
import com.themistra.payment.history.dto.PaymentResponse;
import com.themistra.payment.invoice.InvoiceExceptionHandler;
import com.themistra.payment.invoice.InvoiceNotFoundForTests;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Frontend Phase 1b (2026-10-21): {@code GET /api/v1/invoices/{invoiceUuid}/payments}, with the
 * real resource-server config, as in {@code ReceiptControllerTest}. */
@WebMvcTest(controllers = PaymentHistoryController.class)
@Import({ResourceServerConfig.class, JwtRoleAuthoritiesConverter.class, InvoiceExceptionHandler.class,
        ApiExceptionHandler.class})
class PaymentHistoryControllerTest {

    private static final UUID MERCHANT = UUID.randomUUID();
    private static final UUID INVOICE_UUID = UUID.randomUUID();
    private static final Instant SEEN_AT = Instant.parse("2026-10-21T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentHistoryService historyService;

    private static RequestPostProcessor merchantJwt() {
        return jwt().jwt(b -> b.subject(MERCHANT.toString()).claim("roles", List.of("MERCHANT")))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
    }

    @Test
    void returnsTheCallersPayments() throws Exception {
        UUID paymentUuid = UUID.randomUUID();
        when(historyService.listForMerchant(MERCHANT, INVOICE_UUID)).thenReturn(List.of(new PaymentResponse(
                paymentUuid, INVOICE_UUID, "ETHEREUM", "0xtx", "SEEN", 0, null, "0xtoken", (short) 6, null, null,
                true, SEEN_AT, null, null, null, null, null)));

        mockMvc.perform(get("/api/v1/invoices/" + INVOICE_UUID + "/payments").with(merchantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].paymentUuid").value(paymentUuid.toString()))
                .andExpect(jsonPath("$[0].state").value("SEEN"))
                .andExpect(jsonPath("$[0].confirmations").value(0))
                .andExpect(jsonPath("$[0].addressPoisoningFlag").value(true))
                .andExpect(jsonPath("$[0].seenAt").value("2026-10-21T10:00:00Z"))
                .andExpect(jsonPath("$[0].tokenDecimals").value(6))
                .andExpect(jsonPath("$[0].receiptUuid").doesNotExist());
    }

    /** R28/L10: another merchant's invoice and an unknown one give byte-identical 404 bodies. */
    @Test
    void anotherMerchantsInvoiceAndAnUnknownOneGiveTheSame404() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(historyService.listForMerchant(MERCHANT, INVOICE_UUID)).thenThrow(InvoiceNotFoundForTests.exception());
        when(historyService.listForMerchant(MERCHANT, unknown)).thenThrow(InvoiceNotFoundForTests.exception());

        String notOwned = mockMvc.perform(get("/api/v1/invoices/" + INVOICE_UUID + "/payments").with(merchantJwt()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ProblemTypes.NOT_FOUND.toString()))
                .andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(get("/api/v1/invoices/" + unknown + "/payments").with(merchantJwt()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString()
                .replace(unknown.toString(), INVOICE_UUID.toString());

        assertThat(notOwned).isEqualTo(missing);
    }

    @Test
    void aNonMerchantCallerIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/" + INVOICE_UUID + "/payments")
                        .with(jwt().jwt(b -> b.subject(MERCHANT.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(historyService);
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/" + INVOICE_UUID + "/payments"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(historyService);
    }

    @Test
    void aMalformedSubClaimIsA400() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/" + INVOICE_UUID + "/payments")
                        .with(jwt().jwt(b -> b.subject("not-a-uuid"))
                                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ProblemTypes.INVALID_INVOICE_REQUEST.toString()));
        verifyNoInteractions(historyService);
    }

    @Test
    void aMalformedInvoiceUuidIsA400() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/not-a-uuid/payments").with(merchantJwt()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(historyService);
    }
}
