package com.themistra.crypto.events;

import java.util.Map;

/**
 * Aggregate type → Kafka topic (design.md §4c, VERBATIM). A small explicit table rather than a
 * naming convention: an unmapped aggregate type is a configuration error and must fail loudly, not
 * silently drop events or guess a topic name.
 */
public final class EventTopics {

    private static final Map<String, String> TOPIC_BY_AGGREGATE_TYPE = Map.of(
            "tx-seen", "chain.tx.seen",
            "tx-confirmed", "chain.tx.confirmed",
            "tx-finalized", "chain.tx.finalized",
            "tx-reorged", "chain.tx.reorged",
            "provider", "chain.provider.degraded"
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
