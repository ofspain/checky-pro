package com.themistra.payment.receipt;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * T19. No custom query needed yet - {@code save(...)} alone is sufficient for this task's own
 * scope. Package-private, mirrors every other repository's own visibility in this codebase.
 */
interface ReceiptRepository extends JpaRepository<Receipt, Long> {
}
