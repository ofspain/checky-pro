package com.themistra.notification;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Themistra Notification Service — idempotent Kafka consumer fanning domain events out to
 * email and in-app channels.
 *
 * <p>Spec: spec/notification-service/design.md. Standing rules: spec/notification-service/agents.md.</p>
 *
 * <p>{@code @ConfigurationPropertiesScan} added in T03, the first task to introduce any
 * {@code @ConfigurationProperties} class. {@code @EnableScheduling}/{@code @EnableSchedulerLock}
 * added in T14, the first task to introduce a scheduled job ({@code RetryScheduler}) - mirroring
 * crypto-service's own T01/T03 discipline of adding each annotation in the task that actually
 * introduces the thing it enables, never mirroring a sibling's annotations blindly.
 * {@code defaultLockAtMostFor} is a required, ShedLock-wide safety ceiling (in case a replica dies
 * mid-lock); {@code RetryScheduler}'s own {@code @SchedulerLock} sets its own, tighter value.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
