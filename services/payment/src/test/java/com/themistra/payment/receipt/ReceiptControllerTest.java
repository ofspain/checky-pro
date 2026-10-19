package com.themistra.payment.receipt;

import com.themistra.payment.common.ApiExceptionHandler;
import com.themistra.payment.common.JwtRoleAuthoritiesConverter;
import com.themistra.payment.common.ProblemTypes;
import com.themistra.payment.common.ResourceServerConfig;
import com.themistra.payment.receipt.dto.ReceiptResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FU-T29-1: {@code GET /api/v1/receipts/{receiptUuid}}. Same security wiring as {@code
 * InvoiceControllerTest}: the real resource-server config, with authorities set explicitly. */
@WebMvcTest(controllers = ReceiptController.class)
@Import({ResourceServerConfig.class, JwtRoleAuthoritiesConverter.class, ReceiptExceptionHandler.class,
        ApiExceptionHandler.class})
class ReceiptControllerTest {

    private static final UUID MERCHANT = UUID.randomUUID();
    private static final UUID RECEIPT_UUID = UUID.randomUUID();
    private static final Instant FINALIZED_AT = Instant.parse("2026-10-19T10:00:00Z");
    private static final Instant SIGNED_AT = Instant.parse("2026-10-19T10:00:05.123Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReceiptReadService receiptReadService;

    private static RequestPostProcessor merchantJwt() {
        return jwt().jwt(b -> b.subject(MERCHANT.toString()).claim("roles", List.of("MERCHANT")))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
    }

    private static ReceiptResponse receipt() {
        return new ReceiptResponse(RECEIPT_UUID, UUID.randomUUID(), UUID.randomUUID(), MERCHANT, "ETHEREUM", "0xtx",
                "0xtoken", 6, "1000000", "0xpayto", "0xfrom", FINALIZED_AT, "sig-hex", "kms-key-1", SIGNED_AT);
    }

    @Test
    void returnsTheCallersOwnReceipt() throws Exception {
        when(receiptReadService.getForMerchant(MERCHANT, RECEIPT_UUID)).thenReturn(receipt());

        mockMvc.perform(get("/api/v1/receipts/" + RECEIPT_UUID).with(merchantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptUuid").value(RECEIPT_UUID.toString()))
                .andExpect(jsonPath("$.merchantAccountUuid").value(MERCHANT.toString()))
                .andExpect(jsonPath("$.amount").value("1000000"))
                .andExpect(jsonPath("$.tokenDecimals").value(6))
                .andExpect(jsonPath("$.finalizedAt").value("2026-10-19T10:00:00Z"))
                .andExpect(jsonPath("$.signedAt").value("2026-10-19T10:00:05.123Z"))
                .andExpect(jsonPath("$.signature").value("sig-hex"));
    }

    /** L10: another merchant's receipt and a nonexistent one give byte-identical 404 bodies. */
    @Test
    void anotherMerchantsReceiptAndAnUnknownOneGiveTheSame404() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(receiptReadService.getForMerchant(MERCHANT, RECEIPT_UUID)).thenThrow(new ReceiptNotFoundException());
        when(receiptReadService.getForMerchant(MERCHANT, unknown)).thenThrow(new ReceiptNotFoundException());

        String notOwned = mockMvc.perform(get("/api/v1/receipts/" + RECEIPT_UUID).with(merchantJwt()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ProblemTypes.NOT_FOUND.toString()))
                .andExpect(jsonPath("$.title").value("Receipt not found"))
                .andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(get("/api/v1/receipts/" + unknown).with(merchantJwt()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString()
                .replace(unknown.toString(), RECEIPT_UUID.toString());

        assertThat(notOwned).isEqualTo(missing);
        assertThat(notOwned).doesNotContain("detail");
    }

    @Test
    void aNonMerchantCallerIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/receipts/" + RECEIPT_UUID)
                        .with(jwt().jwt(b -> b.subject(MERCHANT.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(ProblemTypes.FORBIDDEN.toString()));
        verifyNoInteractions(receiptReadService);
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/receipts/" + RECEIPT_UUID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(receiptReadService);
    }

    @Test
    void aMalformedSubClaimIsA400() throws Exception {
        mockMvc.perform(get("/api/v1/receipts/" + RECEIPT_UUID)
                        .with(jwt().jwt(b -> b.subject("not-a-uuid"))
                                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ProblemTypes.INVALID_RECEIPT_REQUEST.toString()));
    }

    @Test
    void aMalformedReceiptUuidIsA400() throws Exception {
        mockMvc.perform(get("/api/v1/receipts/not-a-uuid").with(merchantJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ProblemTypes.MALFORMED_REQUEST_PARAMETER.toString()));
    }

    /** A storage fault is a 500 with a trace id and none of the internal message. */
    @Test
    void aStorageFaultIsA500WithoutInternalDetail() throws Exception {
        when(receiptReadService.getForMerchant(any(), any()))
                .thenThrow(new ReceiptStorageException("Stored receipt object missing for receipt x", null));

        String body = mockMvc.perform(get("/api/v1/receipts/" + RECEIPT_UUID).with(merchantJwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value(ProblemTypes.INTERNAL_ERROR.toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Stored receipt object").doesNotContain("ReceiptStorageException");
    }
}
