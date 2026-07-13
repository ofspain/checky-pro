package com.themistra.auth.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private record SamplePayload(String field) {
    }

    @Mock
    private OutboxEventRepository repository;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(repository, new ObjectMapper());
    }

    @Test
    void publishSerializesPayloadAndSavesEventWithGivenMetadata() {
        publisher.publish("account", "acct-1", "user.registered", 1, new SamplePayload("value"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAggregateType()).isEqualTo("account");
        assertThat(saved.getAggregateId()).isEqualTo("acct-1");
        assertThat(saved.getEventType()).isEqualTo("user.registered");
        assertThat(saved.getSchemaVersion()).isEqualTo(1);
        assertThat(saved.getPayload()).isEqualTo("{\"field\":\"value\"}");
        assertThat(saved.isPublished()).isFalse();
    }

    @Test
    void eachPublishGetsAUniqueId() {
        publisher.publish("account", "acct-1", "user.registered", 1, new SamplePayload("a"));
        publisher.publish("account", "acct-2", "user.registered", 1, new SamplePayload("b"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());

        assertThat(captor.getAllValues().get(0).getId()).isNotEqualTo(captor.getAllValues().get(1).getId());
    }
}
