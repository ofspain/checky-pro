package com.themistra.crypto.token;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface TokenAllowlistRepository extends JpaRepository<TokenAllowlist, Long> {

    Optional<TokenAllowlist> findTopByOrderByVersionDesc();

    Optional<TokenAllowlist> findByChainAndContractAddressAndVersion(String chain, String contractAddress, int version);
}
