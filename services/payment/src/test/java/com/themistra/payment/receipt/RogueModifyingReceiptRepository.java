package com.themistra.payment.receipt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * T28 Phase 11 (Kimi Gap 4). A deliberate violation: a second {@link Receipt} repository that
 * declares a {@code @Modifying} query and a Spring Data derived delete ({@code removeBy…}).
 * {@code @NoRepositoryBean} keeps Spring Data from instantiating it. Never referenced by
 * production code.
 */
@NoRepositoryBean
interface RogueModifyingReceiptRepository extends JpaRepository<Receipt, Long> {

    @Modifying
    @Query("UPDATE Receipt r SET r.signature = :signature")
    int rewriteEverySignature(String signature);

    long removeByPaymentId(Long paymentId);
}
