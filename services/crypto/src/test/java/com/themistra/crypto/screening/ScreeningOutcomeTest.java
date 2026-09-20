package com.themistra.crypto.screening;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** AC1 (R21/L12): locks the enum's exact 3-value set against {@code
 * chain.screening_results}'s {@code chk_screening_outcome} CHECK constraint (frozen brief Phase 4,
 * Finding #6 disposition) - a future rename/addition would fail this test even though no other test
 * enumerates the values directly. */
class ScreeningOutcomeTest {

    @Test
    void hasExactlyTheThreeValuesTheCheckConstraintAllows() {
        assertThat(ScreeningOutcome.values())
                .containsExactly(ScreeningOutcome.CLEARED, ScreeningOutcome.BLOCKED,
                        ScreeningOutcome.ERROR);
    }

    // Phase 11 (Kimi) Gap 1: values() only proves Java enum identity, not that the DB-converted
    // strings actually equal V1__chain_baseline.sql's literal CHECK constraint values
    // ('CLEARED','BLOCKED','ERROR') - exercises the converter directly, decoupled from the DB.
    @Test
    void dbConverterMapsEachValueToTheExactCheckConstraintLiteral() {
        ScreeningOutcome.DbConverter converter = new ScreeningOutcome.DbConverter();

        assertThat(converter.convertToDatabaseColumn(ScreeningOutcome.CLEARED)).isEqualTo("CLEARED");
        assertThat(converter.convertToDatabaseColumn(ScreeningOutcome.BLOCKED)).isEqualTo("BLOCKED");
        assertThat(converter.convertToDatabaseColumn(ScreeningOutcome.ERROR)).isEqualTo("ERROR");
    }

    @Test
    void dbConverterRoundTripsEveryValue() {
        ScreeningOutcome.DbConverter converter = new ScreeningOutcome.DbConverter();

        for (ScreeningOutcome outcome : ScreeningOutcome.values()) {
            String dbValue = converter.convertToDatabaseColumn(outcome);
            assertThat(converter.convertToEntityAttribute(dbValue)).isEqualTo(outcome);
        }
    }
}
