package com.themistra.payment.payment;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The single, centralized source of truth for the payment verification state graph (L1) — pure
 * logic, no persistence, no Spring dependency beyond this stereotype annotation. A stateless
 * {@code @Component}, mirroring {@code crypto-service}'s own {@code AddressValidator} precedent
 * exactly: no injected dependency, but still a proper Spring bean for eventual injection at a
 * future call site (tasks 11, 12, 17, 19 — none of which exist yet).
 *
 * <p><b>The 10-edge set (Phase 3/4, Kimi Finding 1 — a genuine Human Approval decision, not a
 * default).</b> {@code design.md} §4c's own verbatim state-graph comment lists {@code any ->
 * HELD}; read literally, this would permit a {@code CREATED}/{@code WATCHING}/{@code SEEN}/
 * {@code CONFIRMING -> HELD} edge with no requirement anywhere backing it. The frozen brief's own
 * decision, made directly by the user rather than assumed: only {@code FINALIZED -> HELD} (R20,
 * a {@code BLOCKED} attest outcome) and {@code ATTESTED -> HELD} (L11, a post-attest reorg) are
 * permitted. {@code HELD} is therefore terminal within this task's own output — every
 * {@code (HELD, x)} pair, including {@code (HELD, HELD)}, is illegal. A future task that needs a
 * pre-finality compliance hold requires a new LOCKED decision, not a change here.</p>
 *
 * <p>{@link #transition} and {@link #isPermitted} both delegate to the same {@link
 * #PERMITTED_TRANSITIONS} lookup, so the "legal" and "illegal" views of the graph can never
 * silently drift apart from each other.</p>
 */
@Component
public class PaymentStateMachine {

    private static final Map<PaymentState, Set<PaymentState>> PERMITTED_TRANSITIONS =
            new EnumMap<>(PaymentState.class);

    static {
        PERMITTED_TRANSITIONS.put(PaymentState.CREATED, EnumSet.of(PaymentState.WATCHING));
        PERMITTED_TRANSITIONS.put(PaymentState.WATCHING, EnumSet.of(PaymentState.SEEN));
        PERMITTED_TRANSITIONS.put(PaymentState.SEEN,
                EnumSet.of(PaymentState.CONFIRMING, PaymentState.FINALIZED, PaymentState.WATCHING));
        PERMITTED_TRANSITIONS.put(PaymentState.CONFIRMING,
                EnumSet.of(PaymentState.FINALIZED, PaymentState.SEEN));
        PERMITTED_TRANSITIONS.put(PaymentState.FINALIZED,
                EnumSet.of(PaymentState.ATTESTED, PaymentState.HELD));
        PERMITTED_TRANSITIONS.put(PaymentState.ATTESTED, EnumSet.of(PaymentState.HELD));
        PERMITTED_TRANSITIONS.put(PaymentState.HELD, EnumSet.noneOf(PaymentState.class));
    }

    /**
     * @return {@code target}, unchanged, if the {@code (current, target)} pair is one of the 10
     * permitted edges (a convenience echo for a fluent call site, e.g. {@code payment.state =
     * stateMachine.transition(payment.state, target)}).
     * @throws IllegalPaymentStateTransitionException if the pair is not permitted — never a
     * silent no-op.
     */
    public PaymentState transition(PaymentState current, PaymentState target) {
        if (!isPermitted(current, target)) {
            throw new IllegalPaymentStateTransitionException(current, target);
        }
        return target;
    }

    /**
     * A non-throwing query, so a caller (task 11's own {@code ChainEventConsumer}) can check
     * permissibility first and treat a stale/out-of-order event (R14) as "ignore," rather than
     * relying on exception-driven control flow for a routine, expected case.
     */
    public boolean isPermitted(PaymentState current, PaymentState target) {
        Objects.requireNonNull(current, "current must not be null");
        Objects.requireNonNull(target, "target must not be null");
        return PERMITTED_TRANSITIONS.get(current).contains(target);
    }
}
