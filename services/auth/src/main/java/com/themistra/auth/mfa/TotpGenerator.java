package com.themistra.auth.mfa;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * TOTP secret generation and {@code otpauth://} provisioning URI construction (R22, L6). No AWS
 * SDK import — {@link MfaSeedEncryption} is the only class in this service permitted that
 * (ADR-0003, D-025). Neither the raw secret nor the returned URI (which carries the same secret,
 * Base32-encoded) may ever be logged — both are secret material.
 */
@Component
public class TotpGenerator {

    /** RFC 6238 §5.1 minimum (160 bits); matches the Google Authenticator 32-character convention. */
    private static final int SECRET_LENGTH_BYTES = 20;

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final MfaProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public TotpGenerator(MfaProperties properties) {
        this.properties = properties;
    }

    /** A cryptographically random 20-byte (160-bit) TOTP secret. */
    public byte[] generateSecret() {
        byte[] secret = new byte[SECRET_LENGTH_BYTES];
        secureRandom.nextBytes(secret);
        return secret;
    }

    /**
     * Builds an {@code otpauth://totp/...} provisioning URI per the Google Authenticator Key URI
     * Format convention, using L6's algorithm/digits/period (RFC 6238 defaults).
     *
     * @param secret       the raw secret from {@link #generateSecret()}
     * @param accountLabel identifies the account within the authenticator app (e.g. the account
     *                     UUID or email); URL-encoded along with the issuer per RFC 3986
     */
    public String buildProvisioningUri(byte[] secret, String accountLabel) {
        String encodedIssuerPathSegment = UriUtils.encodePathSegment(properties.issuerName(), StandardCharsets.UTF_8);
        String encodedLabelPathSegment = UriUtils.encodePathSegment(accountLabel, StandardCharsets.UTF_8);
        String encodedIssuerQueryParam = UriUtils.encodeQueryParam(properties.issuerName(), StandardCharsets.UTF_8);
        String base32Secret = base32Encode(secret);

        return "otpauth://totp/" + encodedIssuerPathSegment + ":" + encodedLabelPathSegment
                + "?secret=" + base32Secret
                + "&issuer=" + encodedIssuerQueryParam
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsInBuffer = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsInBuffer += 8;
            while (bitsInBuffer >= 5) {
                int index = (buffer >>> (bitsInBuffer - 5)) & 0x1F;
                result.append(BASE32_ALPHABET.charAt(index));
                bitsInBuffer -= 5;
            }
        }
        if (bitsInBuffer > 0) {
            int index = (buffer << (5 - bitsInBuffer)) & 0x1F;
            result.append(BASE32_ALPHABET.charAt(index));
        }
        return result.toString();
    }
}
