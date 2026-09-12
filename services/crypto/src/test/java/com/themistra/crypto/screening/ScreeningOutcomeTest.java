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
}
