package com.themistra.payment.verification;

import com.themistra.payment.payment.PaymentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationRecordServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final Long PAYMENT_ID = 42L;
    private static final String TRIGGER_EVENT_KEY = "ETHEREUM:0xabc:chain.tx.seen";

    @Mock
    private VerificationRecordRepository repository;

    private VerificationRecordService service;

    @BeforeEach
    void setUp() {
        service = new VerificationRecordService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldWalkBackwardConfirmingToSeenOnReorg() {
        when(repository.save(any(VerificationRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VerificationRecord saved = service.record(PAYMENT_ID, PaymentState.CONFIRMING, PaymentState.SEEN,
                TRIGGER_EVENT_KEY, null, null);

        assertThat(saved.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(saved.getFromState()).isEqualTo(PaymentState.CONFIRMING);
        assertThat(saved.getToState()).isEqualTo(PaymentState.SEEN);
        assertThat(saved.getTriggerEventKey()).isEqualTo(TRIGGER_EVENT_KEY);
        assertThat(saved.getConfirmations()).isNull();
        assertThat(saved.getDetail()).isNull();
        assertThat(saved.getOccurredAt()).isEqualTo(NOW);
    }

    @Test
    void shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm() {
        when(repository.save(any(VerificationRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VerificationRecord saved = service.record(PAYMENT_ID, PaymentState.SEEN, PaymentState.WATCHING,
                TRIGGER_EVENT_KEY, null, null);

        assertThat(saved.getFromState()).isEqualTo(PaymentState.SEEN);
        assertThat(saved.getToState()).isEqualTo(PaymentState.WATCHING);
        assertThat(saved.getTriggerEventKey()).isEqualTo(TRIGGER_EVENT_KEY);
    }

    /** R14 (Phase 3/4 Finding 1): {@code fromState} is {@code null} - an explicit, unambiguous
     * "no real transition occurred" signal - not a redundant {@code fromState == toState}
     * self-transition. */
    @Test
    void shouldIgnoreOutOfOrderOrStaleChainEvents() {
        when(repository.save(any(VerificationRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Map<String, Object> detail = Map.of("reason", "stale", "actualState", "FINALIZED");

        VerificationRecord saved = service.recordIgnored(PAYMENT_ID, PaymentState.FINALIZED,
                TRIGGER_EVENT_KEY, detail);

        assertThat(saved.getFromState()).isNull();
        assertThat(saved.getToState()).isEqualTo(PaymentState.FINALIZED);
        assertThat(saved.getConfirmations()).isNull();
        assertThat(saved.getDetail()).containsExactlyInAnyOrderEntriesOf(detail);
        verify(repository).save(any(VerificationRecord.class));
    }

    @Test
    void shouldRecordAnOrdinaryForwardTransition() {
        when(repository.save(any(VerificationRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VerificationRecord saved = service.record(PAYMENT_ID, PaymentState.CREATED, PaymentState.WATCHING,
                null, null, null);

        assertThat(saved.getFromState()).isEqualTo(PaymentState.CREATED);
        assertThat(saved.getToState()).isEqualTo(PaymentState.WATCHING);
        assertThat(saved.getTriggerEventKey()).isNull();
        // Phase 11 Gap 3 (Kimi): the thenAnswer-echo style alone doesn't prove repository.save was
        // actually called - a broken implementation returning a hand-built object would still pass.
        verify(repository).save(any(VerificationRecord.class));
    }

    /** Phase 11 Gap 5 (Kimi): {@code record} itself (not just {@code recordIgnored}) must also
     * accept a null {@code fromState} - e.g. the very first recording of a payment, with no prior
     * state at all. A regression that accidentally added a null-check on {@code fromState} would
     * break this. */
    @Test
    void shouldRecordTransitionWithNullFromState() {
        when(repository.save(any(VerificationRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VerificationRecord saved = service.record(PAYMENT_ID, null, PaymentState.CREATED,
                TRIGGER_EVENT_KEY, null, null);

        assertThat(saved.getFromState()).isNull();
        assertThat(saved.getToState()).isEqualTo(PaymentState.CREATED);
    }

    @Test
    void shouldPersistConfirmationCountWhenProvided() {
        when(repository.save(any(VerificationRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VerificationRecord saved = service.record(PAYMENT_ID, PaymentState.SEEN, PaymentState.CONFIRMING,
                TRIGGER_EVENT_KEY, 3, null);

        assertThat(saved.getConfirmations()).isEqualTo(3);
    }

    @Test
    void recordShouldThrowNullPointerExceptionForNullPaymentId() {
        assertThatThrownBy(() -> service.record(null, PaymentState.SEEN, PaymentState.CONFIRMING,
                TRIGGER_EVENT_KEY, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("paymentId");
    }

    @Test
    void recordShouldThrowNullPointerExceptionForNullToState() {
        assertThatThrownBy(() -> service.record(PAYMENT_ID, PaymentState.SEEN, null,
                TRIGGER_EVENT_KEY, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("toState");
    }

    @Test
    void recordIgnoredShouldThrowNullPointerExceptionForNullPaymentId() {
        assertThatThrownBy(() -> service.recordIgnored(null, PaymentState.SEEN, TRIGGER_EVENT_KEY, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("paymentId");
    }

    @Test
    void recordIgnoredShouldThrowNullPointerExceptionForNullCurrentState() {
        assertThatThrownBy(() -> service.recordIgnored(PAYMENT_ID, null, TRIGGER_EVENT_KEY, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("currentState");
    }

    /** Phase 8 (Kimi Finding 1), regression guard: mutating the map after the call must not
     * change what was persisted. */
    @Test
    void shouldNotBeAffectedByCallerMutatingTheDetailMapAfterRecording() {
        ArgumentCaptor<VerificationRecord> captor = ArgumentCaptor.forClass(VerificationRecord.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        Map<String, Object> mutableDetail = new HashMap<>(Map.of("reason", "stale"));

        service.recordIgnored(PAYMENT_ID, PaymentState.FINALIZED, TRIGGER_EVENT_KEY, mutableDetail);
        mutableDetail.put("reason", "mutated-after-the-fact");

        assertThat(captor.getValue().getDetail()).containsEntry("reason", "stale");
    }

    /** Phase 11 Gap 1 (Kimi): the prior test proves a pre-persistence mutation can't leak in;
     * this proves the *returned* {@code detail} is also unmodifiable - a consumer calling
     * {@code getDetail()} and attempting to mutate it must fail loudly, not silently corrupt a
     * shared map other code might also be holding a reference to. */
    @Test
    void getDetailShouldReturnAnUnmodifiableMap() {
        when(repository.save(any(VerificationRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // A genuinely mutable input (not Map.of(...), which is already unmodifiable and would
        // make this test pass even if VerificationRecord provided no protection of its own).
        VerificationRecord saved = service.recordIgnored(PAYMENT_ID, PaymentState.FINALIZED,
                TRIGGER_EVENT_KEY, new HashMap<>(Map.of("reason", "stale")));

        Map<String, Object> detail = saved.getDetail();

        assertThatThrownBy(() -> detail.put("reason", "mutated"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** L7 (AC5, Phase 3/4 Finding 2): no code in this task's own three production files calls
     * delete/update - widened from the Phase 2 brief's own narrower two-file check after Kimi's
     * Phase 3 review found the entity file itself was unexamined. */
    @Test
    void verificationPackageProductionFilesShouldNeverCallDeleteOrUpdate() throws IOException {
        for (String file : new String[] {
                "VerificationRecord.java", "VerificationRecordRepository.java", "VerificationRecordService.java"}) {
            String source = Files.readString(Path.of(
                    "src/main/java/com/themistra/payment/verification/" + file));
            assertThat(source)
                    .as("%s must never call delete/update - verification_records is append-only (L7)", file)
                    .doesNotContain(".delete(")
                    .doesNotContain(".deleteById(")
                    .doesNotContain(".deleteAll(")
                    .doesNotContainPattern("\\.update\\(");
        }
    }
}
