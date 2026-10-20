package com.themistra.notification.common.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Expiry of parked payment events (FU-T07-4 follow-up, open-issues sweep 2026-10-20). An event
 * parked because its route hadn't arrived is dropped after {@code maxAgeHours}; by then the route
 * is not coming (a lost or never-published {@code invoice.created}), and keeping the row forever
 * only grows the table. {@code expiryIntervalSeconds} is how often {@code ParkedEventExpiryJob}
 * runs.
 */
@ConfigurationProperties(prefix = "themistra.notification.parked-events")
@Validated
public record ParkedEventProperties(
        @Min(1) int maxAgeHours,
        @Min(1) int expiryIntervalSeconds
) {
}
