package com.themistra.payment.chainverify;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** {@code payments.chain_verifications} — insert and read only; the role holds no UPDATE or DELETE. */
public interface ChainVerificationRepository extends JpaRepository<ChainVerification, Long> {

    /** The most recent verifications, newest first — what the landing page lists under the form. */
    List<ChainVerification> findAllByOrderByRequestedAtDesc(Limit limit);
}
