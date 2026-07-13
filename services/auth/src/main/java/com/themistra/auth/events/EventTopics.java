package com.themistra.auth.events;

import java.util.Map;

/**
 * Aggregate type → Kafka topic (target-design §12). A small explicit table rather than a naming
 * convention: an unmapped aggregate type is a configuration error and must fail loudly, not
 * silently drop events or guess a topic name.
 */
public final class EventTopics {

    private static final Map<String, String> TOPIC_BY_AGGREGATE_TYPE = Map.of(
            "account", "auth.user.lifecycle"
            // "verification-token" -> "auth.email.requested" and the audit mirror topic join
            // this table when their owning stages (account email-verification, Audit) land.
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
