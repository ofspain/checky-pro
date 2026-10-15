package com.themistra.payment.receipt;

import com.themistra.payment.common.AttestationRefusedException;
import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.common.CryptoServiceUnavailableException;
import com.themistra.payment.common.crypto.AttestationResult;
import com.themistra.payment.invoice.InvoiceReceiptView;
import com.themistra.payment.invoice.InvoiceService;
import com.themistra.payment.invoice.InvoiceState;
import com.themistra.payment.payment.PaymentReceiptView;
import com.themistra.payment.payment.PaymentService;
import com.themistra.payment.payment.PaymentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * T17 (L2, R17/R18/R19/R20). Guards that a payment is {@code FINALIZED} and its invoice is
 * exactly {@code PAID} (R17's own "satisfies its invoice," resolved directly by the product
 * owner: a receipt is Themistra's attestation that the payment correctly satisfied the invoice
 * - an {@code UNDERPAID}/{@code OVERPAID} payment is finalized on-chain and keeps its own
 * discrepancy record, R15, but is never attested), computes the canonical digest (T16), and
 * calls {@code CryptoClient.attest} (T15) - never inside an open transaction (L2's own "all
 * durably" language, mirrors {@code ChainEventConsumer.onChainTxReorged}'s own identical
 * {@code TransactionTemplate} split for the same class of problem, T12).
 *
 * <p>A {@code SIGNED} result persists nothing here - no {@code Receipt} row can exist yet
 * ({@code receipts.s3_bucket}/{@code s3_key} are {@code NOT NULL}; T18 hasn't written one yet)
 * and the payment does not reach {@code ATTESTED} here either (T19's own job, once the ledger
 * append also exists). The signed data is returned to the caller via {@link
 * ReceiptIssueResult.Signed}, not discarded.</p>
 *
 * <p>A {@code BLOCKED} result moves both the payment and invoice to {@code HELD}, atomically as
 * a pair (Phase 3, Kimi Finding 2) - a concurrent reorg could otherwise leave exactly one of the
 * two updated, which {@link IllegalStateException} here guards against by rolling both back and
 * reporting {@link ReceiptIssueResult.Failed} instead of a silently inconsistent pair.</p>
 *
 * <p><b>Disclosed gap, not solved here:</b> a transport failure ({@link
 * CryptoServiceUnavailableException}) or a refused attest ({@link AttestationRefusedException},
 * 409 - distinct from a {@code BLOCKED} outcome, Phase 3 Kimi Finding 6) is logged and returned
 * as {@link ReceiptIssueResult.Failed}, never retried from this code path. Unlike a failed watch
 * registration (which rides the invoice's own natural expiry), a {@code FINALIZED} payment that
 * fails to attest has no analogous natural recovery - it simply never reaches {@code ATTESTED}
 * until some future task adds a retry mechanism. No task in the current breakdown (15-21) adds
 * one; inventing one here would be scope creep beyond what this task's own text asks for.</p>
 */
@Service
public class ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

    private final PaymentService paymentService;
    private final InvoiceService invoiceService;
    private final CryptoClient cryptoClient;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ReceiptService(PaymentService paymentService, InvoiceService invoiceService,
                           CryptoClient cryptoClient, PlatformTransactionManager transactionManager, Clock clock) {
        this.paymentService = paymentService;
        this.invoiceService = invoiceService;
        this.cryptoClient = cryptoClient;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ReceiptIssueResult issueIfEligible(Long paymentId, String triggerEventKey) {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(triggerEventKey, "triggerEventKey must not be null");

        // Phase 3, Kimi Finding 3: re-read fresh - the caller's own triggering transaction has
        // already committed, so its outcome is stale by the time this runs.
        Optional<PaymentReceiptView> maybePayment = paymentService.findReceiptPrimitives(paymentId);
        if (maybePayment.isEmpty() || maybePayment.get().state() != PaymentState.FINALIZED) {
            return new ReceiptIssueResult.NotEligible();
        }
        PaymentReceiptView payment = maybePayment.get();

        Optional<InvoiceReceiptView> maybeInvoice = invoiceService.findReceiptPrimitives(payment.invoiceId());
        if (maybeInvoice.isEmpty() || maybeInvoice.get().state() != InvoiceState.PAID) {
            return new ReceiptIssueResult.NotEligible();
        }
        InvoiceReceiptView invoice = maybeInvoice.get();

        UUID receiptUuid = UUID.randomUUID();
        ReceiptPayload payload = buildPayload(receiptUuid, payment, invoice);
        String digest = ReceiptDigest.digest(payload);

        AttestationResult result;
        try {
            result = cryptoClient.attest(digest, payment.chain(), payment.txHash());
        } catch (AttestationRefusedException e) {
            log.warn("Attestation refused for payment {}: {}", paymentId, e.getMessage());
            return new ReceiptIssueResult.Failed("refused: " + e.getMessage());
        } catch (CryptoServiceUnavailableException e) {
            log.error("Crypto Service unavailable while attesting payment {}", paymentId, e);
            return new ReceiptIssueResult.Failed("unavailable: " + e.getMessage());
        }

        if (result instanceof AttestationResult.Signed signed) {
            return new ReceiptIssueResult.Signed(receiptUuid, signed);
        }
        AttestationResult.Blocked blocked = (AttestationResult.Blocked) result;
        return handleBlocked(payment.id(), payment.invoiceId(), blocked.reason(), triggerEventKey);
    }

    /** Phase 3, Kimi Finding 2: the two {@code HELD} updates are all-or-nothing. If exactly one
     * succeeds, the mismatch is a data-inconsistency bug (most plausibly a concurrent reorg
     * moving the payment out of {@code FINALIZED} in the narrow window between the eligibility
     * check above and this transaction), not a business outcome - thrown and rolled back here,
     * never propagated to {@code ChainEventConsumer} (so the Kafka offset for the already-
     * committed first transaction is never blocked). */
    private ReceiptIssueResult handleBlocked(Long paymentId, Long invoiceId, String reason, String triggerEventKey) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Instant now = clock.instant();
                boolean paymentHeld = paymentService.markHeldIfFinalized(paymentId, reason, triggerEventKey, now);
                boolean invoiceHeld = invoiceService.markHeldIfPaid(invoiceId, now);
                if (paymentHeld != invoiceHeld) {
                    throw new IllegalStateException("Split HELD transition for paymentId=" + paymentId
                            + " invoiceId=" + invoiceId + ": paymentHeld=" + paymentHeld + " invoiceHeld=" + invoiceHeld);
                }
                if (!paymentHeld) {
                    log.warn("Payment {} / invoice {} were already resolved elsewhere when the BLOCKED "
                            + "HELD transition ran - treating as a no-op, not a failure", paymentId, invoiceId);
                }
            });
            return new ReceiptIssueResult.Blocked(reason);
        } catch (IllegalStateException e) {
            log.error("HELD transition failed for payment {}", paymentId, e);
            return new ReceiptIssueResult.Failed("held-transition-split: " + e.getMessage());
        }
    }

    private static ReceiptPayload buildPayload(UUID receiptUuid, PaymentReceiptView payment, InvoiceReceiptView invoice) {
        return new ReceiptPayload(receiptUuid, invoice.invoiceUuid(), payment.paymentUuid(), invoice.merchantAccountUuid(),
                payment.chain(), payment.txHash(), payment.tokenContractAddress(), invoice.tokenDecimals(),
                payment.amount(), invoice.payToAddress(), payment.fromAddress(), payment.finalizedAt());
    }
}
