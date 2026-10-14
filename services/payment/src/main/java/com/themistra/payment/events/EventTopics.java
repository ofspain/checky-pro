package com.themistra.payment.events;

import java.util.Map;

/**
 * Aggregate type → Kafka topic (design.md §4c, VERBATIM; R30). A small explicit table rather
 * than a naming convention: an unmapped aggregate type is a configuration error and must fail
 * loudly, not silently drop events or guess a topic name.
 */
public final class EventTopics {

    private static final Map<String, String> TOPIC_BY_AGGREGATE_TYPE = Map.of(
            "invoice", "payments.invoice.created",
            "payment-seen", "payments.payment.seen",
            "payment-finalized", "payments.payment.finalized",
            "receipt", "payments.receipt.issued"
    );

    private EventTopics() {
    }

    public static String forAggregateType(String aggregateType) {
        String topic = TOPIC_BY_AGGREGATE_TYPE.get(aggregateType);
        if (topic == null) {
            throw new IllegalStateException("No Kafka topic mapped for aggregate type: " + aggregateType);
        }
        return topic;
    }
}
