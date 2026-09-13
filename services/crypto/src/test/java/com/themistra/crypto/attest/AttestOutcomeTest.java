package com.themistra.crypto.attest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks the enum's exact 3-value set against {@code chain.attestations}'s {@code chk_attest_outcome}
 * CHECK constraint, and its converter's round-trip - mirrors {@code screening.ScreeningOutcomeTest}. */
class AttestOutcomeTest {

    @Test
    void hasExactlyTheThreeValuesTheCheckConstraintAllows() {
        assertThat(AttestOutcome.values())
                .containsExactly(AttestOutcome.SIGNED, AttestOutcome.BLOCKED, AttestOutcome.REFUSED);
    }

    @Test
    void dbConverterMapsEachValueToTheExactCheckConstraintLiteral() {
        AttestOutcome.DbConverter converter = new AttestOutcome.DbConverter();

        assertThat(converter.convertToDatabaseColumn(AttestOutcome.SIGNED)).isEqualTo("SIGNED");
        assertThat(converter.convertToDatabaseColumn(AttestOutcome.BLOCKED)).isEqualTo("BLOCKED");
        assertThat(converter.convertToDatabaseColumn(AttestOutcome.REFUSED)).isEqualTo("REFUSED");
    }

    @Test
    void dbConverterRoundTripsEveryValue() {
        AttestOutcome.DbConverter converter = new AttestOutcome.DbConverter();

        for (AttestOutcome outcome : AttestOutcome.values()) {
            String dbValue = converter.convertToDatabaseColumn(outcome);
            assertThat(converter.convertToEntityAttribute(dbValue)).isEqualTo(outcome);
        }
    }
}
