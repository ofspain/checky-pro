package com.themistra.payment.chainverify;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** {@code payments.chain_verifications} — insert and read only; the role holds no UPDATE or DELETE. */
public interface ChainVerificationRepository extends JpaRepository<ChainVerification, Long> {

    /** One recorded answer by its public id — what a shared receipt link resolves to. */
    Optional<ChainVerification> findByVerificationUuid(UUID verificationUuid);
}
