package com.themistra.auth.mfa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Package-private on purpose, consistent with {@code MfaEnrollmentRepository}. No UUID-resolution
 * method here — callers already hold {@code accountId} by the time they touch {@link RecoveryCode}
 * (from an {@link MfaEnrollment} they just loaded/created).
 */
interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    List<RecoveryCode> findByAccountId(Long accountId);

    List<RecoveryCode> findByAccountIdAndUsedAtIsNull(Long accountId);

    /** For recovery-code verification (R25, task 18): find the specific code a caller submitted. */
    Optional<RecoveryCode> findByAccountIdAndCodeHash(Long accountId, String codeHash);

    /**
     * Atomically marks a recovery code used only if it is still unused; returns the number of
     * rows affected (0 or 1). This single conditional update is the sole redemption path — the
     * {@code usedAt IS NULL} clause makes double-consume impossible under concurrent calls.
     * Mirrors {@code VerificationTokenRepository.markConsumed} exactly.
     *
     * <p>Callers must check the return value: {@code 0} means the code was already used (or the
     * id didn't exist) and authentication must be rejected — treating a {@code 0} result as
     * success would let a replayed recovery code pass.</p>
     */
    @Modifying
    @Query("UPDATE RecoveryCode r SET r.usedAt = :usedAt WHERE r.id = :id AND r.usedAt IS NULL")
    int markUsed(@Param("id") Long id, @Param("usedAt") Instant usedAt);

    /** For MFA disable (R28, task 18): invalidates every recovery code, used or not. */
    void deleteByAccountId(Long accountId);
}
