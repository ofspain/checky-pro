package com.themistra.crypto.quorum;

import com.themistra.crypto.observation.FactType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AC4 (counts persisted exactly), AC5 (no mutator beyond construction - grant-enforced
 * immutability), plus the Phase 9 range-check fix (Kimi Phase 8 Issue 3). */
class QuorumDecisionTest {

    @Test
    void createPopulatesEveryFieldExactlyAsGiven() {
        Instant decidedAt = Instant.parse("2026-09-03T12:00:00Z");

        QuorumDecision decision = QuorumDecision.create("ETHEREUM", "0xabc123", FactType.EXISTENCE,
                QuorumOutcome.AGREED, 2, 3, decidedAt);

        assertThat(decision.chain()).isEqualTo("ETHEREUM");
        assertThat(decision.txHash()).isEqualTo("0xabc123");
        assertThat(decision.factType()).isEqualTo(FactType.EXISTENCE);
        assertThat(decision.outcome()).isEqualTo(QuorumOutcome.AGREED);
        assertThat(decision.agreeingCount()).isEqualTo((short) 2);
        assertThat(decision.providerCount()).isEqualTo((short) 3);
        assertThat(decision.decidedAt()).isEqualTo(decidedAt);
        assertThat(decision.id()).isNull(); // DB-generated, unset until persisted
    }

    @Test
    void createPersistsAHeldOutcomeJustAsFaithfullyAsAgreed() {
        QuorumDecision decision = QuorumDecision.create("TRON", "abc", FactType.AMOUNT,
                QuorumOutcome.HELD, 1, 3, Instant.now());

        assertThat(decision.outcome()).isEqualTo(QuorumOutcome.HELD);
        assertThat(decision.agreeingCount()).isEqualTo((short) 1);
    }

    @Test
    void createRejectsANegativeAgreeingCount() {
        assertThatThrownBy(() -> QuorumDecision.create("ETHEREUM", "0xabc", FactType.EXISTENCE,
                QuorumOutcome.HELD, -1, 3, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agreeingCount");
    }

    @Test
    void createRejectsAProviderCountExceedingShortRange() {
        assertThatThrownBy(() -> QuorumDecision.create("ETHEREUM", "0xabc", FactType.EXISTENCE,
                QuorumOutcome.AGREED, 2, Short.MAX_VALUE + 1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerCount");
    }

    @Test
    void hasNoPublicMutatorBeyondConstruction() {
        // AC5, grant-enforced: crypto_app has INSERT+SELECT only on chain.quorum_decisions. Every
        // declared public, non-static method on this class must be a zero-arg, non-void getter -
        // never a setter/mutator, and never a static factory beyond `create`.
        List<Method> nonGetterPublicMethods = java.util.Arrays.stream(QuorumDecision.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> m.getParameterCount() > 0 || m.getReturnType() == void.class)
                .toList();

        assertThat(nonGetterPublicMethods).isEmpty();
    }
}
