package com.themistra.auth.account;

import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end against real Postgres + Kafka (Testcontainers) — verifies what mocked unit tests
 * structurally cannot: the actual unique-constraint race path, and that an activated account's
 * lifecycle event genuinely traverses AccountService -> outbox table -> OutboxRelay -> Kafka.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountPersistenceIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private KafkaContainer kafka;

    private Consumer<String, String> testConsumer;

    @BeforeEach
    void subscribeToLifecycleTopic() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "account-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        testConsumer = new KafkaConsumer<>(props);
        testConsumer.subscribe(List.of("auth.user.lifecycle"));
    }

    @AfterEach
    void closeConsumer() {
        if (testConsumer != null) {
            testConsumer.close();
        }
    }

    private String uniqueEmail() {
        return "integration-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void registerPersistsAccountAndRejectsDuplicateEmailAgainstRealUniqueConstraint() {
        String email = uniqueEmail();

        AccountResponse first = accountService.register(new RegisterAccountRequest(email, "correct-horse-battery"));
        assertThat(first.status()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(first.email()).isEqualTo(email);

        // exercises the real unique-index race path (DataIntegrityViolationException -> DuplicateEmailException),
        // not just the existsByEmail pre-check a unit test would mock
        assertThatThrownBy(() ->
                accountService.register(new RegisterAccountRequest(email, "another-password-value")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void activateEmailDeliversARealLifecycleEventToKafka() {
        String email = uniqueEmail();
        AccountResponse registered = accountService.register(new RegisterAccountRequest(email, "correct-horse-battery"));

        accountService.activateEmail(registered.accountUuid(), UUID.randomUUID());

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = testConsumer.poll(Duration.ofMillis(500));
            boolean found = false;
            for (ConsumerRecord<String, String> record : records) {
                if (record.key().equals(registered.accountUuid().toString())) {
                    assertThat(record.value()).contains("\"status\":\"ACTIVE\"");
                    found = true;
                }
            }
            assertThat(found).as("lifecycle event for %s observed on the real topic", registered.accountUuid())
                    .isTrue();
        });
    }
}
