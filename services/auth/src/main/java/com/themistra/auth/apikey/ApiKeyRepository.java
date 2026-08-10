package com.themistra.auth.apikey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Package-private on purpose, consistent with {@code MfaEnrollmentRepository}/
 * {@code RecoveryCodeRepository}: only this module's service ({@link ApiKeyService}, task 24)
 * touches this.
 */
interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /**
     * Looks up API keys by their public {@code prefix}. Returns a {@code List}, not an
     * {@code Optional}: unlike {@code key_hash}, {@code prefix} has no DB-level {@code UNIQUE}
     * constraint (frozen brief, Phase 3 finding #4) — an {@code Optional} return type would
     * silently assume a uniqueness the schema doesn't enforce. Callers (task 25's exchange flow)
     * are responsible for deciding how to handle more than one match. This method applies no
     * lifecycle filtering: it returns rows regardless of {@code revokedAt}/{@code expiresAt} —
     * callers must check those themselves rather than treating any returned row as an active key.
     */
    List<ApiKey> findByPrefix(String prefix);

    /** Every key owned by an account, for {@link ApiKeyService#list}. */
    List<ApiKey> findByAccountId(Long accountId);

    /** Resolves a key's internal id from its external UUID, for {@link ApiKeyService#revoke} —
     * the only identifier a caller outside this module ever holds for a specific key. */
    Optional<ApiKey> findByKeyUuid(UUID keyUuid);

    /** Resolves the internal id needed to create/list/revoke a key — no Java-level dependency on
     * {@code com.themistra.auth.account.Account} (L12). Mirrors
     * {@code MfaEnrollmentRepository.findAccountIdByUuid} exactly. */
    @Query(value = "SELECT a.id FROM accounts a WHERE a.account_uuid = :accountUuid", nativeQuery = true)
    Optional<Long> findAccountIdByUuid(@Param("accountUuid") UUID accountUuid);

    /** The reverse of {@link #findAccountIdByUuid} — {@link ApiKeyService#exchange} only has a
     * matched key's internal {@code accountId} and must return the external UUID to its caller. */
    @Query(value = "SELECT a.account_uuid FROM accounts a WHERE a.id = :accountId", nativeQuery = true)
    Optional<UUID> findAccountUuidById(@Param("accountId") Long accountId);

    /** Unconditional: by the time {@link ApiKeyService#exchange} calls this, the row is already
     * known to exist and be valid — no race to guard against, unlike {@code revokeIfActive}. */
    @Modifying
    @Query("UPDATE ApiKey k SET k.lastUsedAt = :lastUsedAt WHERE k.id = :id")
    int updateLastUsedAt(@Param("id") Long id, @Param("lastUsedAt") Instant lastUsedAt);

    /**
     * Atomically revokes a key only if it is not already revoked; returns rows affected (0 or 1).
     * A {@code 0} result means the key was already revoked (by this same call, a prior call, or a
     * concurrent one) — {@link ApiKeyService#revoke} treats that as success, not an error, making
     * revoke idempotent (mirrors {@code RoleService.removeRole}'s idempotent-no-op precedent).
     */
    @Modifying
    @Query("UPDATE ApiKey k SET k.revokedAt = :revokedAt WHERE k.id = :id AND k.revokedAt IS NULL")
    int revokeIfActive(@Param("id") Long id, @Param("revokedAt") Instant revokedAt);
}
