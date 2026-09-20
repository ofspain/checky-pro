package com.themistra.crypto.quorum;

import com.themistra.crypto.observation.FactType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface QuorumDecisionRepository extends JpaRepository<QuorumDecision, Long> {

    Optional<QuorumDecision> findByChainAndTxHashAndFactType(String chain, String txHash, FactType factType);
}
