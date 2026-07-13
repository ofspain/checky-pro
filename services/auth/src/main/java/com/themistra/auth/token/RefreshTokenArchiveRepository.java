package com.themistra.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface RefreshTokenArchiveRepository extends JpaRepository<RefreshTokenArchiveEntry, Long> {

    Optional<RefreshTokenArchiveEntry> findByTokenHash(String tokenHash);
}
