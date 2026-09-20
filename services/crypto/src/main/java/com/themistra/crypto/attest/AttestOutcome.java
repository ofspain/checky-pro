package com.themistra.crypto.attest;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * The three outcomes an attest request can produce - exactly the values named in
 * {@code chain.attestations.outcome}'s own {@code chk_attest_outcome} CHECK constraint (T02,
 * {@code V1__chain_baseline.sql}). Only {@link #SIGNED} carries an actual signature; {@link #BLOCKED}
 * (an active sanctions hit, R21) and {@link #REFUSED} (any other gate failure, R23/L12) both produce no
 * signature.
 */
public enum AttestOutcome {
    SIGNED,
    BLOCKED,
    REFUSED;

    /** Maps to/from the CHECK constraint's own uppercase string values via {@link #name()} directly -
     * mirrors {@code screening.ScreeningOutcome.DbConverter}'s exact shape (Phase 4, Finding #6): the
     * constraint's own values are already uppercase, so no case transformation is needed. Applied
     * explicitly via {@code @Convert} rather than {@code autoApply}, so it can never accidentally
     * attach to some other, unrelated enum-typed column later. */
    @Converter
    static class DbConverter implements AttributeConverter<AttestOutcome, String> {

        @Override
        public String convertToDatabaseColumn(AttestOutcome outcome) {
            return outcome == null ? null : outcome.name();
        }

        @Override
        public AttestOutcome convertToEntityAttribute(String dbValue) {
            return dbValue == null ? null : AttestOutcome.valueOf(dbValue);
        }
    }
}
