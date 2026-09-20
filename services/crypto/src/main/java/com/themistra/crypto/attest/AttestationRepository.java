package com.themistra.crypto.attest;

import org.springframework.data.jpa.repository.JpaRepository;

interface AttestationRepository extends JpaRepository<Attestation, Long> {
}
