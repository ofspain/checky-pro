package com.themistra.payment.receipt;

import com.themistra.payment.common.AttestationRefusedException;
import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.common.CryptoServiceUnavailableException;
import com.themistra.payment.common.crypto.AttestationResult;
import com.themistra.payment.events.OutboxPublisher;
import com.themistra.payment.invoice.InvoiceReceiptView;
import com.themistra.payment.invoice.InvoiceService;
import com.themistra.payment.invoice.InvoiceState;
import com.themistra.payment.payment.PaymentReceiptView;
import com.themistra.payment.payment.PaymentService;
import com.themistra.payment.payment.PaymentState;
import com.themistra.payment.receipt.event.ReceiptIssuedPayload;
import com.themistra.payment.receipt.ledger.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * T17/T19 (L2, R17-R21, R23-R25). Guards that a payment is {@code FINALIZED} and its invoice is
 * exactly {@code PAID} (R17's own "satisfies its invoice," resolved directly by the product
 * owner: a receipt is Themistra's attestation that the payment correctly satisfied the invoice
 * - an {@code UNDERPAID}/{@code OVERPAID} payment is finalized on-chain and keeps its own
 * discrepancy record, R15, but is never attested), computes the canonical digest (T16), and
 * calls {@code CryptoClient.attest} (T15) - never inside an open transaction (L2's own "all
 * durably" language, mirrors {@code ChainEventConsumer.onChainTxReorged}'s own identical
 * {@code TransactionTemplate} split for the same class of problem, T12).
 *
 * <p><b>A {@code SIGNED} result (T19) is persisted in full, atomically</b>: {@code
 * ReceiptStore.store(...)} writes the signed JSON/PDF to S3 (T18, also outside any transaction -
 * an S3 call is an outbound I/O call, the same discipline as {@code attest()} itself), then one
 * single transaction inserts the {@code Receipt} row, appends the hash-chain ledger entry
 * ({@code receipt.ledger.LedgerService}), transitions the payment to {@code ATTESTED}, and
 * publishes {@code receipt.issued} - R24/R25's own explicit "same transaction" text. If the
 * {@code ATTESTED} transition itself finds the payment no longer {@code FINALIZED} (a concurrent
 * reorg, Phase 3/4 Kimi Finding 2), the whole transaction aborts and rolls back - no
 * {@code Receipt}/{@code LedgerEntry}/outbox row survives for a payment that never actually
 * reached {@code ATTESTED}.</p>
 *
 * <p><b>The S3-orphan window (Phase 3/4, Kimi Finding 6) - a sharper form of the "no retry"
 * gap below.</b> If the DB transaction above fails or is never reached after {@code
 * ReceiptStore.store(...)} already succeeded, the signed JSON/PDF objects remain durably in S3
 * WORM storage (L9 bans a delete path in application code - there is no cleanup mechanism here)
 * with no corresponding {@code Receipt}/{@code LedgerEntry}/{@code ATTESTED} payment. Unlike the
 * general "attest() failed, nothing was ever signed" gap, this window means a real KMS signature
 * was already consumed and a real S3 object already written for work that never actually
 * completed - operational reconciliation only, not solved in application code.</p>
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
 *
 * <p><b>The narrower crash-window case (Phase 9, Kimi Phase 8 confirming self-review Finding
 * 2).</b> If the process crashes after {@code ChainEventConsumer}'s own first transaction
 * commits but before (or during) {@code issueIfEligible} ever runs, a receipt is never even
 * attempted - not just attempted and failed. {@code dedupe()} already consumed the event's
 * idempotency key in that already-committed transaction, so Kafka redelivery cannot retry this
 * step either - the identical structural problem {@code onChainTxReorged}'s own Javadoc already
 * names explicitly for {@code registerReplacementWatch} (Phase 9 Finding 5, T12).</p>
 */
@Service
public class ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

    private final PaymentService paymentService;
    private final InvoiceService invoiceService;
    private final CryptoClient cryptoClient;
    private final ReceiptStore receiptStore;
    private final ReceiptRepository receiptRepository;
    private final LedgerService ledgerService;
    private final OutboxPublisher outboxPublisher;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ReceiptService(PaymentService paymentService, InvoiceService invoiceService,
                           CryptoClient cryptoClient, ReceiptStore receiptStore, ReceiptRepository receiptRepository,
                           LedgerService ledgerService, OutboxPublisher outboxPublisher,
                           PlatformTransactionManager transactionManager, Clock clock) {
        this.paymentService = paymentService;
        this.invoiceService = invoiceService;
        this.cryptoClient = cryptoClient;
        this.receiptStore = receiptStore;
        this.receiptRepository = receiptRepository;
        this.ledgerService = ledgerService;
        this.outboxPublisher = outboxPublisher;
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
            return persistSignedReceipt(payment, invoice, payload, digest, receiptUuid, signed, triggerEventKey);
        }
        AttestationResult.Blocked blocked = (AttestationResult.Blocked) result;
        return handleBlocked(payment.id(), payment.invoiceId(), blocked.reason(), triggerEventKey);
    }

    /** T19 (R21/R23/R24/R25, Phase 3/4 Kimi Findings 2/6/7). {@code receiptStore.store(...)}
     * runs first, outside any transaction (an S3 call). Everything else - the {@code Receipt}
     * insert, the ledger append, the {@code ATTESTED} transition, and the {@code receipt.issued}
     * publish - runs inside exactly one transaction, in that order (Finding 7: {@code
     * ledger_entries.receipt_id} references {@code receipts.id}, so the {@code Receipt} must
     * commit-order before the {@code LedgerEntry}). Any failure anywhere in this sequence - the
     * S3 write itself, or anything inside the transaction, including the abort-on-zero-rows
     * guard below (Finding 2) - converts to {@link ReceiptIssueResult.Failed} rather than
     * propagating or silently returning {@code Signed} for work that didn't actually commit. */
    private ReceiptIssueResult persistSignedReceipt(PaymentReceiptView payment, InvoiceReceiptView invoice,
            ReceiptPayload payload, String digest, UUID receiptUuid, AttestationResult.Signed signed,
            String triggerEventKey) {
        try {
            ReceiptStorageLocation location = receiptStore.store(payload, signed);
            return transactionTemplate.execute(status -> {
                Instant now = clock.instant();
                Receipt receipt = receiptRepository.save(Receipt.create(receiptUuid, payment.id(), digest,
                        signed.signature(), signed.kmsKeyId(), location.bucket(), location.s3KeyPrefix(),
                        signed.signedAt(), now));

                Map<String, Object> ledgerPayload = buildLedgerPayload(payload, signed, location);
                ledgerService.append(receipt.getId(), ledgerPayload);

                boolean attested = paymentService.markAttestedIfFinalized(payment.id(), triggerEventKey,
                        receiptUuid, now);
                if (!attested) {
                    throw new IllegalStateException("Payment " + payment.id()
                            + " was no longer FINALIZED when the ATTESTED transition ran");
                }

                outboxPublisher.publish("receipt", receiptUuid.toString(), "receipt.issued",
                        new ReceiptIssuedPayload(invoice.invoiceUuid(), payment.paymentUuid(), receiptUuid,
                                invoice.merchantAccountUuid(), payment.chain(), payment.txHash(),
                                payment.amount().toPlainString(), payment.tokenContractAddress(),
                                signed.kmsKeyId(), receiptUri(location), now));

                return new ReceiptIssueResult.Signed(receiptUuid, signed);
            });
        } catch (RuntimeException e) {
            log.error("Failed to persist receipt/ledger/ATTESTED transition for payment {}", payment.id(), e);
            return new ReceiptIssueResult.Failed("persist-failed: " + e.getMessage());
        }
    }

    /** The ledger entry's own JSONB payload (Phase 3/4, Kimi Finding 5): {@code ReceiptStore}'s
     * own 15-field canonical map (now {@code public static}, Finding 12) plus the already-
     * normalized {@code s3Bucket}/{@code s3KeyPrefix} - so the ledger entry is self-describing,
     * locatable without a join back to {@code receipts}. The JSON object is at
     * {@code s3KeyPrefix + ".json"}, the PDF at {@code s3KeyPrefix + ".pdf"} (documented here,
     * not re-derived elsewhere). */
    private static Map<String, Object> buildLedgerPayload(ReceiptPayload payload, AttestationResult.Signed signed,
            ReceiptStorageLocation location) {
        Map<String, Object> map = ReceiptStore.toCanonicalMap(payload, signed);
        map.put("s3Bucket", location.bucket());
        map.put("s3KeyPrefix", location.s3KeyPrefix());
        return map;
    }

    /** Points at the canonical JSON object - the machine-verifiable artifact (T18's own "JSON
     * first" framing) - never the PDF. */
    private static String receiptUri(ReceiptStorageLocation location) {
        return "s3://" + location.bucket() + "/" + location.s3KeyPrefix() + ".json";
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
        } catch (RuntimeException e) {
            // Phase 9 (Kimi Phase 8, confirming self-review Finding 1): widened from
            // IllegalStateException alone - a real JPA/DB failure from either HELD update would
            // otherwise propagate uncaught out of onChainTxFinalized, even though dedupe()
            // already committed in the first transaction, so a Kafka redelivery could not retry
            // this step anyway (same root cause as the disclosed "no retry" gap above).
            log.error("HELD transition failed for payment {}", paymentId, e);
            return new ReceiptIssueResult.Failed("held-transition-failed: " + e.getMessage());
        }
    }

    private static ReceiptPayload buildPayload(UUID receiptUuid, PaymentReceiptView payment, InvoiceReceiptView invoice) {
        return new ReceiptPayload(receiptUuid, invoice.invoiceUuid(), payment.paymentUuid(), invoice.merchantAccountUuid(),
                payment.chain(), payment.txHash(), payment.tokenContractAddress(), invoice.tokenDecimals(),
                payment.amount(), invoice.payToAddress(), payment.fromAddress(), payment.finalizedAt());
    }
}
