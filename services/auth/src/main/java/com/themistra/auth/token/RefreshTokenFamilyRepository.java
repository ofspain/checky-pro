package com.themistra.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
