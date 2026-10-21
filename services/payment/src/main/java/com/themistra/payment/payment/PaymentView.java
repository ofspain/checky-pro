package com.themistra.payment.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment read (frontend Phase 1b, 2026-10-21): what other modules may see of a {@link Payment}
 * (L12). {@code amount} is token base units and is null until finality reports it.
 * {@code amountDiscrepancy} is null, {@code UNDERPAID} or {@code OVERPAID}.
 */
public record PaymentView(Long id, UUID paymentUuid, String chain, String txHash, PaymentState state,
                          int confirmations, BigDecimal amount, String tokenContractAddress, String fromAddress,
                          String amountDiscrepancy, boolean addressPoisoningFlag, Instant seenAt,
                          Instant confirmedAt, Instant finalizedAt, Instant reorgedAt, Instant attestedAt) {

    static PaymentView from(Payment payment) {
        return new PaymentView(payment.getId(), payment.getPaymentUuid(), payment.getChain(), payment.getTxHash(),
                payment.getState(), payment.getConfirmations(), payment.getAmount(),
                payment.getTokenContractAddress(), payment.getFromAddress(), payment.getAmountDiscrepancy(),
                payment.isAddressPoisoningFlag(), payment.getSeenAt(), payment.getConfirmedAt(),
                payment.getFinalizedAt(), payment.getReorgedAt(), payment.getAttestedAt());
    }
}
