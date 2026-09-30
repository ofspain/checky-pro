package com.themistra.notification.inapp;

import com.themistra.notification.delivery.DeliveryOrchestrator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Real Postgres (Testcontainers), real Spring context, real collaborators end to end - the two
 * `package.md` §8 named tests (R16/R17) live here. {@link InappStreamRegistry} is overridden with a
 * Mockito spy (real behavior, observable via {@code verify}) so the real post-commit push can be
 * asserted without needing a live HTTP/SSE client - proving this task's own code (the deferred
 * push's timing and arguments), not re-testing Spring's own already-trustworthy {@code SseEmitter}
 * implementation.
 */
@Testcontainers
@SpringBootTest
@Import(InAppChannelIntegrationTest.TestConfig.class)
class InAppChannelIntegrationTest {

    private static final String NOTIFICATION_APP_PASSWORD = "it-notification-app-password";

    @TestConfiguration
    static class TestConfig {
        static final Instant FIXED_INSTANT = Instant.parse("2026-03-01T00:00:00Z");

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        InappStreamRegistry spyInappStreamRegistry() {
            return spy(new InappStreamRegistry());
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "notification_app");
        registry.add("spring.datasource.password", () -> NOTIFICATION_APP_PASSWORD);
    }

    @BeforeAll
    static void migrateAndProvisionPassword() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS notifications");
            statement.execute("CREATE EXTENSION IF NOT EXISTS citext SCHEMA notifications");
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("notifications")
                .load()
                .migrate();

        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("ALTER ROLE notification_app PASSWORD '" + NOTIFICATION_APP_PASSWORD + "'");
        }
    }

    @Autowired
    private DeliveryOrchestrator orchestrator;

    @Autowired
    private InappStreamRegistry streamRegistry;

    @Autowired
    private InappStreamController streamController;

    @Autowired
    private InappReadController readController;

    @Autowired
    private InappNotificationRepository repository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private static Jwt jwtFor(UUID accountUuid) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(accountUuid.toString())
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2099-01-01T00:00:00Z"))
                .build();
    }

    /** R17 named test - real end-to-end: a real dispatched event persists a real row, and the real
     * read endpoint (scoped to the caller's own {@code sub}) returns it. */
    @Test
    void shouldReturnUnreadInAppNotificationsForCaller() {
        UUID accountUuid = UUID.randomUUID();

        orchestrator.dispatch(accountUuid, "user.registered", Map.of("sourceEventKey", "inapp-key-1"));

        List<InappNotification.View> unread = readController.unread(jwtFor(accountUuid));

        assertThat(unread).hasSize(1);
        InappNotification.View view = unread.get(0);
        assertThat(view.category()).isEqualTo("SECURITY");
        assertThat(view.title()).contains("Welcome to Themistra");
        assertThat(view.link()).isNull();
        assertThat(view.createdAt()).isEqualTo(TestConfig.FIXED_INSTANT);
    }

    /** L8/R17: account A's own request must never return account B's own notifications. */
    @Test
    void neverReturnsAnotherAccountsNotifications() {
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        orchestrator.dispatch(accountA, "user.registered", Map.of("sourceEventKey", "inapp-key-2"));

        List<InappNotification.View> forB = readController.unread(jwtFor(accountB));

        assertThat(forB).isEmpty();
    }

    /** R16 named test - real end-to-end: a real dispatched event, after its own transaction commits,
     * pushes to a real, registered {@link InappStreamRegistry} with the correct account/event
     * name/view. The registry is a Mockito spy (real behavior, observable), not a mock - proving
     * this task's own deferred-push timing and argument correctness, not Spring's own SseEmitter
     * wire-level behavior. */
    @Test
    void shouldStreamInAppNotificationsToAuthenticatedRecipientOnly() {
        UUID accountUuid = UUID.randomUUID();
        streamController.stream(jwtFor(accountUuid));
        verify(streamRegistry).register(accountUuid);

        orchestrator.dispatch(accountUuid, "user.registered", Map.of("sourceEventKey", "inapp-key-3"));

        verify(streamRegistry).push(eq(accountUuid), eq("notification"), any(InappNotification.View.class));
    }

    /** L8: registering a stream for account A must never itself cause a push meant for account B,
     * and dispatching for B must never push to A's own connection. */
    @Test
    void streamPushIsScopedToTheRegisteredAccountOnly() {
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        streamController.stream(jwtFor(accountA));

        orchestrator.dispatch(accountB, "user.registered", Map.of("sourceEventKey", "inapp-key-4"));

        verify(streamRegistry).push(eq(accountB), eq("notification"), any());
        verify(streamRegistry, org.mockito.Mockito.never()).push(eq(accountA), org.mockito.ArgumentMatchers.anyString(), any());
    }

    /** AC10/Kimi Phase 3 Finding #6: the push is deferred until after commit - if the surrounding
     * transaction rolls back, no push and no persisted row survive. Mirrors
     * {@code DeliveryOrchestratorIntegrationTest}'s own identical rollback-proof pattern. */
    @Test
    void aRolledBackTransactionLeavesNoRowAndTriggersNoPush() {
        UUID accountUuid = UUID.randomUUID();
        var transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            orchestrator.dispatch(accountUuid, "user.registered", Map.of("sourceEventKey", "inapp-key-5"));
            status.setRollbackOnly();
        });

        assertThat(repository.findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(accountUuid)).isEmpty();
        verify(streamRegistry, org.mockito.Mockito.never()).push(eq(accountUuid), org.mockito.ArgumentMatchers.anyString(), any());
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
