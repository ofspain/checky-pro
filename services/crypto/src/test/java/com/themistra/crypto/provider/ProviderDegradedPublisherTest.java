package com.themistra.crypto.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.themistra.crypto.events.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/** AC2/AC6/AC7 (event emission, idempotency key, payload shape). */
@ExtendWith(MockitoExtension.class)
class ProviderDegradedPublisherTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-03T12:00:00Z");

    @Mock
    private OutboxPublisher outboxPublisher;

    private ProviderDegradedPublisher publisher;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        publisher = new ProviderDegradedPublisher(outboxPublisher);
    }

    @Test
    void publishUsesTheFixedAggregateTypeAndEventType() {
        publisher.publish("ETHEREUM", "alchemy", DegradationReason.UNHEALTHY, OCCURRED_AT);

        verify(outboxPublisher).publish(eq("provider"), eq("ETHEREUM:alchemy"),
                eq("chain.provider.degraded"), any(), any());
    }

    @Test
    void publishBuildsTheDocumentedPayloadShape() {
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        publisher.publish("ETHEREUM", "alchemy", DegradationReason.LAGGING, OCCURRED_AT);

        verify(outboxPublisher).publish(any(), any(), any(), any(), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(ProviderDegradedPublisher.Payload.class);
        ProviderDegradedPublisher.Payload payload = (ProviderDegradedPublisher.Payload) payloadCaptor.getValue();
        assertThat(payload.chain()).isEqualTo("ETHEREUM");
        assertThat(payload.provider()).isEqualTo("alchemy");
        assertThat(payload.reason()).isEqualTo(DegradationReason.LAGGING);
        assertThat(payload.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void twoPublishesWithIdenticalArgumentsProduceDifferentIdempotencyKeys() {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        publisher.publish("ETHEREUM", "alchemy", DegradationReason.UNHEALTHY, OCCURRED_AT);
        publisher.publish("ETHEREUM", "alchemy", DegradationReason.UNHEALTHY, OCCURRED_AT);

        verify(outboxPublisher, org.mockito.Mockito.times(2))
                .publish(any(), any(), any(), keyCaptor.capture(), any());
        java.util.List<String> keys = keyCaptor.getAllValues();
        assertThat(keys.get(0)).isNotEqualTo(keys.get(1));
        // Phase 11 Gap 8: exact format, not just a prefix check - "{chain}:{provider}:degraded:
        // {occurredAt}:{UUID}", exactly one colon between each component, UUID last.
        String uuidPattern = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
        String expectedPattern = "^ETHEREUM:alchemy:degraded:" + Pattern.quote(OCCURRED_AT.toString())
                + ":" + uuidPattern + "$";
        assertThat(keys.get(0)).matches(expectedPattern);
        assertThat(keys.get(1)).matches(expectedPattern);
    }

    @Test
    void payloadSerializesToTheDocumentedJsonShapeWithIsoInstantAndEnumName() throws Exception {
        // Phase 11 Gap 5: a bare `new ObjectMapper()` (OutboxPublisherTest's own established
        // pattern) THROWS on java.time.Instant - this task is the first to put one through this
        // path. Even `findAndRegisterModules()` alone serializes Instant as a numeric epoch-seconds
        // value, not ISO-8601, unless WRITE_DATES_AS_TIMESTAMPS is explicitly disabled - which is
        // what Spring Boot's own auto-configured ObjectMapper bean does by default in production.
        // This mapper configuration is chosen specifically to match that real, production shape.
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String json = mapper.writeValueAsString(
                new ProviderDegradedPublisher.Payload("ETHEREUM", "alchemy",
                        DegradationReason.REPEATED_DISAGREEMENT, OCCURRED_AT));

        assertThat(json).isEqualTo("{\"chain\":\"ETHEREUM\",\"provider\":\"alchemy\","
                + "\"reason\":\"REPEATED_DISAGREEMENT\",\"occurredAt\":\"2026-09-03T12:00:00Z\"}");
    }

    @Test
    void publishRejectsAChainContainingAColon() {
        assertThatThrownBy(() -> publisher.publish("ETH:EREUM", "alchemy", DegradationReason.UNHEALTHY, OCCURRED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chain");
    }

    @Test
    void publishRejectsAProviderContainingAColon() {
        assertThatThrownBy(() -> publisher.publish("ETHEREUM", "al:chemy", DegradationReason.UNHEALTHY, OCCURRED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
    }
}
