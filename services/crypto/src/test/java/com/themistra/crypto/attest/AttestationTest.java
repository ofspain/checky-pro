package com.themistra.crypto.attest;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** {@code create(...)} rejects {@code null} for every non-nullable parameter and accepts {@code null}
 * for the DDL's own nullable columns ({@code kms_key_id}, {@code signed_at}). */
class AttestationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-14T00:00:00Z");
    private static final Instant SIGNED_AT = Instant.parse("2026-09-14T00:00:01Z");

    @Test
    void createRejectsNullChain() {
        assertThatNullPointerException()
                .isThrownBy(() -> Attestation.create(null, "0xtx", "d".repeat(64),
                        AttestOutcome.REFUSED, null, null, CREATED_AT));
    }

    @Test
    void createRejectsNullTxHash() {
        assertThatNullPointerException()
                .isThrownBy(() -> Attestation.create("ETHEREUM", null, "d".repeat(64),
                        AttestOutcome.REFUSED, null, null, CREATED_AT));
    }

    @Test
    void createRejectsNullReceiptDigest() {
        assertThatNullPointerException()
                .isThrownBy(() -> Attestation.create("ETHEREUM", "0xtx", null,
                        AttestOutcome.REFUSED, null, null, CREATED_AT));
    }

    @Test
    void createRejectsNullOutcome() {
        assertThatNullPointerException()
                .isThrownBy(() -> Attestation.create("ETHEREUM", "0xtx", "d".repeat(64),
                        null, null, null, CREATED_AT));
    }

    @Test
    void createRejectsNullCreatedAt() {
        assertThatNullPointerException()
                .isThrownBy(() -> Attestation.create("ETHEREUM", "0xtx", "d".repeat(64),
                        AttestOutcome.REFUSED, null, null, null));
    }

    @Test
    void createAcceptsNullKmsKeyIdAndNullSignedAt() {
        Attestation attestation = Attestation.create("ETHEREUM", "0xtx", "d".repeat(64),
                AttestOutcome.REFUSED, null, null, CREATED_AT);

        assertThat(attestation.kmsKeyId()).isNull();
        assertThat(attestation.signedAt()).isNull();
    }

    @Test
    void accessorsReturnExactlyWhatWasPassedIn() {
        Attestation attestation = Attestation.create("TRON", "0xtx", "d".repeat(64),
                AttestOutcome.SIGNED, "arn:aws:kms:...", SIGNED_AT, CREATED_AT);

        assertThat(attestation.chain()).isEqualTo("TRON");
        assertThat(attestation.txHash()).isEqualTo("0xtx");
        assertThat(attestation.receiptDigest()).isEqualTo("d".repeat(64));
        assertThat(attestation.outcome()).isEqualTo(AttestOutcome.SIGNED);
        assertThat(attestation.kmsKeyId()).isEqualTo("arn:aws:kms:...");
        assertThat(attestation.signedAt()).isEqualTo(SIGNED_AT);
        assertThat(attestation.createdAt()).isEqualTo(CREATED_AT);
    }
}
