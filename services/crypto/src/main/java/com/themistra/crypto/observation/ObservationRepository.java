package com.themistra.crypto.observation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ObservationRepository extends JpaRepository<Observation, Long> {

    List<Observation> findByChainAndTxHashAndFactType(String chain, String txHash, FactType factType);
}
