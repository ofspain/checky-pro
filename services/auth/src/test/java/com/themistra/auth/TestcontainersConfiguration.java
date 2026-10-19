package com.themistra.auth;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shared Testcontainers wiring: every integration test runs against real Postgres and Kafka —
 * never a live shared database (gap-analysis §1 #25).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }

    /** Open-issues sweep (2026-10-19): auth's three topics are created as soon as the broker starts.
     * Before, they were auto-created by the first producer send. A test consumer that had already
     * subscribed to the missing topic often did not see it appear within its 15-second wait, so
     * AccountPersistenceIntegrationTest, AuditTrailIntegrationTest and EndToEndLifecycleIntegrationTest
     * timed out. Starting the container here is safe: Testcontainers' start() is idempotent. */
    static final List<String> TOPICS = List.of("auth.user.lifecycle", "auth.email.requested", "auth.security.audit");

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
        kafka.start();
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            admin.createTopics(TOPICS.stream().map(topic -> new NewTopic(topic, 1, (short) 1)).toList())
                    .all().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create auth topics on the test broker", e);
        }
        return kafka;
    }
}
