package com.themistra.crypto.events;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

    Optional<OutboxEvent> findByIdempotencyKey(String idempotencyKey);
}
