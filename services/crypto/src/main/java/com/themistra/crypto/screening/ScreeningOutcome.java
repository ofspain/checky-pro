package com.themistra.crypto.screening;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * The three outcomes a screening attempt can produce - exactly the values named in
 * {@code chain.screening_results.outcome}'s own {@code chk_screening_outcome} CHECK constraint (T02,
 * {@code V1__chain_baseline.sql}). Only {@link #CLEARED} may ever lead to a signature downstream
 * (L12-T19a, frozen brief Phase 4): {@link #BLOCKED} and {@link #ERROR} are both fail-closed,
 * non-retryable outcomes for a caller.
 */
public enum ScreeningOutcome {
    CLEARED,
    BLOCKED,
    ERROR;

    /** Maps to/from the CHECK constraint's own uppercase string values via {@link #name()} directly -
     * verified by reading {@code V1__chain_baseline.sql}'s literal constraint text
     * ({@code CHECK (outcome IN ('CLEARED','BLOCKED','ERROR'))}), not assumed. Unlike {@code
     * observation.FactType.DbConverter} (which lowercases, because {@code
     * chain.observations.fact_type}'s column comment documents lowercase values instead), no case
     * transformation is needed here. Applied explicitly via {@code @Convert} rather than
     * {@code autoApply}, so it can never accidentally attach to some other, unrelated enum-typed column
     * later. */
    @Converter
    static class DbConverter implements AttributeConverter<ScreeningOutcome, String> {

        @Override
        public String convertToDatabaseColumn(ScreeningOutcome outcome) {
            return outcome == null ? null : outcome.name();
        }

        @Override
        public ScreeningOutcome convertToEntityAttribute(String dbValue) {
            return dbValue == null ? null : ScreeningOutcome.valueOf(dbValue);
        }
    }
}
