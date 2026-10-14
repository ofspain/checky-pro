package com.themistra.payment.events;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

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
}
