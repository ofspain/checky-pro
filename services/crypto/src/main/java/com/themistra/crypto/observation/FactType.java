package com.themistra.crypto.observation;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * The five fact kinds a provider observation can report — exactly the values named in
 * {@code chain.observations.fact_type}'s own column comment (T02, {@code V1__chain_baseline.sql}).
 * The database column itself carries no {@code CHECK} constraint (confirmed by reading the migration
 * directly, not assumed); this enum plus {@link DbConverter} is what constrains it at the application
 * layer instead (Phase 3 Kimi Issue 5).
 */
public enum FactType {
    EXISTENCE,
    AMOUNT,
    TOKEN,
    CONFIRMATIONS,
    FINALITY;

    /** Maps to/from the lowercase string values the column comment documents (e.g. {@code
     * "confirmations"}), not {@link #name()}'s own uppercase form. Applied explicitly via
     * {@code @Convert} on {@link Observation#factType} rather than {@code autoApply}, so it can never
     * accidentally attach to some other, unrelated enum-typed column later. */
    @Converter
    static class DbConverter implements AttributeConverter<FactType, String> {

        @Override
        public String convertToDatabaseColumn(FactType factType) {
            return factType == null ? null : factType.name().toLowerCase(java.util.Locale.ROOT);
        }

        @Override
        public FactType convertToEntityAttribute(String dbValue) {
            return dbValue == null ? null : FactType.valueOf(dbValue.toUpperCase(java.util.Locale.ROOT));
        }
    }
}
