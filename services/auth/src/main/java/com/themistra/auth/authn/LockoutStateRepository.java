package com.themistra.auth.authn;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Package-private on purpose (module-boundary rule, mirrors {@code AccountRepository}): only
 * {@link LockoutService} touches this. Both queries are native SQL against table/column names,
 * not JPQL against the {@code Account} entity — no Java-level dependency on
 * {@code com.themistra.auth.account.Account}, so L12 / {@code ArchitectureTest}'s existing rule is
 * satisfied without needing a new one.
 */
interface LockoutStateRepository extends JpaRepository<LockoutState, Long> {

    /**
     * Loads the row for the given account, locking only the {@code lockout_state} row ({@code
     * FOR UPDATE OF ls}) for the remainder of the caller's transaction — the security-critical
     * counter must never lose a concurrent update. Deliberately scoped to exclude the joined
     * {@code accounts} row: an unqualified {@code FOR UPDATE} locks every table contributing to
     * the result set, which would otherwise serialize unrelated {@code AccountService} operations
     * (password change, suspend, etc.) against every login attempt for no reason.
     */
    @Query(value = "SELECT ls.* FROM lockout_state ls JOIN accounts a ON a.id = ls.account_id "
            + "WHERE a.account_uuid = :accountUuid FOR UPDATE OF ls", nativeQuery = true)
    Optional<LockoutState> findByAccountUuidForUpdate(@Param("accountUuid") UUID accountUuid);

    /**
     * Plain, non-locking read — for a point-in-time check consumed before any mutation, not part
     * of a read-evaluate-write cycle (T13's {@code LockoutService#isCurrentlyLocked}). Taking the
     * {@code FOR UPDATE} lock here would be unnecessary contention for a query that never writes.
     */
    @Query(value = "SELECT ls.* FROM lockout_state ls JOIN accounts a ON a.id = ls.account_id "
            + "WHERE a.account_uuid = :accountUuid", nativeQuery = true)
    Optional<LockoutState> findByAccountUuid(@Param("accountUuid") UUID accountUuid);

    /** Resolves the internal id needed to insert a brand-new row on an account's first failure. */
    @Query(value = "SELECT a.id FROM accounts a WHERE a.account_uuid = :accountUuid", nativeQuery = true)
    Optional<Long> findAccountIdByUuid(@Param("accountUuid") UUID accountUuid);
}
