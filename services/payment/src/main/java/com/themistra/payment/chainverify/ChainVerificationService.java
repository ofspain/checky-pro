package com.themistra.payment.chainverify;

import com.themistra.payment.chainverify.dto.VerifyResponse;
import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.common.crypto.TransactionLookupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Verify one transaction hash against the chain, and keep the answer.
 *
 * <p>This service contains <b>no verification logic of its own and must never grow any</b>. The
 * agreement is crypto-service's: three providers, 2-of-3 per fact, token allowlist, and a
 * {@code HELD} answer that carries no facts. Everything here is the two things around it — asking,
 * and writing down what came back.</p>
 *
 * <p><b>Why the write is not in the same transaction as the call.</b> The lookup is remote and can
 * take seconds; holding a database transaction open across it is how a connection pool dies under
 * the load a public page can attract. The call happens first, outside any transaction, and only the
 * insert is transactional — the same shape {@code InvoiceController} already uses for
 * {@code CryptoClient}, and for the same reason.</p>
 */
@Service
public class ChainVerificationService {

    private static final Logger log = LoggerFactory.getLogger(ChainVerificationService.class);

    /** All three providers agree it does not exist — a real answer, and crypto's own 404. */
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final int RECENT_LIMIT = 10;

    private final CryptoClient cryptoClient;
    private final ChainVerificationRepository repository;
    private final TokenDisplayProperties tokenDisplay;
    private final Clock clock;

    public ChainVerificationService(CryptoClient cryptoClient, ChainVerificationRepository repository,
            TokenDisplayProperties tokenDisplay, Clock clock) {
        this.cryptoClient = cryptoClient;
        this.repository = repository;
        this.tokenDisplay = tokenDisplay;
        this.clock = clock;
    }

    public VerifyResponse verify(String rawTxHash, String rawChain) {
        String chain = TokenDisplayProperties.normaliseChain(rawChain);
        // Hex is case-insensitive and every explorer prints it lowercase, so one spelling is stored.
        // The `0x` prefix is kept, not stripped: it is what the chain adapters and every provider
        // expect, and what `bin/chain pay` prints.
        String txHash = rawTxHash.strip().toLowerCase(Locale.ROOT);

        TransactionLookupResponse answer = cryptoClient.lookupTransaction(chain, txHash);
        Instant requestedAt = clock.instant();

        // `save` is transactional in its own right (SimpleJpaRepository). An earlier draft wrapped
        // it in a @Transactional method on this class and called it from here - self-invocation, so
        // the proxy is never involved and the annotation does nothing at all. One insert needs no
        // transaction of its own anyway.
        ChainVerification stored = answer == null
                ? repository.save(ChainVerification.record(UUID.randomUUID(), chain, txHash, NOT_FOUND,
                        null, null, null, null, null, null, requestedAt))
                : repository.save(ChainVerification.record(UUID.randomUUID(), chain, txHash, answer.outcome(),
                        answer.existence(), answer.confirmations(), answer.tokenContractAddress(),
                        answer.fromAddress(), answer.toAddress(), parseAmount(answer.amount()),
                        requestedAt));

        log.info("Verified {}:{} -> {}", chain, txHash, stored.getOutcome());
        return present(stored);
    }

    /**
     * One recorded answer by its public id, for a receipt link that can be shared and reopened.
     *
     * <p>It is deliberately readable by anyone holding the id: the id is a random UUID, it is the
     * only way in, and the row describes a public chain transaction that any block explorer will
     * show the same way. Nothing merchant-scoped is reachable through it.</p>
     */
    @Transactional(readOnly = true)
    public Optional<VerifyResponse> find(UUID verificationId) {
        return repository.findByVerificationUuid(verificationId).map(this::present);
    }

    @Transactional(readOnly = true)
    public List<VerifyResponse> recent() {
        return repository.findAllByOrderByRequestedAtDesc(Limit.of(RECENT_LIMIT))
                .stream().map(this::present).toList();
    }

    /**
     * Base units as a decimal string, never a JSON number (L7). A 78-digit base-unit amount does
     * not survive a double, and this is the boundary where that would happen silently.
     */
    private static BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException e) {
            // crypto-service agreed on something this service cannot read. Recording the row
            // without the amount is better than discarding the whole answer: the outcome and the
            // addresses are still true, and a null amount is already a shape the reader handles.
            log.warn("Crypto Service returned an unparseable amount '{}'; recording without it", amount);
            return null;
        }
    }

    private VerifyResponse present(ChainVerification verification) {
        return new VerifyResponse(
                verification.getVerificationUuid(),
                verification.getChain(),
                verification.getTxHash(),
                verification.getOutcome(),
                verification.getExistence(),
                verification.getConfirmations(),
                verification.getTokenContractAddress(),
                verification.getFromAddress(),
                verification.getToAddress(),
                verification.getAmount() == null ? null : verification.getAmount().toPlainString(),
                display(verification),
                verification.getRequestedAt());
    }

    /** "12.5 USDC", or null when the amount is absent or the token is not one we can name. */
    private String display(ChainVerification verification) {
        if (verification.getAmount() == null) {
            return null;
        }
        return tokenDisplay.find(verification.getChain(), verification.getTokenContractAddress())
                .map(token -> verification.getAmount()
                        .movePointLeft(token.decimals())
                        .stripTrailingZeros()
                        .toPlainString() + " " + token.symbol())
                .orElse(null);
    }
}
