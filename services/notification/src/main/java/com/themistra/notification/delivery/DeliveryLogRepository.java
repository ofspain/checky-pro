package com.themistra.notification.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * No custom methods - plain {@code save()} (inherited from {@link JpaRepository}) is this task's
 * own entire write path. Unlike every prior module repository (all read-mostly, with one narrow,
 * native-query write path each), {@code delivery_log} is append-only with no upsert/conflict logic
 * needed - a genuine {@code INSERT} per attempt is exactly what standard JPA persistence already
 * does correctly.
 */
interface DeliveryLogRepository extends JpaRepository<DeliveryLog, Long> {
}
