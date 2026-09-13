package com.themistra.crypto.attest;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * {@code POST /internal/v1/attest}'s {@code 200} response body (`design.md` §4c) - covers both the
 * {@code SIGNED} and {@code BLOCKED} wire shapes with one record. {@code @JsonInclude(NON_NULL)} (Phase
 * 4, Finding #7) keeps each shape exactly as `design.md` shows it - without it, a {@code SIGNED} body
 * would carry a stray {@code "reason": null} and a {@code BLOCKED} body a stray
 * {@code "signature": null}/{@code "kmsKeyId": null}/{@code "signedAt": null}.
 *
 * <p>{@code REFUSED} never produces an instance of this record - it is a {@code 409 problem+json}
 * response instead (via {@link AttestationRefusedException}), matching `design.md`'s own wire shapes,
 * which name no {@code 200 REFUSED} case.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttestResponse(String signature, String kmsKeyId, Instant signedAt, String outcome,
                              String reason) {

    public static AttestResponse signed(String signature, String kmsKeyId, Instant signedAt) {
        return new AttestResponse(signature, kmsKeyId, signedAt, "SIGNED", null);
    }

    public static AttestResponse blocked(String reason) {
        return new AttestResponse(null, null, null, "BLOCKED", reason);
    }
}
