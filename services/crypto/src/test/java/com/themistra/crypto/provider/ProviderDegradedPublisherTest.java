package com.themistra.crypto.provider;

import com.themistra.crypto.events.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

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
        assertThat(keys.get(0)).startsWith("ETHEREUM:alchemy:degraded:" + OCCURRED_AT);
        assertThat(keys.get(1)).startsWith("ETHEREUM:alchemy:degraded:" + OCCURRED_AT);
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
