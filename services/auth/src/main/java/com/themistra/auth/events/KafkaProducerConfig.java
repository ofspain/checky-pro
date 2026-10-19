package com.themistra.auth.events;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Explicit producer configuration rather than relying on Boot's autoconfigured KafkaTemplate —
 * autoconfiguration's generic type resolution (KafkaTemplate&lt;Object,Object&gt; vs the
 * String/String pair the relay needs) is version-sensitive; declaring it directly here removes
 * the ambiguity entirely.
 */
@Configuration
public class KafkaProducerConfig {

    /** Open-issues sweep (2026-10-19): the broker address comes from Spring Boot's {@link
     * KafkaConnectionDetails}, not the raw {@code spring.kafka.bootstrap-servers} property. In
     * production both give the same value. In tests, {@code @ServiceConnection} supplies the
     * container's address only through the connection details, so reading the raw property made the
     * outbox publish to the local dev broker (localhost:9094) while the tests listened on their own
     * container, and three Kafka integration tests timed out. */
    @Bean
    public ProducerFactory<String, String> outboxProducerFactory(KafkaConnectionDetails connectionDetails) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducer().getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate(
            ProducerFactory<String, String> outboxProducerFactory) {
        return new KafkaTemplate<>(outboxProducerFactory);
    }
}
