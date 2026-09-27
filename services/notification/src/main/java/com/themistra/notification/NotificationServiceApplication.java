package com.themistra.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Themistra Notification Service — idempotent Kafka consumer fanning domain events out to
 * email and in-app channels.
 *
 * <p>Spec: spec/notification-service/design.md. Standing rules: spec/notification-service/agents.md.</p>
 *
 * <p>{@code @ConfigurationPropertiesScan} added in T03, the first task to introduce any
 * {@code @ConfigurationProperties} class. {@code @EnableScheduling}/{@code @EnableSchedulerLock}
 * are still absent - no scheduled job exists yet (task 14). Add each annotation in the task that
 * actually introduces the thing it enables, mirroring crypto-service's own T01/T03 discipline -
 * never mirror a sibling's annotations blindly.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
