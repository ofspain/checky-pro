package com.themistra.payment.invoice;

import com.themistra.payment.common.config.InvoiceProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * {@code create(...)} is the only method this task (T05) implements — {@code expire(...)} and
 * the {@code mark paid/under/over} methods {@code design.md} §6 lists for this class's eventual
 * full scope belong to later tasks (8, and whichever task applies R15) and do not exist yet.
 */
@Service
public class InvoiceService {

    private static final Set<String> KNOWN_CHAINS = Set.of("ETHEREUM", "TRON");
    private static final int MAX_AMOUNT_DIGITS = 78;

    private final InvoiceRepository repository;
    private final InvoiceProperties invoiceProperties;
    private final Clock clock;

    public InvoiceService(InvoiceRepository repository, InvoiceProperties invoiceProperties, Clock clock) {
        this.repository = repository;
        this.invoiceProperties = invoiceProperties;
        this.clock = clock;
    }

    @Transactional
    public Invoice create(UUID merchantAccountUuid, String chain, String tokenContractAddress,
                           short tokenDecimals, BigDecimal amount, String payToAddress, String reference) {
        Objects.requireNonNull(merchantAccountUuid, "merchantAccountUuid must not be null");
        Objects.requireNonNull(payToAddress, "payToAddress must not be null");
        validateChainAndTokenAddress(chain, tokenContractAddress);
        validateAmount(amount);

        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofMinutes(invoiceProperties.defaultTtlMinutes()));

        Invoice invoice = Invoice.create(UUID.randomUUID(), merchantAccountUuid, chain,
                tokenContractAddress, tokenDecimals, amount, payToAddress, reference, expiresAt, now);
        return repository.save(invoice);
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
