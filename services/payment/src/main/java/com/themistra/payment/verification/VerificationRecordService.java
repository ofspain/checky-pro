package com.themistra.payment.verification;

import com.themistra.payment.payment.PaymentState;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

/**
 * T10 (R11, R12, R14). The append-only recorder for every payment state movement — this class
 * trusts its caller completely: it never references {@link
 * com.themistra.payment.payment.PaymentStateMachine} and has no opinion on whether a given
 * {@code (fromState, toState)} pair is a legal transition (L1), only that it happened and must be
 * recorded. {@code triggerEventKey} is an intentional, unvalidated passthrough (Phase 3 Finding
 * 4) — no real producer of this key exists yet (task 11's own scope); validating its shape here,
 * with nothing yet constructing a real one, would be speculative.
 */
@Component
public class VerificationRecordService {

    private final VerificationRecordRepository repository;
    private final Clock clock;

    public VerificationRecordService(VerificationRecordRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Records a forward transition or a reorg reversal alike (R9-R12) — mechanically identical,
     * a state pair plus an optional trigger key/confirmation count/detail payload. */
    @Transactional
    public VerificationRecord record(Long paymentId, PaymentState fromState, PaymentState toState,
                                      String triggerEventKey, Integer confirmations, Map<String, Object> detail) {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(toState, "toState must not be null");
        VerificationRecord verificationRecord = VerificationRecord.create(paymentId, fromState, toState,
                triggerEventKey, confirmations, detail, clock.instant());
        return repository.save(verificationRecord);
    }

    /** R14. {@code fromState = null} (Phase 3 Finding 1, not {@code currentState}) — an explicit,
     * unambiguous "no real transition occurred" signal; {@code toState} alone already carries
     * the unchanged current state, so a redundant self-transition would add nothing but
     * ambiguity. */
    @Transactional
    public VerificationRecord recordIgnored(Long paymentId, PaymentState currentState,
                                             String triggerEventKey, Map<String, Object> detail) {
        Objects.requireNonNull(currentState, "currentState must not be null");
        return record(paymentId, null, currentState, triggerEventKey, null, detail);
    }
}
