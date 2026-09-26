package com.themistra.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Themistra Notification Service — idempotent Kafka consumer fanning domain events out to
 * email and in-app channels.
 *
 * <p>Spec: spec/notification-service/design.md. Standing rules: spec/notification-service/agents.md.</p>
 *
 * <p>Bare skeleton (T01) - no {@code @ConfigurationPropertiesScan}/{@code @EnableScheduling}/
 * {@code @EnableSchedulerLock} yet, since no {@code @ConfigurationProperties} class or scheduled job
 * exists yet either. Add each annotation in the task that actually introduces the thing it enables,
 * mirroring crypto-service's own T01 discipline - never mirror a sibling's annotations blindly.</p>
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
