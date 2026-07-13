package com.themistra.auth.audit;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Package-private: other modules record audit events through {@link AuditService}, never here.
 * Query methods for the admin-facing audit browser (target-design §10 GET /admin/audit) arrive
 * with the admin/API stage.
 */
interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
}
