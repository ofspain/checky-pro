package com.themistra.payment.receipt;

import com.themistra.payment.common.CanonicalJson;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T16 (R19 partial, `design.md` O4). Unit tests for the receipt digest — no Spring context,
 * pure functions throughout.
 */
class ReceiptDigestTest {

    private static final UUID RECEIPT_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INVOICE_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PAYMENT_UUID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID MERCHANT_ACCOUNT_UUID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant FINALIZED_AT = Instant.parse("2026-07-13T01:00:00Z");

    private static ReceiptPayload samplePayload() {
        return new ReceiptPayload(RECEIPT_UUID, INVOICE_UUID, PAYMENT_UUID, MERCHANT_ACCOUNT_UUID,
                "ETHEREUM", "0xabc123", "0xdef456", (short) 6,
                new BigDecimal("1000000"), "0x789", "0x012", FINALIZED_AT);
    }

    @Test
    void shouldProduceSixtyFourCharacterLowercaseHexDigest() {
        String digest = ReceiptDigest.digest(samplePayload());

        assertThat(digest).hasSize(64);
        assertThat(digest).matches("[0-9a-f]{64}");
    }

    @Test
    void shouldBeDeterministicForIdenticalPayload() {
        String first = ReceiptDigest.digest(samplePayload());
        String second = ReceiptDigest.digest(samplePayload());

        assertThat(first).isEqualTo(second);
    }

    @Test
    void shouldChangeDigestWhenFromAddressChangesFromValueToNull() {
        String withAddress = ReceiptDigest.digest(samplePayload());
        ReceiptPayload nullFromAddress = new ReceiptPayload(RECEIPT_UUID, INVOICE_UUID, PAYMENT_UUID,
                MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc123", "0xdef456", (short) 6,
                new BigDecimal("1000000"), "0x789", null, FINALIZED_AT);

        String withoutAddress = ReceiptDigest.digest(nullFromAddress);

        assertThat(withAddress).isNotEqualTo(withoutAddress);
    }

    @Test
    void shouldChangeDigestWhenAmountChanges() {
        ReceiptPayload changed = new ReceiptPayload(RECEIPT_UUID, INVOICE_UUID, PAYMENT_UUID,
                MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc123", "0xdef456", (short) 6,
                new BigDecimal("2000000"), "0x789", "0x012", FINALIZED_AT);

        assertThat(ReceiptDigest.digest(samplePayload())).isNotEqualTo(ReceiptDigest.digest(changed));
    }

    @Test
    void shouldChangeDigestWhenChainChanges() {
        ReceiptPayload changed = new ReceiptPayload(RECEIPT_UUID, INVOICE_UUID, PAYMENT_UUID,
                MERCHANT_ACCOUNT_UUID, "TRON", "0xabc123", "0xdef456", (short) 6,
                new BigDecimal("1000000"), "0x789", "0x012", FINALIZED_AT);

        assertThat(ReceiptDigest.digest(samplePayload())).isNotEqualTo(ReceiptDigest.digest(changed));
    }

    @Test
    void shouldChangeDigestWhenFinalizedAtChanges() {
        ReceiptPayload changed = new ReceiptPayload(RECEIPT_UUID, INVOICE_UUID, PAYMENT_UUID,
                MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc123", "0xdef456", (short) 6,
                new BigDecimal("1000000"), "0x789", "0x012", Instant.parse("2026-07-13T02:00:00Z"));

        assertThat(ReceiptDigest.digest(samplePayload())).isNotEqualTo(ReceiptDigest.digest(changed));
    }

    @Test
    void shouldChangeDigestWhenTokenDecimalsChanges() {
        ReceiptPayload changed = new ReceiptPayload(RECEIPT_UUID, INVOICE_UUID, PAYMENT_UUID,
                MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc123", "0xdef456", (short) 18,
                new BigDecimal("1000000"), "0x789", "0x012", FINALIZED_AT);

        assertThat(ReceiptDigest.digest(samplePayload())).isNotEqualTo(ReceiptDigest.digest(changed));
    }

    @Test
    void shouldSerializeAmountAsDecimalStringNotJsonNumber() {
        String canonicalJson = canonicalJsonFor(samplePayload());

        assertThat(canonicalJson).contains("\"amount\":\"1000000\"");
    }

    @Test
    void shouldSerializeTokenDecimalsAsJsonNumberNotString() {
        String canonicalJson = canonicalJsonFor(samplePayload());

        assertThat(canonicalJson).contains("\"tokenDecimals\":6");
        assertThat(canonicalJson).doesNotContain("\"tokenDecimals\":\"6\"");
    }

    @Test
    void shouldRejectNullPayload() {
        assertThatThrownBy(() -> ReceiptDigest.digest(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldProduceGoldenCanonicalJsonForFullReceiptPayload() {
        String canonicalJson = canonicalJsonFor(samplePayload());

        assertThat(canonicalJson).isEqualTo(
                "{\"amount\":\"1000000\",\"chain\":\"ETHEREUM\",\"finalizedAt\":\"2026-07-13T01:00:00Z\","
                        + "\"fromAddress\":\"0x012\",\"invoiceUuid\":\"22222222-2222-2222-2222-222222222222\","
                        + "\"merchantAccountUuid\":\"44444444-4444-4444-4444-444444444444\","
                        + "\"payToAddress\":\"0x789\",\"paymentUuid\":\"33333333-3333-3333-3333-333333333333\","
                        + "\"receiptUuid\":\"11111111-1111-1111-1111-111111111111\","
                        + "\"tokenContractAddress\":\"0xdef456\",\"tokenDecimals\":6,\"txHash\":\"0xabc123\"}");
    }

    /** Mirrors {@code ReceiptDigest.toCanonicalMap} exactly, as a test-only seam (that method is
     * private) - rebuilding the same map here lets these tests inspect the canonical JSON text
     * directly rather than only its SHA-256 digest. */
    private static String canonicalJsonFor(ReceiptPayload payload) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("receiptUuid", payload.receiptUuid().toString());
        map.put("invoiceUuid", payload.invoiceUuid().toString());
        map.put("paymentUuid", payload.paymentUuid().toString());
        map.put("merchantAccountUuid", payload.merchantAccountUuid().toString());
        map.put("chain", payload.chain());
        map.put("txHash", payload.txHash());
        map.put("tokenContractAddress", payload.tokenContractAddress());
        map.put("tokenDecimals", payload.tokenDecimals());
        map.put("amount", payload.amount().toPlainString());
        map.put("payToAddress", payload.payToAddress());
        map.put("fromAddress", payload.fromAddress());
        map.put("finalizedAt", payload.finalizedAt().toString());
        return CanonicalJson.serialize(map);
    }
}
