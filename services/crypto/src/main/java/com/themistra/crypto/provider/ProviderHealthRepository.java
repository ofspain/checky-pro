package com.themistra.crypto.provider;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ProviderHealthRepository extends JpaRepository<ProviderHealth, Long> {

    Optional<ProviderHealth> findByChainAndProvider(String chain, String provider);
}
