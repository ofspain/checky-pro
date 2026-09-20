package com.themistra.crypto.events;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11 Gap 7 — pins the actual reason {@link KafkaProducerConfig} exists (design §4c /
 * frozen brief amendment #3): explicit {@code acks=all} and {@code enable.idempotence=true} on the
 * producer, and a {@code KafkaTemplate<String, String>} bean actually present in the context.
 * {@code KafkaAutoConfiguration} is included deliberately, exactly the scenario amendment #3 was
 * written to guard against - this proves our explicit bean wins via
 * {@code @ConditionalOnMissingBean}, not just that it compiles.
 */
class KafkaProducerConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9094")
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(KafkaAutoConfiguration.class))
            .withUserConfiguration(KafkaProducerConfig.class);

    @Test
    @SuppressWarnings("unchecked")
    void producerFactoryHasAcksAllAndIdempotenceEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ProducerFactory<String, String> factory = context.getBean(ProducerFactory.class);
            assertThat(factory).isInstanceOf(DefaultKafkaProducerFactory.class);

            var props = ((DefaultKafkaProducerFactory<String, String>) factory).getConfigurationProperties();
            assertThat(props.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
            assertThat(props.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isEqualTo(true);
            assertThat(props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("localhost:9094");
        });
    }

    @Test
    void exactlyOneStringStringKafkaTemplateBeanExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(KafkaTemplate.class);
            KafkaTemplate<?, ?> template = context.getBean(KafkaTemplate.class);
            assertThat(template.getDefaultTopic()).isNull(); // sanity: real, usable template, not a stub
        });
    }
}
