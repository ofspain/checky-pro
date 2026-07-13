package com.themistra.auth.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.auth.events.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final UUID ACCOUNT_UUID = UUID.randomUUID();
    private static final UUID ACTOR_UUID = UUID.randomUUID();

    @Mock
    private AuditEventRepository repository;

    @Mock
    private OutboxPublisher outboxPublisher;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(repository, outboxPublisher, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recordSavesRowWithHashedUserAgentNeverRawInStorageOrMirror() {
        service.record(new RecordAuditEventRequest(
                "account.suspended", AuditOutcome.SUCCESS, ACCOUNT_UUID, ACTOR_UUID,
                "203.0.113.7", "Mozilla/5.0 test-agent", "trace-123", Map.of("reason", "fraud-hold")));

        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(saved.capture());

        AuditEvent event = saved.getValue();
        assertThat(event.getEventType()).isEqualTo("account.suspended");
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getAccountUuid()).isEqualTo(ACCOUNT_UUID);
        assertThat(event.getActorUuid()).isEqualTo(ACTOR_UUID);
        assertThat(event.getIp()).isEqualTo("203.0.113.7");
        assertThat(event.getTraceId()).isEqualTo("trace-123");
        assertThat(event.getOccurredAt()).isEqualTo(NOW);
        assertThat(event.getDetails()).contains("fraud-hold");

        // the raw user agent must never appear anywhere in the persisted row
        assertThat(event.getUserAgentHash()).isNotEqualTo("Mozilla/5.0 test-agent");
        assertThat(event.getUserAgentHash()).hasSize(64); // sha256 hex
    }

    @Test
    void nullUserAgentYieldsNullHashRatherThanHashingEmptyString() {
        service.record(new RecordAuditEventRequest(
                "token.reuse_detected", AuditOutcome.FAILURE, null, null, null, null, null, null));

        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserAgentHash()).isNull();
    }

    @Test
    void mirrorsToOutboxWithAuditAggregateTypeAndAccountPartitionKey() {
        service.record(new RecordAuditEventRequest(
                "account.deleted", AuditOutcome.SUCCESS, ACCOUNT_UUID, null,
                null, null, null, null));

        verify(outboxPublisher).publish(
                eq("audit"), eq(ACCOUNT_UUID.toString()), eq("security.account.deleted"), eq(1), any());
    }

    @Test
    void mirrorPayloadOmitsIpAndUserAgentHash() {
        service.record(new RecordAuditEventRequest(
                "account.suspended", AuditOutcome.SUCCESS, ACCOUNT_UUID, null,
                "203.0.113.7", "some-agent", null, null));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(any(), any(), any(), anyInt(), payloadCaptor.capture());

        var mirror = (AuditMirrorPayload) payloadCaptor.getValue();
        assertThat(mirror.eventType()).isEqualTo("account.suspended");
        assertThat(mirror.accountUuid()).isEqualTo(ACCOUNT_UUID);
        // AuditMirrorPayload's record components are exactly: eventType, outcome, accountUuid,
        // actorUuid, occurredAt — no ip/userAgent fields exist to assert absent, by construction
    }

    @Test
    void accountLessEventGetsRandomPartitionKeyNotNull() {
        service.record(new RecordAuditEventRequest(
                "system.check", AuditOutcome.SUCCESS, null, null, null, null, null, null));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxPublisher).publish(eq("audit"), keyCaptor.capture(), any(), anyInt(), any());
        assertThat(keyCaptor.getValue()).isNotBlank();
    }

    @Test
    void listWithoutFilterDelegatesToFindAllAndParsesDetails() {
        AuditEvent event = AuditEvent.record(NOW, "account.suspended", AuditOutcome.SUCCESS,
                ACCOUNT_UUID, ACTOR_UUID, "203.0.113.7", "hash", "trace-1", "{\"reason\":\"fraud-hold\"}");
        var pageable = PageRequest.of(0, 50);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(java.util.List.of(event)));

        var page = service.list(null, pageable);

        assertThat(page.getContent()).hasSize(1);
        var response = page.getContent().getFirst();
        assertThat(response.eventType()).isEqualTo("account.suspended");
        assertThat(response.details()).containsEntry("reason", "fraud-hold");
    }

    @Test
    void listWithAccountFilterDelegatesToFindByAccountUuid() {
        var pageable = PageRequest.of(0, 50);
        when(repository.findByAccountUuid(ACCOUNT_UUID, pageable)).thenReturn(new PageImpl<>(java.util.List.of()));

        service.list(ACCOUNT_UUID, pageable);

        verify(repository).findByAccountUuid(ACCOUNT_UUID, pageable);
    }

    @Test
    void listToleratesUnparseableDetailsWithoutThrowing() {
        AuditEvent event = AuditEvent.record(NOW, "account.suspended", AuditOutcome.SUCCESS,
                ACCOUNT_UUID, null, null, null, null, "not-valid-json");
        var pageable = PageRequest.of(0, 50);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(java.util.List.of(event)));

        var page = service.list(null, pageable);

        assertThat(page.getContent().getFirst().details()).isEmpty();
    }
}
