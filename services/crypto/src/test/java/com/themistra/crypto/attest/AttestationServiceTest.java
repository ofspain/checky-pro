package com.themistra.crypto.attest;

import com.themistra.crypto.observation.FactType;
import com.themistra.crypto.quorum.QuorumDecisionService;
import com.themistra.crypto.screening.ScreeningClient;
import com.themistra.crypto.screening.ScreeningOutcome;
import com.themistra.crypto.watch.ChainCursor;
import com.themistra.crypto.watch.WatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The named tests from package.md §8 (`shouldReturnKmsSignatureFromAttestForValidDigest`,
 * `shouldReturnBlockedFromAttestOnSanctionedCounterparty`, `shouldRejectAttestWhenQuorumOrFinalityNotMet`)
 * plus every AC from the frozen brief (Phase 4). */
@ExtendWith(MockitoExtension.class)
class AttestationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
    private static final String CHAIN = "ETHEREUM";
    private static final String TX_HASH = "0xtx";
    private static final String DIGEST_HEX = "d".repeat(64);
    private static final byte[] DIGEST_BYTES = hexDecode(DIGEST_HEX);
    private static final String FROM_ADDRESS = "0xfrom";

    @Mock
    private QuorumDecisionService quorumDecisionService;
    @Mock
    private WatchService watchService;
    @Mock
    private ScreeningClient screeningClient;
    @Mock
    private KmsSigner kmsSigner;
    @Mock
    private AttestationRepository attestationRepository;

    private AttestationService service;

    @BeforeEach
    void setUp() {
        service = new AttestationService(quorumDecisionService, watchService, screeningClient, kmsSigner,
                attestationRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AttestRequest request() {
        return new AttestRequest(DIGEST_HEX, CHAIN, TX_HASH);
    }

    private void stubAllFactsAgreed() {
        when(quorumDecisionService.isAgreed(eq(CHAIN), eq(TX_HASH), any(FactType.class))).thenReturn(true);
    }

    private ChainCursor cursorWithFromAddress(String fromAddress) {
        ChainCursor cursor = ChainCursor.placeholder(CHAIN, UUID.randomUUID(), NOW);
        cursor.recordSeenTransaction(TX_HASH, BigDecimal.TEN, fromAddress, "0xto", NOW);
        return cursor;
    }

    // --- shouldReturnKmsSignatureFromAttestForValidDigest (AC1) ---

    @Test
    void shouldReturnKmsSignatureFromAttestForValidDigest() {
        stubAllFactsAgreed();
        when(watchService.findChainCursors(CHAIN, TX_HASH)).thenReturn(List.of(cursorWithFromAddress(FROM_ADDRESS)));
        when(screeningClient.screen(CHAIN, FROM_ADDRESS, TX_HASH)).thenReturn(ScreeningOutcome.CLEARED);
        SignatureResult signatureResult = new SignatureResult("c2ln", "arn:aws:kms:key/abc", NOW);
        when(kmsSigner.sign(any())).thenReturn(signatureResult);

        AttestResponse response = service.attest(request());

        assertThat(response.outcome()).isEqualTo("SIGNED");
        assertThat(response.signature()).isEqualTo("c2ln");
        assertThat(response.kmsKeyId()).isEqualTo("arn:aws:kms:key/abc");
        assertThat(response.signedAt()).isEqualTo(NOW);
        assertThat(response.reason()).isNull();

        ArgumentCaptor<byte[]> digestCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(kmsSigner).sign(digestCaptor.capture());
        assertThat(digestCaptor.getValue()).isEqualTo(DIGEST_BYTES);

        ArgumentCaptor<Attestation> attestationCaptor = ArgumentCaptor.forClass(Attestation.class);
        verify(attestationRepository).save(attestationCaptor.capture());
        Attestation saved = attestationCaptor.getValue();
        assertThat(saved.outcome()).isEqualTo(AttestOutcome.SIGNED);
        assertThat(saved.kmsKeyId()).isEqualTo("arn:aws:kms:key/abc");
        assertThat(saved.signedAt()).isEqualTo(NOW);
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.chain()).isEqualTo(CHAIN);
        assertThat(saved.txHash()).isEqualTo(TX_HASH);
        assertThat(saved.receiptDigest()).isEqualTo(DIGEST_HEX);
    }

    // --- shouldReturnBlockedFromAttestOnSanctionedCounterparty (AC2) ---

    @Test
    void shouldReturnBlockedFromAttestOnSanctionedCounterparty() {
        stubAllFactsAgreed();
        when(watchService.findChainCursors(CHAIN, TX_HASH)).thenReturn(List.of(cursorWithFromAddress(FROM_ADDRESS)));
        when(screeningClient.screen(CHAIN, FROM_ADDRESS, TX_HASH)).thenReturn(ScreeningOutcome.BLOCKED);

        AttestResponse response = service.attest(request());

        assertThat(response.outcome()).isEqualTo("BLOCKED");
        assertThat(response.reason()).isNotBlank();
        assertThat(response.signature()).isNull();
        verifyNoInteractions(kmsSigner);

        ArgumentCaptor<Attestation> attestationCaptor = ArgumentCaptor.forClass(Attestation.class);
        verify(attestationRepository).save(attestationCaptor.capture());
        assertThat(attestationCaptor.getValue().outcome()).isEqualTo(AttestOutcome.BLOCKED);
    }

    @Test
    void blockedIsOnlyReachableAfterQuorumAndFinalityPass() {
        // Phase 4 (Kimi Phase 3 Finding #1): screening must never even be attempted if quorum/finality
        // haven't passed - the gate order itself proves this, not just the outcome.
        when(quorumDecisionService.isAgreed(eq(CHAIN), eq(TX_HASH), any(FactType.class))).thenReturn(false);

        assertThatThrownBy(() -> service.attest(request())).isInstanceOf(AttestationRefusedException.class);

        verifyNoInteractions(watchService, screeningClient, kmsSigner);
    }

    // --- shouldRejectAttestWhenQuorumOrFinalityNotMet (AC3) ---

    @ParameterizedTest
    @EnumSource(value = FactType.class, names = {"EXISTENCE", "AMOUNT", "TOKEN", "FINALITY"})
    void shouldRejectAttestWhenQuorumOrFinalityNotMet(FactType missingFact) {
        when(quorumDecisionService.isAgreed(eq(CHAIN), eq(TX_HASH), any(FactType.class)))
                .thenAnswer(invocation -> invocation.getArgument(2) != missingFact);

        assertThatThrownBy(() -> service.attest(request())).isInstanceOf(AttestationRefusedException.class);

        verifyNoInteractions(watchService, screeningClient, kmsSigner);
        ArgumentCaptor<Attestation> attestationCaptor = ArgumentCaptor.forClass(Attestation.class);
        verify(attestationRepository).save(attestationCaptor.capture());
        assertThat(attestationCaptor.getValue().outcome()).isEqualTo(AttestOutcome.REFUSED);
    }

    @Test
    void confirmationsIsNotAmongTheRequiredFacts() {
        // Deliberate exclusion (Phase 2, unchallenged at Phase 3): only EXISTENCE/AMOUNT/TOKEN/FINALITY
        // are checked - CONFIRMATIONS must never be queried at all.
        stubAllFactsAgreed();
        when(watchService.findChainCursors(CHAIN, TX_HASH)).thenReturn(List.of(cursorWithFromAddress(FROM_ADDRESS)));
        when(screeningClient.screen(any(), any(), any())).thenReturn(ScreeningOutcome.CLEARED);
        when(kmsSigner.sign(any())).thenReturn(new SignatureResult("c2ln", "key", NOW));

        service.attest(request());

        verify(quorumDecisionService, never()).isAgreed(any(), any(), eq(FactType.CONFIRMATIONS));
    }

    // --- Screening fail-closed (AC4) ---

    @Test
    void screeningErrorRefusesRatherThanBlocking() {
        stubAllFactsAgreed();
        when(watchService.findChainCursors(CHAIN, TX_HASH)).thenReturn(List.of(cursorWithFromAddress(FROM_ADDRESS)));
        when(screeningClient.screen(CHAIN, FROM_ADDRESS, TX_HASH)).thenReturn(ScreeningOutcome.ERROR);

        assertThatThrownBy(() -> service.attest(request())).isInstanceOf(AttestationRefusedException.class);

        verifyNoInteractions(kmsSigner);
        ArgumentCaptor<Attestation> attestationCaptor = ArgumentCaptor.forClass(Attestation.class);
        verify(attestationRepository).save(attestationCaptor.capture());
        assertThat(attestationCaptor.getValue().outcome()).isEqualTo(AttestOutcome.REFUSED);
    }

    @Test
    void aThrownScreeningExceptionRefusesRatherThanPropagating() {
        stubAllFactsAgreed();
        when(watchService.findChainCursors(CHAIN, TX_HASH)).thenReturn(List.of(cursorWithFromAddress(FROM_ADDRESS)));
        when(screeningClient.screen(CHAIN, FROM_ADDRESS, TX_HASH))
                .thenThrow(new RuntimeException("screening vendor unreachable"));

        assertThatThrownBy(() -> service.attest(request())).isInstanceOf(AttestationRefusedException.class);

        verifyNoInteractions(kmsSigner);
    }

    // --- Multi-cursor / null-fromAddress handling (AC8) ---

    @Test
    void multipleCursorsWithDistinctFromAddressesAreAllScreenedAndAnyBlockedHitBlocksTheWholeRequest() {
        stubAllFactsAgreed();
        ChainCursor cursorA = cursorWithFromAddress("0xfrom-a");
        ChainCursor cursorB = cursorWithFromAddress("0xfrom-b");
        when(watchService.findChainCursors(CHAIN, TX_HASH)).thenReturn(List.of(cursorA, cursorB));
        when(screeningClient.screen(CHAIN, "0xfrom-a", TX_HASH)).thenReturn(ScreeningOutcome.CLEARED);
        when(screeningClient.screen(CHAIN, "0xfrom-b", TX_HASH)).thenReturn(ScreeningOutcome.BLOCKED);

        AttestResponse response = service.attest(request());

        assertThat(response.outcome()).isEqualTo("BLOCKED");
        verifyNoInteractions(kmsSigner);
    }

    @Test
    void emptyCursorListRefuses() {
        stubAllFactsAgreed();
        when(watchService.findChainCursors(CHAIN, TX_HASH)).thenReturn(List.of());

        assertThatThrownBy(() -> service.attest(request())).isInstanceOf(AttestationRefusedException.class);

        verifyNoInteractions(screeningClient, kmsSigner);
    }

    @Test
    void allCursorsWithNullFromAddressRefusesRatherThanNpe() {
        stubAllFactsAgreed();
        ChainCursor placeholderCursor = ChainCursor.placeholder(CHAIN, UUID.randomUUID(), NOW);
        when(watchService.findChainCursors(CHAIN, TX_HASH)).thenReturn(List.of(placeholderCursor));

        assertThatThrownBy(() -> service.attest(request())).isInstanceOf(AttestationRefusedException.class);

        verifyNoInteractions(screeningClient, kmsSigner);
    }

    // --- KMS failure (Finding #10/L-T21b) ---

    @Test
    void aKmsFailurePropagatesUncaughtAndPersistsNoAttestationRow() {
        stubAllFactsAgreed();
        when(watchService.findChainCursors(CHAIN, TX_HASH)).thenReturn(List.of(cursorWithFromAddress(FROM_ADDRESS)));
        when(screeningClient.screen(any(), any(), any())).thenReturn(ScreeningOutcome.CLEARED);
        RuntimeException kmsFailure = new RuntimeException("kms unavailable");
        when(kmsSigner.sign(any())).thenThrow(kmsFailure);

        assertThatThrownBy(() -> service.attest(request())).isSameAs(kmsFailure);

        verify(attestationRepository, never()).save(any());
    }

    // --- Exception message content (AC11) ---

    @Test
    void refusalMessageNeverNamesTheSpecificFailedFact() {
        when(quorumDecisionService.isAgreed(eq(CHAIN), eq(TX_HASH), any(FactType.class))).thenReturn(false);

        assertThatThrownBy(() -> service.attest(request()))
                .isInstanceOf(AttestationRefusedException.class)
                .hasMessage("Attestation preconditions not met for " + CHAIN + ":" + TX_HASH)
                .hasMessageNotContainingAny("EXISTENCE", "AMOUNT", "TOKEN", "FINALITY", "HELD", "AGREED");
    }

    private static byte[] hexDecode(String hex) {
        return java.util.HexFormat.of().parseHex(hex);
    }
}
