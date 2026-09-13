package com.themistra.crypto.attest;

import com.themistra.crypto.observation.FactType;
import com.themistra.crypto.quorum.QuorumDecisionService;
import com.themistra.crypto.screening.ScreeningClient;
import com.themistra.crypto.screening.ScreeningOutcome;
import com.themistra.crypto.watch.ChainCursor;
import com.themistra.crypto.watch.WatchService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Gates {@link KmsSigner#sign} on quorum + finality + screening (L10, L12, R20, R21, R23) - the
 * orchestration `design.md`'s own package map names {@link SignatureResult}'s only legitimate consumer.
 *
 * <p><b>Gate order is a Locked Decision (Phase 4, Finding #1/L10).</b> Screening is never attempted
 * until every required fact is {@code AGREED} - the vendor is never called for an unproven transaction.
 * A request that fails both gates always surfaces as {@code 409}, never {@code 200 BLOCKED}.</p>
 *
 * <p><b>{@code CONFIRMATIONS} is deliberately excluded</b> from {@link #REQUIRED_FACTS} - proposed at
 * Phase 2, unchallenged at Phase 3: it is a progress count superseded by {@code FINALITY} once reached,
 * not itself a payment-correctness fact the way EXISTENCE/AMOUNT/TOKEN are.</p>
 *
 * <p><b>The counterparty is {@code fromAddress}, never {@code toAddress}</b> (L-T21a) - {@code toAddress}
 * is the platform's own already-known, already-approved invoice/watch address; {@code fromAddress} is
 * the external, unknown paying party, the actual AML counterparty for this launch's inbound-payment-only
 * flow. More than one {@link ChainCursor} can match the same {@code (chain, txHash)} (no unique
 * constraint exists - verified against {@code V1__chain_baseline.sql}), so every distinct, non-null
 * {@code fromAddress} among them is screened; a single {@code BLOCKED} blocks the whole request
 * (fail-closed, conservative, Phase 4 Finding #3).</p>
 *
 * <p><b>A KMS/infrastructure failure is a deliberately different signal from a gate failure</b>
 * (L-T21b). {@link KmsSigner#sign}'s exceptions propagate uncaught here too, surfacing as {@code 500} -
 * never converted into {@link AttestationRefusedException}, and no {@link Attestation} row is persisted
 * for that attempt (none of {@code SIGNED}/{@code BLOCKED}/{@code REFUSED} accurately describes an
 * incomplete request).</p>
 *
 * <p>No outer {@code @Transactional}: each {@link AttestationRepository#save} call is already its own
 * transaction (Spring Data), and the screening/KMS network calls must never hold a database connection
 * open across them (T19/T20 precedent).</p>
 */
@Service
public class AttestationService {

    private static final List<FactType> REQUIRED_FACTS =
            List.of(FactType.EXISTENCE, FactType.AMOUNT, FactType.TOKEN, FactType.FINALITY);

    private final QuorumDecisionService quorumDecisionService;
    private final WatchService watchService;
    private final ScreeningClient screeningClient;
    private final KmsSigner kmsSigner;
    private final AttestationRepository attestationRepository;
    private final Clock clock;

    public AttestationService(QuorumDecisionService quorumDecisionService, WatchService watchService,
                               ScreeningClient screeningClient, KmsSigner kmsSigner,
                               AttestationRepository attestationRepository, Clock clock) {
        this.quorumDecisionService = quorumDecisionService;
        this.watchService = watchService;
        this.screeningClient = screeningClient;
        this.kmsSigner = kmsSigner;
        this.attestationRepository = attestationRepository;
        this.clock = clock;
    }

    public AttestResponse attest(AttestRequest request) {
        String chain = request.chain();
        String txHash = request.txHash();

        requireAllFactsAgreed(chain, txHash, request.receiptDigestSha256());

        List<ChainCursor> cursors = watchService.findChainCursors(chain, txHash);
        Set<String> fromAddresses = distinctFromAddresses(cursors);
        if (fromAddresses.isEmpty()) {
            persist(chain, txHash, request.receiptDigestSha256(), AttestOutcome.REFUSED, null, null);
            throw new AttestationRefusedException(chain, txHash);
        }

        for (String fromAddress : fromAddresses) {
            ScreeningOutcome outcome;
            try {
                outcome = screeningClient.screen(chain, fromAddress, txHash);
            } catch (RuntimeException e) {
                persist(chain, txHash, request.receiptDigestSha256(), AttestOutcome.REFUSED, null, null);
                throw new AttestationRefusedException(chain, txHash);
            }
            if (outcome == ScreeningOutcome.BLOCKED) {
                persist(chain, txHash, request.receiptDigestSha256(), AttestOutcome.BLOCKED, null, null);
                return AttestResponse.blocked("counterparty address is sanctioned");
            }
            if (outcome == ScreeningOutcome.ERROR) {
                persist(chain, txHash, request.receiptDigestSha256(), AttestOutcome.REFUSED, null, null);
                throw new AttestationRefusedException(chain, txHash);
            }
        }

        byte[] digest = HexFormat.of().parseHex(request.receiptDigestSha256());
        SignatureResult result = kmsSigner.sign(digest);

        persist(chain, txHash, request.receiptDigestSha256(), AttestOutcome.SIGNED, result.kmsKeyId(),
                result.signedAt());
        return AttestResponse.signed(result.signatureBase64(), result.kmsKeyId(), result.signedAt());
    }

    private void requireAllFactsAgreed(String chain, String txHash, String receiptDigest) {
        for (FactType factType : REQUIRED_FACTS) {
            if (!quorumDecisionService.isAgreed(chain, txHash, factType)) {
                persist(chain, txHash, receiptDigest, AttestOutcome.REFUSED, null, null);
                throw new AttestationRefusedException(chain, txHash);
            }
        }
    }

    private Set<String> distinctFromAddresses(List<ChainCursor> cursors) {
        Set<String> fromAddresses = new LinkedHashSet<>();
        for (ChainCursor cursor : cursors) {
            if (cursor.fromAddress() != null) {
                fromAddresses.add(cursor.fromAddress());
            }
        }
        return fromAddresses;
    }

    private void persist(String chain, String txHash, String receiptDigest, AttestOutcome outcome,
                          String kmsKeyId, Instant signedAt) {
        attestationRepository.save(Attestation.create(chain, txHash, receiptDigest, outcome, kmsKeyId,
                signedAt, clock.instant()));
    }
}
