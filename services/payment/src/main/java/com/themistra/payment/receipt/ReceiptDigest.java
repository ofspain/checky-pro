package com.themistra.payment.receipt;

import com.themistra.payment.common.CanonicalJson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T16 (R19, partial — the digest-computation mechanism; obtaining the signature and acting on
 * the outcome is T17's own scope). Computes {@code SHA-256(CanonicalJson.serialize(receiptPayload))}
 * (`design.md` O4) — the exact bytes sent to the Crypto Service's {@code POST /attest} as
 * {@code receiptDigestSha256}.
 *
 * <p>Converts every {@link ReceiptPayload} field to a {@link CanonicalJson}-safe type ({@code
 * String}/{@code Short}) before building the map — {@code UUID.toString()}, {@code
 * BigDecimal.toPlainString()} for {@code amount}, {@code Instant.toString()} (RFC3339) for
 * {@code finalizedAt} — <b>except {@code tokenDecimals}</b>, which O4's own pinned JSON example
 * shows as a bare integer ({@code "tokenDecimals": 6}, unquoted), unlike every other field,
 * which O4 shows quoted. This is a deliberate, easy-to-miss asymmetry in O4's own text, not an
 * L6 violation (L6 governs money-carrying fields; {@code tokenDecimals} is a small integer
 * count, not an amount).</p>
 */
public final class ReceiptDigest {

    private static final String SHA_256 = "SHA-256";

    private ReceiptDigest() {
    }

    public static String digest(ReceiptPayload payload) {
        String canonicalJson = CanonicalJson.serialize(toCanonicalMap(payload));
        return sha256(canonicalJson);
    }

    private static Map<String, Object> toCanonicalMap(ReceiptPayload payload) {
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
        return map;
    }

    private static String sha256(String canonicalJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
