package com.themistra.payment.payment;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T09 (L1). Plain JUnit, no Spring context — {@link PaymentStateMachine} is pure logic.
 *
 * <p>{@link #LEGAL_EDGES} is an independent, test-owned restatement of the frozen brief's own
 * 10-edge list (not a reference to {@code PaymentStateMachine}'s own private
 * {@code PERMITTED_TRANSITIONS} field) — two independently-written expressions of the same
 * documented fact catch more mistakes than one proof plus a derived check. The 10 named tests
 * below each assert one specific, literal pair directly (matching the frozen brief's own test
 * names verbatim); {@link #LEGAL_EDGES} is used only by the single exhaustive test to enumerate
 * the complementary 39 illegal pairs.</p>
 */
class PaymentStateMachineTest {

    private static final Map<PaymentState, Set<PaymentState>> LEGAL_EDGES = Map.of(
            PaymentState.CREATED, Set.of(PaymentState.WATCHING),
            PaymentState.WATCHING, Set.of(PaymentState.SEEN),
            PaymentState.SEEN, Set.of(PaymentState.CONFIRMING, PaymentState.FINALIZED, PaymentState.WATCHING),
            PaymentState.CONFIRMING, Set.of(PaymentState.FINALIZED, PaymentState.SEEN),
            PaymentState.FINALIZED, Set.of(PaymentState.ATTESTED, PaymentState.HELD),
            PaymentState.ATTESTED, Set.of(PaymentState.HELD),
            PaymentState.HELD, Set.of());

    private final PaymentStateMachine stateMachine = new PaymentStateMachine();

    // --- AC1: the 10 permitted edges, named individually per the frozen brief ---

    @Test
    void shouldTransitionCreatedToWatchingOnWatchAck() {
        assertLegal(PaymentState.CREATED, PaymentState.WATCHING);
    }

    @Test
    void shouldTransitionWatchingToSeenOnChainTxSeen() {
        assertLegal(PaymentState.WATCHING, PaymentState.SEEN);
    }

    @Test
    void shouldTransitionSeenToConfirmingOnChainTxConfirmed() {
        assertLegal(PaymentState.SEEN, PaymentState.CONFIRMING);
    }

    @Test
    void shouldTransitionConfirmingToFinalizedOnChainTxFinalized() {
        assertLegal(PaymentState.CONFIRMING, PaymentState.FINALIZED);
    }

    @Test
    void shouldTransitionSeenDirectlyToFinalizedWhenConfirmedNeverArrived() {
        assertLegal(PaymentState.SEEN, PaymentState.FINALIZED);
    }

    @Test
    void shouldTransitionConfirmingBackToSeenOnReorg() {
        assertLegal(PaymentState.CONFIRMING, PaymentState.SEEN);
    }

    @Test
    void shouldTransitionSeenBackToWatchingOnReorg() {
        assertLegal(PaymentState.SEEN, PaymentState.WATCHING);
    }

    @Test
    void shouldTransitionFinalizedToAttestedOnReceiptIssuance() {
        assertLegal(PaymentState.FINALIZED, PaymentState.ATTESTED);
    }

    @Test
    void shouldTransitionFinalizedToHeldOnBlockedAttest() {
        assertLegal(PaymentState.FINALIZED, PaymentState.HELD);
    }

    @Test
    void shouldTransitionAttestedToHeldOnPostAttestReorg() {
        assertLegal(PaymentState.ATTESTED, PaymentState.HELD);
    }

    private void assertLegal(PaymentState current, PaymentState target) {
        assertThat(stateMachine.transition(current, target)).isEqualTo(target);
        assertThat(stateMachine.isPermitted(current, target)).isTrue();
    }

    // --- AC2: every other ordered pair (39 of 49) throws, never silently no-ops ---

    /** Finding 4 (Phase 3/4): {@code HELD} is terminal — every {@code (HELD, x)} pair, including
     * {@code (HELD, HELD)}, is covered here since {@link #LEGAL_EDGES} maps {@code HELD} to an
     * empty set. */
    @Test
    void shouldThrowForEveryPairNotInThePermittedEdgeSet() {
        int illegalPairsChecked = 0;
        for (PaymentState current : PaymentState.values()) {
            for (PaymentState target : PaymentState.values()) {
                if (LEGAL_EDGES.get(current).contains(target)) {
                    continue;
                }
                illegalPairsChecked++;

                assertThatThrownBy(() -> stateMachine.transition(current, target))
                        .as("%s -> %s must be illegal", current, target)
                        .isInstanceOf(IllegalPaymentStateTransitionException.class)
                        .satisfies(thrown -> {
                            IllegalPaymentStateTransitionException ex = (IllegalPaymentStateTransitionException) thrown;
                            assertThat(ex.getCurrent()).isEqualTo(current);
                            assertThat(ex.getTarget()).isEqualTo(target);
                        });
                assertThat(stateMachine.isPermitted(current, target))
                        .as("%s -> %s must report false", current, target)
                        .isFalse();
            }
        }
        // 7 states x 7 states = 49 ordered pairs total, minus the 10 legal edges = 39.
        assertThat(illegalPairsChecked).isEqualTo(39);
    }

    // --- AC4: null handling ---

    @Test
    void transitionShouldThrowNullPointerExceptionForNullCurrent() {
        assertThatThrownBy(() -> stateMachine.transition(null, PaymentState.SEEN))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("current");
    }

    @Test
    void transitionShouldThrowNullPointerExceptionForNullTarget() {
        assertThatThrownBy(() -> stateMachine.transition(PaymentState.SEEN, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("target");
    }

    @Test
    void isPermittedShouldThrowNullPointerExceptionForNullCurrent() {
        assertThatThrownBy(() -> stateMachine.isPermitted(null, PaymentState.SEEN))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("current");
    }

    @Test
    void isPermittedShouldThrowNullPointerExceptionForNullTarget() {
        assertThatThrownBy(() -> stateMachine.isPermitted(PaymentState.SEEN, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("target");
    }

    // --- AC5: stateless, Spring-managed, zero-arg ---

    /** Phase 10 correction of the frozen brief's own AC5 wording: "zero declared fields" is read
     * as "zero <i>instance</i> fields" (already the reading Phase 7's self-review independently
     * reached) — {@code PERMITTED_TRANSITIONS} is a {@code private static final} immutable
     * lookup table, not per-instance mutable state, so its existence does not violate
     * "stateless." Also closes Phase 8 (Kimi) Finding 7: a future refactor that silently dropped
     * {@code @Component} would otherwise leave AC5 passing despite breaking DI at a future call
     * site (tasks 11/12/17/19). */
    @Test
    void shouldHaveNoInstanceFieldsAndAZeroArgConstructorAndRemainSpringManaged() {
        boolean hasInstanceField = Arrays.stream(PaymentStateMachine.class.getDeclaredFields())
                .map(Field::getModifiers)
                .anyMatch(modifiers -> !Modifier.isStatic(modifiers));
        assertThat(hasInstanceField)
                .as("PaymentStateMachine must have no instance fields - any static field must be an "
                        + "immutable, class-level constant, never per-instance state")
                .isFalse();

        Constructor<?>[] constructors = PaymentStateMachine.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterCount()).isZero();

        assertThat(PaymentStateMachine.class.isAnnotationPresent(Component.class))
                .as("PaymentStateMachine must remain a Spring bean for future injection")
                .isTrue();
    }
}
