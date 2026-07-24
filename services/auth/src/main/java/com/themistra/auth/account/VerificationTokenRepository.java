package com.themistra.auth.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Package-private on purpose, consistent with {@link AccountRepository} — callers go through
 * {@link VerificationTokenService}.
 */
interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    /**
     * Atomically marks a token used only if it is still unused and unexpired at {@code now};
     * returns the number of rows affected (0 or 1). This single conditional update is the sole
     * redemption path — the {@code usedAt IS NULL} clause makes double-consume impossible under
     * concurrent calls, and folding {@code expiresAt > :now} into the same statement avoids a
     * separate expiry check racing against the mark-used write.
     */
    @Modifying
    @Query("UPDATE VerificationToken t SET t.usedAt = :now "
            + "WHERE t.tokenHash = :tokenHash AND t.usedAt IS NULL AND t.expiresAt > :now")
    int markConsumed(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    /**
     * Invalidates any prior unused, unexpired token for the same account and purpose, so a newly
     * issued token supersedes stale ones rather than leaving them redeemable alongside it.
     * Excludes already-expired tokens — updating them would be a no-op functionally (they're
     * already unredeemable) but would still acquire row locks needlessly.
     */
    @Modifying
    @Query("UPDATE VerificationToken t SET t.usedAt = :now "
            + "WHERE t.accountId = :accountId AND t.purpose = :purpose AND t.usedAt IS NULL "
            + "AND t.expiresAt > :now")
    int invalidateActive(@Param("accountId") Long accountId,
                          @Param("purpose") VerificationToken.Purpose purpose,
                          @Param("now") Instant now);
}
