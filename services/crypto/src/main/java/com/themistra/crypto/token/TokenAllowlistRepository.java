package com.themistra.crypto.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface TokenAllowlistRepository extends JpaRepository<TokenAllowlist, Long> {

    Optional<TokenAllowlist> findByChainAndContractAddressAndVersion(String chain, String contractAddress, int version);

    /**
     * The entry for {@code (chain, contractAddress)} at that chain's own current (highest) version,
     * in a single atomic query. Phase 9 (Kimi Phase 8 Issues 1 and 3): version is scoped per chain,
     * not globally - a version bump on one chain must never affect another chain's lookups - and the
     * max-version read and the keyed match happen in one statement, not two separate round trips that
     * could observe a concurrently-committed newer version in between.
     */
    @Query("SELECT t FROM TokenAllowlist t WHERE t.chain = :chain AND t.contractAddress = :contractAddress "
            + "AND t.version = (SELECT MAX(t2.version) FROM TokenAllowlist t2 WHERE t2.chain = :chain)")
    Optional<TokenAllowlist> findCurrentVersionEntry(@Param("chain") String chain,
                                                      @Param("contractAddress") String contractAddress);
}
