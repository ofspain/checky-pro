package com.themistra.payment.receipt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * T16 (`design.md` O4). The exact 12-field receipt payload that gets canonicalized and signed
 * (R19). Deliberately a plain record of primitive/value types — never an {@code Invoice} or
 * {@code Payment} reference — since {@code receipt} is a different feature module from {@code
 * invoice}/{@code payment} and both are {@code @Entity} classes {@code ArchitectureTest}'s own
 * {@code shouldPreventCrossModuleEntityImports} rule (L1/L12) already bans importing across that
 * boundary. The caller (T17's {@code ReceiptService}) reads the needed fields off those entities
 * via their own getters and constructs a {@code ReceiptPayload} from the primitives — the same
 * "read primitives across a module boundary, never pass the entity" pattern already established
 * by {@code PaymentService.findInvoiceIdByChainAndTxHash} (T13).
 *
 * <p>{@code receiptUuid} has no source entity to read from at all — no {@code Receipt} entity
 * exists yet (T17's own job) — so it is always caller-generated, mirroring {@code
 * Invoice.create}/{@code Payment.createSeen}'s own {@code UUID.randomUUID()}-at-creation
 * pattern.</p>
 *
 * <p>Every field except {@link #fromAddress} is required (Phase 3/4, Kimi Finding 5) — a
 * malformed {@code ReceiptPayload} fails at construction time, not downstream inside a digest
 * that would otherwise look valid. {@code fromAddress} is the one field O4 itself declares
 * nullable ({@code "<address|null>"}).</p>
 */
public record ReceiptPayload(
        UUID receiptUuid,
        UUID invoiceUuid,
        UUID paymentUuid,
        UUID merchantAccountUuid,
        String chain,
        String txHash,
        String tokenContractAddress,
        short tokenDecimals,
        BigDecimal amount,
        String payToAddress,
        String fromAddress,
        Instant finalizedAt) {

    public ReceiptPayload {
        Objects.requireNonNull(receiptUuid, "receiptUuid must not be null");
        Objects.requireNonNull(invoiceUuid, "invoiceUuid must not be null");
        Objects.requireNonNull(paymentUuid, "paymentUuid must not be null");
        Objects.requireNonNull(merchantAccountUuid, "merchantAccountUuid must not be null");
        Objects.requireNonNull(chain, "chain must not be null");
        Objects.requireNonNull(txHash, "txHash must not be null");
        Objects.requireNonNull(tokenContractAddress, "tokenContractAddress must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(payToAddress, "payToAddress must not be null");
        Objects.requireNonNull(finalizedAt, "finalizedAt must not be null");
    }
}
