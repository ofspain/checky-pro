package com.themistra.auth.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Package-private: other modules record audit events through {@link AuditService}, never here.
 */
interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByAccountUuid(UUID accountUuid, Pageable pageable);
}
