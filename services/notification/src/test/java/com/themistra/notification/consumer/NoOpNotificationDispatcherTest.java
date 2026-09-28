package com.themistra.notification.consumer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kimi Phase 8 Finding #5 / L4: {@link NoOpNotificationDispatcher} logs only
 * {@code accountUuid}/{@code notificationKind}/{@code eventData}'s key set - never
 * {@code eventData}'s own values, which may carry a raw verification/reset token. Captures the
 * real formatted log output via a Logback {@link ListAppender}, mirroring
 * {@code EmailRequestedEventContractTest.toStringExcludesTheRawToken}'s own precedent at the DTO
 * layer, one level further down the call chain.
 */
class NoOpNotificationDispatcherTest {

    private final NoOpNotificationDispatcher dispatcher = new NoOpNotificationDispatcher();
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(NoOpNotificationDispatcher.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void dispatchNeverLogsTheRawTokenValue() {
        dispatcher.dispatch(UUID.randomUUID(), "verify_email",
                Map.of("token", "super-secret-raw-token-value"));

        String formattedOutput = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", String::concat);

        assertThat(formattedOutput).doesNotContain("super-secret-raw-token-value");
    }

    @Test
    void dispatchLogsTheEventDataKeySetOnly() {
        dispatcher.dispatch(UUID.randomUUID(), "password_reset", Map.of("token", "another-secret"));

        String formattedOutput = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", String::concat);

        assertThat(formattedOutput).contains("[token]");
        assertThat(formattedOutput).doesNotContain("another-secret");
    }

    @Test
    void dispatchLogsAccountUuidAndNotificationKind() {
        UUID accountUuid = UUID.randomUUID();

        dispatcher.dispatch(accountUuid, "user.registered", Map.of());

        String formattedOutput = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", String::concat);

        assertThat(formattedOutput).contains(accountUuid.toString());
        assertThat(formattedOutput).contains("user.registered");
    }
}
