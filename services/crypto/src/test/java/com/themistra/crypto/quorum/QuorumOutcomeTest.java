package com.themistra.crypto.quorum;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 11 Gap 10: {@link QuorumOutcome} is a VERBATIM artifact from design.md §4c - this guards
 * against a silent rename/removal of a member (e.g. {@code UNKNOWN_TOKEN}, which no production code
 * path in this task references, so no other test would catch its disappearance). */
class QuorumOutcomeTest {

    @Test
    void containsExactlyTheThreeVerbatimMembersInOrder() {
        assertThat(QuorumOutcome.values())
                .extracting(Enum::name)
                .containsExactly("AGREED", "HELD", "UNKNOWN_TOKEN");
    }
}
