package com.themistra.auth.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the audit mirror genuinely reaches auth.security.audit and — the property that
 * matters most for a security log — that the raw IP and user agent never appear in the
 * Kafka-visible mirror, only the row written to auth_audit (AuditMirrorPayload's design, §12).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuditTrailIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private AuditService auditService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaContainer kafka;

    private Consumer<String, String> testConsumer;

    @BeforeEach
    void subscribeToAuditTopic() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "audit-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        testConsumer = new KafkaConsumer<>(props);
        testConsumer.subscribe(List.of("auth.security.audit"));
    }

    @AfterEach
    void closeConsumer() {
        if (testConsumer != null) {
            testConsumer.close();
        }
    }

    @Test
    void recordMirrorsToKafkaWithoutLeakingIpOrUserAgent() {
        // T37 fix: a real account is required - auth_audit.account_uuid has a real FK to accounts,
        // and a fabricated UUID.randomUUID() (this test's original form) violates it.
        UUID accountUuid = registerAndActivate("audit-trail-" + UUID.randomUUID() + "@example.com");

        auditService.record(new RecordAuditEventRequest(
                "account.suspended", AuditOutcome.SUCCESS, accountUuid, null,
                "203.0.113.9", "Mozilla/5.0 integration-test-agent", "trace-xyz",
                Map.of("reason", "integration-test")));

        // Phase 11, Kimi Gap 6: assert the auth_audit row itself, not just its Kafka mirror - a
        // regression that wrote only to Kafka (or silently dropped the DB insert) would otherwise
        // pass this test on the mirror assertion alone.
        assertThat(auditService.list(accountUuid, Pageable.unpaged()).getContent())
                .anySatisfy(event -> assertThat(event.eventType()).isEqualTo("account.suspended"));

        // Kimi sweep review, Finding 6 (2026-10-20): registerAndActivate also writes an
        // account.activated audit event with the same key. The old loop asserted the event type of
        // every record with this key, so when both arrived in one batch it threw, the batch was
        // consumed and lost, and the wait timed out (a flake that depended on batching). Records are
        // now kept across polls and the suspended one is picked out by key and event type.
        List<ConsumerRecord<String, String>> received = new java.util.ArrayList<>();
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            testConsumer.poll(Duration.ofMillis(500)).forEach(received::add);
            assertThat(received)
                    .as("audit mirror for %s observed on the real topic", accountUuid)
                    .anyMatch(record -> isSuspendedEventFor(record, accountUuid));
        });

        for (ConsumerRecord<String, String> record : received) {
            if (accountUuid.toString().equals(record.key())) {
                // No mirrored record for this account may carry the caller's IP or user agent.
                assertThat(record.value()).doesNotContain("Mozilla/5.0 integration-test-agent");
                assertThat(record.value()).doesNotContain("203.0.113.9");
            }
        }
    }

    private boolean isSuspendedEventFor(ConsumerRecord<String, String> record, UUID accountUuid) {
        if (!accountUuid.toString().equals(record.key())) {
            return false;
        }
        try {
            return "account.suspended".equals(objectMapper.readTree(record.value()).get("eventType").asText());
        } catch (Exception e) {
            return false;
        }
    }

    private UUID registerAndActivate(String email) {
        AccountResponse registered = accountService.register(new RegisterAccountRequest(email, PASSWORD));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        return registered.accountUuid();
    }
}
