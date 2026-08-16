package com.themistra.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RefreshTokenFamilyRepository extends JpaRepository<RefreshTokenFamily, UUID> {

    Optional<RefreshTokenFamily> findByAuthorizationId(String authorizationId);

    Optional<RefreshTokenFamily> findByCurrentTokenHashAndRevokedAtIsNull(String tokenHash);

    List<RefreshTokenFamily> findByPrincipalNameAndRevokedAtIsNull(String principalName);

    /** For {@code DELETE /accounts/me/sessions/{familyId}} (T28, R37) — deliberately no
     * {@code revokedAt} filter (frozen brief D1): an already-revoked-but-owned family must still
     * be found here so {@link RefreshTokenFamily#revoke} idempotency (not this query) is what
     * decides success, and a genuinely nonexistent or unowned family is the only 404 case. */
    Optional<RefreshTokenFamily> findByFamilyIdAndPrincipalName(UUID familyId, String principalName);

    /** Cleanup job (T30, R40) - hard-deletes families revoked before the retention cutoff. The
     * database's own {@code ON DELETE CASCADE} on {@code refresh_token_archive.family_id} (V2)
     * removes each deleted family's archive rows automatically; a family that is still active
     * (never revoked) is never matched by this query regardless of age. */
    @Modifying
    @Query("DELETE FROM RefreshTokenFamily f WHERE f.revokedAt IS NOT NULL AND f.revokedAt < :cutoff")
    int deleteRevokedBefore(@Param("cutoff") Instant cutoff);
}
