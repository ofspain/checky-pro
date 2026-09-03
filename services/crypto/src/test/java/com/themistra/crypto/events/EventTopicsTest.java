package com.themistra.crypto.events;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Named test shouldRouteEachChainEventToItsTopic (package.md §8, → R26). */
class EventTopicsTest {

    @ParameterizedTest
    @CsvSource({
            "tx-seen, chain.tx.seen",
            "tx-confirmed, chain.tx.confirmed",
            "tx-finalized, chain.tx.finalized",
            "tx-reorged, chain.tx.reorged",
            "provider, chain.provider.degraded"
    })
    void shouldRouteEachChainEventToItsTopic(String aggregateType, String expectedTopic) {
        assertThat(EventTopics.forAggregateType(aggregateType)).isEqualTo(expectedTopic);
    }

    @Test
    void unmappedAggregateTypeFailsLoudRatherThanGuessing() {
        assertThatThrownBy(() -> EventTopics.forAggregateType("unmapped-thing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unmapped-thing");
    }

    @Test
    void blankAggregateTypeIsTreatedAsUnmapped() {
        assertThatThrownBy(() -> EventTopics.forAggregateType(""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullAggregateTypeThrowsNullPointerException() {
        // Map.of(...) rejects a null key lookup itself - documented here as this class's actual
        // behavior (matches services/auth's identical EventTopics, which has the same characteristic).
        // Not converted to IllegalStateException: a null aggregate type is a caller bug, not a
        // "configuration error" the way an unmapped-but-present string is.
        assertThatThrownBy(() -> EventTopics.forAggregateType(null))
                .isInstanceOf(NullPointerException.class);
    }
}
