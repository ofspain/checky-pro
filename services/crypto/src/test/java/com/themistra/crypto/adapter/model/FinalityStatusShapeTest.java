package com.themistra.crypto.adapter.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Phase 11 Gap 4, AC5 (L4) — {@code FinalityStatus} must carry only raw block/checkpoint data: no
 * precomputed "is final" boolean, no hardcoded confirmation-count field. A future change adding
 * either would violate L4 without this guard failing.
 */
class FinalityStatusShapeTest {

    @Test
    void hasExactlyTheThreeRawLongFieldsAndNothingElse() {
        RecordComponent[] components = FinalityStatus.class.getRecordComponents();

        assertThat(Arrays.stream(components).map(c -> tuple(c.getName(), c.getType())))
                .containsExactlyInAnyOrder(
                        tuple("txBlockNumber", long.class),
                        tuple("currentBlockNumber", long.class),
                        tuple("finalizedBlockNumber", long.class));
    }

    @Test
    void noComponentNameSuggestsAPrecomputedFinalityDecision() {
        RecordComponent[] components = FinalityStatus.class.getRecordComponents();

        assertThat(Arrays.stream(components).map(RecordComponent::getName))
                .as("L4: finality is the FinalityPolicy's decision, never baked into this record")
                .filteredOn(name -> !name.equals("finalizedBlockNumber"))
                .noneMatch(name -> {
                    String lower = name.toLowerCase(java.util.Locale.ROOT);
                    return lower.contains("final") || lower.contains("confirmation") || lower.contains("threshold");
                });
    }
}
