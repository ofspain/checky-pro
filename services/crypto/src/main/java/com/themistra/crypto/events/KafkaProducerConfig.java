package com.themistra.crypto.events;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
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
 * the ambiguity entirely (mirrors services/auth's identical KafkaProducerConfig).
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, String> outboxProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
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
