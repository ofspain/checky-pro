package com.themistra.payment.events;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11 Gap 4 — AC5 explicitly requires an explicit {@code ProducerFactory<String,String>} and
 * {@code KafkaTemplate<String,String>} with {@code acks=all} and {@code enable.idempotence=true};
 * without this test, removing either setting or reverting to Boot's ambiguous autoconfigured
 * generic-typed template would compile and pass the suite. {@code KafkaAutoConfiguration} is
 * included deliberately so this proves our explicit bean wins, not just that it compiles.
 * Mirrors {@code crypto-service}'s identical {@code KafkaProducerConfigTest}.
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
            assertThat(template.getDefaultTopic()).isNull();
        });
    }
}
