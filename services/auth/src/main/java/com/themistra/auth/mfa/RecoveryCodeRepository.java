package com.themistra.auth.mfa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Package-private on purpose, consistent with {@code MfaEnrollmentRepository}. No UUID-resolution
 * method here — callers already hold {@code accountId} by the time they touch {@link RecoveryCode}
 * (from an {@link MfaEnrollment} they just loaded/created).
 */
interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    List<RecoveryCode> findByAccountId(Long accountId);

    List<RecoveryCode> findByAccountIdAndUsedAtIsNull(Long accountId);

    /**
     * Atomically marks a recovery code used only if it is still unused; returns the number of
     * rows affected (0 or 1). This single conditional update is the sole redemption path — the
     * {@code usedAt IS NULL} clause makes double-consume impossible under concurrent calls.
     * Mirrors {@code VerificationTokenRepository.markConsumed} exactly.
     */
    @Modifying
    @Query("UPDATE RecoveryCode r SET r.usedAt = :usedAt WHERE r.id = :id AND r.usedAt IS NULL")
    int markUsed(@Param("id") Long id, @Param("usedAt") Instant usedAt);
}
