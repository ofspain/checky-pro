package com.themistra.notification.inapp;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentCaptor.forClass;

/**
 * Mocked collaborators, no Spring context, no Docker. Exercises only the "no transaction
 * synchronization active" branch, where the push fires immediately - the "deferred until
 * afterCommit" branch needs a real, active {@link org.springframework.transaction.PlatformTransactionManager}
 * to mean anything and is proven at {@link InAppChannelIntegrationTest}'s own integration level
 * instead (mirrors {@code IdempotencyGuardIntegrationTest}'s own established "unit test proves the
 * logic given a known collaborator response; integration test proves the real transactional
 * behavior" split).
 */
class InappNotificationAppenderTest {

    private final InappNotificationRepository repository = mock(InappNotificationRepository.class);
    private final InappStreamRegistry streamRegistry = mock(InappStreamRegistry.class);
    private final InappNotificationAppender appender = new InappNotificationAppender(repository, streamRegistry);

    @Test
    void appendAndPushSavesTheCorrectRowAndPushesImmediatelyWhenNoTransactionIsActive() {
        UUID accountUuid = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-03-01T00:00:00Z");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        appender.appendAndPush(accountUuid, "SECURITY", "title", "body", createdAt);

        var saveCaptor = forClass(InappNotification.class);
        verify(repository).save(saveCaptor.capture());
        InappNotification saved = saveCaptor.getValue();
        assertThat(saved.getAccountUuid()).isEqualTo(accountUuid);
        assertThat(saved.getCategory()).isEqualTo("SECURITY");
        assertThat(saved.getTitle()).isEqualTo("title");
        assertThat(saved.getBody()).isEqualTo("body");
        assertThat(saved.getLink()).isNull();
        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(saved.getNotificationUuid()).isNotNull();

        var pushCaptor = forClass(InappNotification.View.class);
        verify(streamRegistry).push(org.mockito.ArgumentMatchers.eq(accountUuid),
                org.mockito.ArgumentMatchers.eq("notification"), pushCaptor.capture());
        assertThat(pushCaptor.getValue().category()).isEqualTo("SECURITY");
        assertThat(pushCaptor.getValue().title()).isEqualTo("title");
    }

    @Test
    void eachCallGeneratesAFreshNotificationUuid() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        appender.appendAndPush(UUID.randomUUID(), "SECURITY", "t1", "b1", Instant.now());
        appender.appendAndPush(UUID.randomUUID(), "SECURITY", "t2", "b2", Instant.now());

        var captor = forClass(InappNotification.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getNotificationUuid())
                .isNotEqualTo(captor.getAllValues().get(1).getNotificationUuid());
    }
}
