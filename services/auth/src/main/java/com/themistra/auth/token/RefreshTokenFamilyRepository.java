package com.themistra.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RefreshTokenFamilyRepository extends JpaRepository<RefreshTokenFamily, UUID> {

    Optional<RefreshTokenFamily> findByAuthorizationId(String authorizationId);

    Optional<RefreshTokenFamily> findByCurrentTokenHashAndRevokedAtIsNull(String tokenHash);

    List<RefreshTokenFamily> findByPrincipalNameAndRevokedAtIsNull(String principalName);
}
