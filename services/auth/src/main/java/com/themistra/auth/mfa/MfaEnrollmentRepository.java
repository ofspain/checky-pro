package com.themistra.auth.mfa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Package-private on purpose, consistent with {@code LockoutStateRepository}/
 * {@code VerificationTokenRepository}: only this module's future service (task 18) touches this.
 */
interface MfaEnrollmentRepository extends JpaRepository<MfaEnrollment, Long> {

    /** Resolves the internal id needed to insert a brand-new enrollment row — no Java-level
     * dependency on {@code com.themistra.auth.account.Account} (L12). */
    @Query(value = "SELECT a.id FROM accounts a WHERE a.account_uuid = :accountUuid", nativeQuery = true)
    Optional<Long> findAccountIdByUuid(@Param("accountUuid") UUID accountUuid);

    Optional<MfaEnrollment> findByAccountIdAndType(Long accountId, MfaEnrollment.Type type);
}
