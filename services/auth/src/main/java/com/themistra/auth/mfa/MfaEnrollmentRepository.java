package com.themistra.auth.mfa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /** For mandatory-MFA enforcement (R24, task 18): only a confirmed enrollment counts. */
    Optional<MfaEnrollment> findByAccountIdAndTypeAndConfirmedAtIsNotNull(Long accountId, MfaEnrollment.Type type);

    /** For MFA disable (R28, task 19). */
    void deleteByAccountIdAndType(Long accountId, MfaEnrollment.Type type);

    /**
     * Atomically confirms an enrollment only if it is still unconfirmed; returns rows affected
     * (0 or 1). Mirrors {@code RecoveryCodeRepository.markUsed}'s conditional-update shape —
     * {@code MfaService.confirm} uses this instead of the plain {@link MfaEnrollment#confirm}
     * mutator specifically to close a concurrent-double-confirm race (T18 Phase 9 finding): two
     * racing transactions loading the same unconfirmed row would otherwise both pass verification
     * and both insert a full set of recovery codes. A {@code 0} result means the row was already
     * confirmed (by this same call, a prior call, or a concurrent one) and the caller must not
     * generate recovery codes.
     */
    @Modifying
    @Query("UPDATE MfaEnrollment e SET e.confirmedAt = :confirmedAt WHERE e.id = :id AND e.confirmedAt IS NULL")
    int confirmIfUnconfirmed(@Param("id") Long id, @Param("confirmedAt") Instant confirmedAt);

    /**
     * Atomically deletes an enrollment only if it is still unconfirmed; returns rows affected (0
     * or 1). Used by {@code MfaService.beginEnroll}'s abandoned-enrollment retry path (T18 Phase 9
     * finding) so a row that a concurrent transaction confirmed between the caller's read and this
     * delete is never removed — a {@code 0} result means exactly that race occurred, and the
     * caller must reject the retry rather than silently replace a now-confirmed enrollment.
     */
    @Modifying
    @Query("DELETE FROM MfaEnrollment e WHERE e.id = :id AND e.confirmedAt IS NULL")
    int deleteByIdIfUnconfirmed(@Param("id") Long id);

    /**
     * Atomically records that {@code id}'s TOTP code was just used, but only if the previously
     * accepted step (if any) was strictly earlier than the one just accepted —
     * {@code acceptedStepStart} is that step's start instant ({@link TotpVerifier#stepStart}), and
     * is also what gets stored, not the wall-clock instant of this call. Comparing a step-start
     * value against a later step-start value is what makes "strictly newer" correct: an earlier
     * version of this method stored wall-clock time, which can already be past the *next* step's
     * boundary under ordinary network latency, and could reject a legitimate next-step code as if
     * it were a replay (Phase 8 independent-review finding #3). Returns rows affected (0 or 1);
     * {@code 0} means the submitted code's step was already accepted before (a genuine replay) and
     * the caller must reject it. Mirrors {@link #confirmIfUnconfirmed}'s conditional-update shape —
     * task 20, closing the TOTP replay gap T18 explicitly deferred to this task.
     */
    @Modifying
    @Query("UPDATE MfaEnrollment e SET e.lastUsedAt = :acceptedStepStart WHERE e.id = :id "
            + "AND (e.lastUsedAt IS NULL OR e.lastUsedAt < :acceptedStepStart)")
    int recordUseIfNewer(@Param("id") Long id, @Param("acceptedStepStart") Instant acceptedStepStart);
}
