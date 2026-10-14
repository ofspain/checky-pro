package com.themistra.payment.invoice;

import com.themistra.payment.common.config.InvoiceProperties;
import com.themistra.payment.events.OutboxPublisher;
import com.themistra.payment.invoice.event.InvoiceCreatedPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * {@code create(...)} is this task's (T05/T06) own scope — {@code expire(...)} and the
 * {@code mark paid/under/over} methods {@code design.md} §6 lists for this class's eventual full
 * scope belong to later tasks (8, and whichever task applies R15) and do not exist yet.
 *
 * <p><b>T06:</b> {@link #computeExpiresAt()} is deliberately a plain, non-{@code @Transactional}
 * method — {@code InvoiceController} calls it, then {@link com.themistra.payment.common.CryptoClient}
 * (outside any transaction), then {@link #create}, so {@code expiresAt} is computed exactly once
 * and is identical in both the watch-registration call and the persisted row (Phase 3/4
 * disposition — the TIB's original "compute inside the transaction" design would have required
 * two separate {@link Clock} reads, risking a few-millisecond skew between the two).</p>
 */
@Service
public class InvoiceService {

    private static final Set<String> KNOWN_CHAINS = Set.of("ETHEREUM", "TRON");
    private static final int MAX_AMOUNT_DIGITS = 78;

    private final InvoiceRepository repository;
    private final InvoiceProperties invoiceProperties;
    private final Clock clock;
    private final OutboxPublisher outboxPublisher;

    public InvoiceService(InvoiceRepository repository, InvoiceProperties invoiceProperties, Clock clock,
                           OutboxPublisher outboxPublisher) {
        this.repository = repository;
        this.invoiceProperties = invoiceProperties;
        this.clock = clock;
        this.outboxPublisher = outboxPublisher;
    }

    public Instant computeExpiresAt() {
        return clock.instant().plus(Duration.ofMinutes(invoiceProperties.defaultTtlMinutes()));
    }

    @Transactional
    public Invoice create(UUID invoiceUuid, UUID merchantAccountUuid, String chain, String tokenContractAddress,
                           short tokenDecimals, BigDecimal amount, String payToAddress, String reference,
                           Instant expiresAt, UUID watchId) {
        Objects.requireNonNull(invoiceUuid, "invoiceUuid must not be null");
        Objects.requireNonNull(merchantAccountUuid, "merchantAccountUuid must not be null");
        Objects.requireNonNull(payToAddress, "payToAddress must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        validateChainAndTokenAddress(chain, tokenContractAddress);
        validateAmount(amount);

        Instant now = clock.instant();
        Invoice invoice = Invoice.create(invoiceUuid, merchantAccountUuid, chain, tokenContractAddress,
                tokenDecimals, amount, payToAddress, reference, expiresAt, watchId, now);
        Invoice saved = repository.save(invoice);

        InvoiceCreatedPayload payload = new InvoiceCreatedPayload(invoiceUuid, merchantAccountUuid, chain,
                tokenContractAddress, tokenDecimals, amount.toPlainString(), payToAddress, reference, expiresAt);
        outboxPublisher.publish("invoice", invoiceUuid.toString(), "invoice.created", payload);

        return saved;
    }

    /** R28 (AC1): all of the caller's own invoices, newest first. */
    @Transactional(readOnly = true)
    public List<Invoice> listForMerchant(UUID merchantAccountUuid) {
        Objects.requireNonNull(merchantAccountUuid, "merchantAccountUuid must not be null");
        return repository.findByMerchantAccountUuidOrderByCreatedAtDesc(merchantAccountUuid);
    }

    /** R28 (AC2): the same {@link InvoiceNotFoundException} for "doesn't exist" and "exists but
     * isn't the caller's" - no enumeration oracle (mirrors {@code auth-service}'s own
     * {@code SessionNotFoundException} convention). */
    @Transactional(readOnly = true)
    public Invoice getForMerchant(UUID merchantAccountUuid, UUID invoiceUuid) {
        Objects.requireNonNull(merchantAccountUuid, "merchantAccountUuid must not be null");
        Objects.requireNonNull(invoiceUuid, "invoiceUuid must not be null");
        return repository.findByInvoiceUuidAndMerchantAccountUuid(invoiceUuid, merchantAccountUuid)
                .orElseThrow(InvoiceNotFoundException::new);
    }

    /** R3 (AC1): {@code chain} must be a recognized value; {@code tokenContractAddress} must be
     * present. No address-format/checksum validation - no precedent exists anywhere in the
     * codebase yet ({@code crypto-service}'s own {@code AddressValidator} is explicitly future-
     * scoped, unbuilt - Phase 1 finding 3). */
    private static void validateChainAndTokenAddress(String chain, String tokenContractAddress) {
        if (chain == null || !KNOWN_CHAINS.contains(chain)) {
            throw new InvalidInvoiceRequestException("Unrecognized chain: " + chain);
        }
        if (tokenContractAddress == null || tokenContractAddress.isBlank()) {
            throw new InvalidInvoiceRequestException("tokenContractAddress must not be blank");
        }
    }

    /** R4 (AC2): null, non-positive, non-integral (fractional base units), or exceeding the
     * {@code NUMERIC(78,0)} column's own 78-digit capacity (Phase 3 finding 4) - all four are
     * ways an amount is "not representable as an exact NUMERIC" per R4's own wording. */
    private static void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidInvoiceRequestException("amount must not be null");
        }
        if (amount.signum() <= 0) {
            throw new InvalidInvoiceRequestException("amount must be positive");
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new InvalidInvoiceRequestException(
                    "amount must be a whole number of base units (no fractional component)");
        }
        // Safe: the scale check above already confirmed no fractional component exists.
        String digits = amount.toBigIntegerExact().abs().toString();
        if (digits.length() > MAX_AMOUNT_DIGITS) {
            throw new InvalidInvoiceRequestException(
                    "amount exceeds the maximum representable precision (" + MAX_AMOUNT_DIGITS + " digits)");
        }
    }
}
