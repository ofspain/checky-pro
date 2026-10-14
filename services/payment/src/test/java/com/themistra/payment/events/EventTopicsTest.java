package com.themistra.payment.events;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Named test shouldRouteEachPublishedEventToItsPaymentsTopic (package.md §8, R30). */
class EventTopicsTest {

    @ParameterizedTest
    @CsvSource({
            "invoice, payments.invoice.created",
            "payment-seen, payments.payment.seen",
            "payment-finalized, payments.payment.finalized",
            "receipt, payments.receipt.issued"
    })
    void shouldRouteEachPublishedEventToItsPaymentsTopic(String aggregateType, String expectedTopic) {
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
        // Map.of(...) rejects a null key lookup itself - matches crypto-service's identical
        // EventTopics behavior. Not converted to IllegalStateException: a null aggregate type is
        // a caller bug, not a "configuration error" the way an unmapped-but-present string is.
        assertThatThrownBy(() -> EventTopics.forAggregateType(null))
                .isInstanceOf(NullPointerException.class);
    }

    /** Phase 11 Gap 1: the four cases above only prove each known mapping is correct, never that
     * the map is exactly those four entries - a future edit adding/removing/renaming a mapping
     * would stay green otherwise. Reflection is used rather than a new package-private accessor,
     * since the map's own visibility is deliberately private (design.md §4c - no public mutation
     * surface). */
    @Test
    @SuppressWarnings("unchecked")
    void mappingIsExactlyTheFourAggregateTypesDesignDotMdSpecifies() throws Exception {
        Field field = EventTopics.class.getDeclaredField("TOPIC_BY_AGGREGATE_TYPE");
        field.setAccessible(true);
        Map<String, String> actual = (Map<String, String>) field.get(null);

        assertThat(actual).containsExactlyInAnyOrderEntriesOf(Map.of(
                "invoice", "payments.invoice.created",
                "payment-seen", "payments.payment.seen",
                "payment-finalized", "payments.payment.finalized",
                "receipt", "payments.receipt.issued"
        ));
    }
}
