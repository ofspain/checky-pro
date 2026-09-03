package com.themistra.crypto.quorum;

import com.themistra.crypto.observation.FactType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The named tests from package.md §8 (`shouldHoldFactAndAlertWhenProvidersDisagree`,
 * `shouldNeverAutoResolveDisagreementInPayersFavor`), AC2 (alert + persist on HELD, after-save
 * ordering per Phase 9), AC3 (never auto-resolve), AC7 (alerter iff HELD), AC8 (duplicate-provider
 * rejection), plus the Phase 9 pre-flight existing-decision check and null guards. A real {@link
 * QuorumEvaluator} is used (pure, cheap, no need to mock it) - only {@link QuorumDecisionRepository}
 * and {@link HeldFactAlerter} are Mockito mocks. Fixed {@link Clock} per agents.md. */
@ExtendWith(MockitoExtension.class)
class QuorumDecisionServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-03T12:00:00Z");

    @Mock
    private QuorumDecisionRepository repository;
    @Mock
    private HeldFactAlerter alerter;

    private final QuorumEvaluator evaluator = new QuorumEvaluator();
    private final Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private QuorumDecisionService service;

    @BeforeEach
    void setUp() {
        service = new QuorumDecisionService(evaluator, repository, alerter, fixedClock);
    }

    private void stubNoExistingDecision() {
        when(repository.findByChainAndTxHashAndFactType(any(), any(), any())).thenReturn(Optional.empty());
    }

    private void stubSuccessfulSave() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldHoldFactAndAlertWhenProvidersDisagree() {
        stubNoExistingDecision();
        stubSuccessfulSave();
        List<ProviderAnswer<String>> disagreeingAnswers = List.of(
                new ProviderAnswer<>("alchemy", "A"),
                new ProviderAnswer<>("quicknode", "B"),
                new ProviderAnswer<>("infura", "C"));

        QuorumDecision decision = service.evaluate("ETHEREUM", "0xabc", FactType.TOKEN, disagreeingAnswers);

        assertThat(decision.outcome()).isEqualTo(QuorumOutcome.HELD);
        verify(alerter).alert(eq("ETHEREUM"), eq("0xabc"), eq(FactType.TOKEN), eq(disagreeingAnswers));
        verify(repository).save(any());
    }

    @Test
    void shouldNeverAutoResolveDisagreementInPayersFavor() {
        // R3/L2: once persisted as HELD, no code path in this service resolves it automatically -
        // there is no update/resolve method to call, and the repository is never invoked with
        // anything but the single `save` of the freshly-created HELD decision.
        stubNoExistingDecision();
        stubSuccessfulSave();
        List<ProviderAnswer<String>> disagreeingAnswers = List.of(
                new ProviderAnswer<>("alchemy", "A"),
                new ProviderAnswer<>("quicknode", "B"),
                new ProviderAnswer<>("infura", "C"));

        service.evaluate("ETHEREUM", "0xabc", FactType.TOKEN, disagreeingAnswers);

        verify(repository, org.mockito.Mockito.times(1)).save(any());
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(any());
    }

    @Test
    void alerterIsInvokedOnlyOnHeldNeverOnAgreed() {
        stubNoExistingDecision();
        stubSuccessfulSave();
        List<ProviderAnswer<Boolean>> agreeingAnswers = List.of(
                new ProviderAnswer<>("alchemy", true),
                new ProviderAnswer<>("quicknode", true),
                new ProviderAnswer<>("infura", false));

        QuorumDecision decision = service.evaluate("ETHEREUM", "0xabc", FactType.EXISTENCE, agreeingAnswers);

        assertThat(decision.outcome()).isEqualTo(QuorumOutcome.AGREED);
        verifyNoInteractions(alerter);
    }

    @Test
    void alertFiresOnlyAfterTheDecisionIsSuccessfullyPersisted() {
        // Phase 9 (Kimi Phase 8 Issue 1): alert-after-save ordering, not before.
        stubNoExistingDecision();
        stubSuccessfulSave();
        List<ProviderAnswer<String>> disagreeingAnswers = List.of(
                new ProviderAnswer<>("alchemy", "A"),
                new ProviderAnswer<>("quicknode", "B"),
                new ProviderAnswer<>("infura", "C"));

        service.evaluate("ETHEREUM", "0xabc", FactType.TOKEN, disagreeingAnswers);

        InOrder order = inOrder(repository, alerter);
        order.verify(repository).save(any());
        order.verify(alerter).alert(any(), any(), any(), any());
    }

    @Test
    void aFailedSaveNeverTriggersAnAlert() {
        // Corollary of the above: if persistence fails, no alert should have fired for it either
        // (the reordering means the alert call is never reached).
        stubNoExistingDecision();
        RuntimeException dbFailure = new RuntimeException("connection refused");
        when(repository.save(any())).thenThrow(dbFailure);
        List<ProviderAnswer<String>> disagreeingAnswers = List.of(
                new ProviderAnswer<>("alchemy", "A"),
                new ProviderAnswer<>("quicknode", "B"),
                new ProviderAnswer<>("infura", "C"));

        assertThatThrownBy(() -> service.evaluate("ETHEREUM", "0xabc", FactType.TOKEN, disagreeingAnswers))
                .isSameAs(dbFailure);

        verifyNoInteractions(alerter);
    }

    @Test
    void agreeingCountAndProviderCountOnThePersistedDecisionMatchTheEvaluatorsComputation() {
        stubNoExistingDecision();
        ArgumentCaptor<QuorumDecision> captor = ArgumentCaptor.forClass(QuorumDecision.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        List<ProviderAnswer<Integer>> answers = List.of(
                new ProviderAnswer<>("alchemy", 10),
                new ProviderAnswer<>("quicknode", 10),
                new ProviderAnswer<>("infura", 10));

        service.evaluate("ETHEREUM", "0xabc", FactType.CONFIRMATIONS, answers);

        assertThat(captor.getValue().agreeingCount()).isEqualTo((short) 3);
        assertThat(captor.getValue().providerCount()).isEqualTo((short) 3);
    }

    @Test
    void decidedAtUsesTheInjectedClockNotWallClockTime() {
        stubNoExistingDecision();
        ArgumentCaptor<QuorumDecision> captor = ArgumentCaptor.forClass(QuorumDecision.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        List<ProviderAnswer<Boolean>> answers = List.of(
                new ProviderAnswer<>("alchemy", true),
                new ProviderAnswer<>("quicknode", true),
                new ProviderAnswer<>("infura", true));

        service.evaluate("ETHEREUM", "0xabc", FactType.EXISTENCE, answers);

        assertThat(captor.getValue().decidedAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void rejectsDuplicateProviderAnswersBeforeAnyCollaboratorIsInvoked() {
        // AC8 (Amendment #6): the existing-decision pre-flight check runs first (it needs the
        // repository to answer findByChainAndTxHashAndFactType), then duplicate-provider rejection -
        // so `save` and the alerter must never be reached, even though the repository's read finder
        // is legitimately consulted first.
        stubNoExistingDecision();
        List<ProviderAnswer<Boolean>> duplicateProviderAnswers = List.of(
                new ProviderAnswer<>("alchemy", true),
                new ProviderAnswer<>("alchemy", false),
                new ProviderAnswer<>("infura", true));

        assertThatThrownBy(() -> service.evaluate("ETHEREUM", "0xabc", FactType.EXISTENCE, duplicateProviderAnswers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alchemy");

        verify(repository, never()).save(any());
        verifyNoInteractions(alerter);
    }

    @Test
    void rejectsReEvaluationOfAnAlreadyDecidedFactBeforeAnyOtherCollaboratorIsInvoked() {
        // Phase 9 (Kimi Phase 8 Issue 7): pre-flight check against uq_quorum_tx_fact re-evaluation.
        QuorumDecision existing = QuorumDecision.create("ETHEREUM", "0xabc", FactType.EXISTENCE,
                QuorumOutcome.AGREED, 2, 3, FIXED_INSTANT);
        when(repository.findByChainAndTxHashAndFactType("ETHEREUM", "0xabc", FactType.EXISTENCE))
                .thenReturn(Optional.of(existing));
        List<ProviderAnswer<Boolean>> answers = List.of(
                new ProviderAnswer<>("alchemy", true),
                new ProviderAnswer<>("quicknode", true),
                new ProviderAnswer<>("infura", false));

        assertThatThrownBy(() -> service.evaluate("ETHEREUM", "0xabc", FactType.EXISTENCE, answers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        verify(repository, never()).save(any());
        verifyNoInteractions(alerter);
    }

    @Test
    void rejectsANullAnswersList() {
        assertThatThrownBy(() -> service.evaluate("ETHEREUM", "0xabc", FactType.EXISTENCE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("answers");
    }

    @Test
    void rejectsANullChain() {
        List<ProviderAnswer<Boolean>> answers = List.of(
                new ProviderAnswer<>("alchemy", true),
                new ProviderAnswer<>("quicknode", true),
                new ProviderAnswer<>("infura", false));

        assertThatThrownBy(() -> service.evaluate(null, "0xabc", FactType.EXISTENCE, answers))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("chain");
    }
}
