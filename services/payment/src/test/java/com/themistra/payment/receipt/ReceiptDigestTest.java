package com.themistra.payment.receipt;

import com.themistra.payment.common.CanonicalJson;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
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

    /** The exact canonical JSON {@link #samplePayload()} must produce - shared by the golden
     * test and the independent digest-tie test (Phase 11, Kimi Gap 1) so both reference the
     * one literal, never two separately-maintained copies. */
    private static final String GOLDEN_CANONICAL_JSON =
            "{\"amount\":\"1000000\",\"chain\":\"ETHEREUM\",\"finalizedAt\":\"2026-07-13T01:00:00Z\","
                    + "\"fromAddress\":\"0x012\",\"invoiceUuid\":\"22222222-2222-2222-2222-222222222222\","
                    + "\"merchantAccountUuid\":\"44444444-4444-4444-4444-444444444444\","
                    + "\"payToAddress\":\"0x789\",\"paymentUuid\":\"33333333-3333-3333-3333-333333333333\","
                    + "\"receiptUuid\":\"11111111-1111-1111-1111-111111111111\","
                    + "\"tokenContractAddress\":\"0xdef456\",\"tokenDecimals\":6,\"txHash\":\"0xabc123\"}";

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

    /** Phase 11 Gap 4 (self-produced): {@code receiptUuid} has no entity source at all - it is
     * generated fresh by the caller every time - making it the highest-value single field to
     * prove sensitivity for among the seven not already covered. */
    @Test
    void shouldChangeDigestWhenReceiptUuidChanges() {
        ReceiptPayload changed = new ReceiptPayload(UUID.randomUUID(), INVOICE_UUID, PAYMENT_UUID,
                MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc123", "0xdef456", (short) 6,
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

        assertThat(canonicalJson).isEqualTo(GOLDEN_CANONICAL_JSON);
    }

    /** Phase 11 Gap 1/2 (Kimi, real pass): the golden-JSON test above and the amount/
     * tokenDecimals-shape tests all go through {@link #canonicalJsonFor}, a test-only helper
     * that mirrors {@code ReceiptDigest.toCanonicalMap}'s own logic independently - if that
     * private method drifted (dropped a field, changed formatting), the helper would need the
     * identical drift to still pass, so none of those tests actually guard against it. This
     * test closes that gap: it hashes {@link #GOLDEN_CANONICAL_JSON} itself, via a completely
     * separate {@code MessageDigest} call (not {@code ReceiptDigest}'s own, not
     * {@code canonicalJsonFor}), and asserts it equals {@code ReceiptDigest.digest(...)}'s real
     * output - tying the actual production code path directly to the golden reference. */
    @Test
    void shouldProduceDigestMatchingIndependentShaTwoFiftySixOfGoldenCanonicalJson()
            throws NoSuchAlgorithmException {
        MessageDigest independentDigest = MessageDigest.getInstance("SHA-256");
        byte[] hash = independentDigest.digest(GOLDEN_CANONICAL_JSON.getBytes(StandardCharsets.UTF_8));
        String expectedHex = HexFormat.of().formatHex(hash);

        assertThat(ReceiptDigest.digest(samplePayload())).isEqualTo(expectedHex);
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
