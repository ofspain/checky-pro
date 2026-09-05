package com.themistra.crypto.provider;

import com.themistra.crypto.common.config.ProviderHealthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

/** The named test from package.md §8 (`shouldEmitProviderDegradedWhenAProviderIsUnhealthy`), AC1-AC3.
 * {@link ProviderHealthRepository} is mocked but backed by a simple in-memory map so sequential calls
 * within one test observe realistic upsert behavior (find returns whatever was last saved). */
@ExtendWith(MockitoExtension.class)
class ProviderHealthTrackerTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Mock
    private ProviderHealthRepository repository;
    @Mock
    private ProviderDegradedPublisher publisher;

    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ProviderHealthProperties properties = new ProviderHealthProperties(3);
    private final Map<String, ProviderHealth> store = new HashMap<>();

    private ProviderHealthTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ProviderHealthTracker(repository, publisher, fixedClock, properties);
        // lenient(): the null-guard tests below throw before ever reaching the repository, so these
        // stubs are legitimately unused in those specific test methods.
        lenient().when(repository.findByChainAndProvider(any(), any())).thenAnswer(invocation -> {
            String chain = invocation.getArgument(0);
            String provider = invocation.getArgument(1);
            return Optional.ofNullable(store.get(chain + ":" + provider));
        });
        lenient().when(repository.save(any())).thenAnswer(invocation -> {
            ProviderHealth health = invocation.getArgument(0);
            store.put(health.chain() + ":" + health.provider(), health);
            return health;
        });
    }

    @Test
    void shouldEmitProviderDegradedWhenAProviderIsUnhealthy() {
        tracker.recordUnhealthy("ETHEREUM", "alchemy", DegradationReason.UNHEALTHY);

        verify(publisher).publish("ETHEREUM", "alchemy", DegradationReason.UNHEALTHY, NOW);
        assertThat(store.get("ETHEREUM:alchemy").healthy()).isFalse();
    }

    @Test
    void recordUnhealthyOnAnAlreadyUnhealthyProviderDoesNotReemitOrResave() {
        tracker.recordUnhealthy("ETHEREUM", "alchemy", DegradationReason.UNHEALTHY);
        tracker.recordUnhealthy("ETHEREUM", "alchemy", DegradationReason.LAGGING);

        verify(publisher, times(1)).publish(any(), any(), any(), any());
        verify(repository, times(1)).save(any());
    }

    @Test
    void recordHealthyNeverEmitsAnEvent() {
        tracker.recordHealthy("ETHEREUM", "alchemy");

        verifyNoInteractions(publisher);
        assertThat(store.get("ETHEREUM:alchemy").healthy()).isTrue();
    }

    @Test
    void recordDisagreementBelowThresholdDoesNotTransitionAndReachingItDoes() {
        tracker.recordDisagreement("ETHEREUM", "alchemy");
        tracker.recordDisagreement("ETHEREUM", "alchemy");
        verifyNoInteractions(publisher);
        assertThat(store.get("ETHEREUM:alchemy").healthy()).isTrue();

        tracker.recordDisagreement("ETHEREUM", "alchemy");

        verify(publisher).publish("ETHEREUM", "alchemy", DegradationReason.REPEATED_DISAGREEMENT, NOW);
        assertThat(store.get("ETHEREUM:alchemy").healthy()).isFalse();
    }

    @Test
    void recordHealthyResetsTheDisagreementCounter() {
        tracker.recordDisagreement("ETHEREUM", "alchemy");
        tracker.recordDisagreement("ETHEREUM", "alchemy");
        tracker.recordHealthy("ETHEREUM", "alchemy");

        tracker.recordDisagreement("ETHEREUM", "alchemy");
        tracker.recordDisagreement("ETHEREUM", "alchemy");

        // Only 2 disagreements accumulated since the reset - threshold (3) not reached.
        verifyNoInteractions(publisher);
        assertThat(store.get("ETHEREUM:alchemy").healthy()).isTrue();
    }

    @Test
    void recordDisagreementWhileAlreadyUnhealthyUpdatesTimestampButNeverTouchesTheCounterOrPublishes() {
        tracker.recordUnhealthy("ETHEREUM", "alchemy", DegradationReason.UNHEALTHY);
        verify(publisher, times(1)).publish(any(), any(), any(), any());

        tracker.recordDisagreement("ETHEREUM", "alchemy");

        // Still just the one publish from recordUnhealthy - a disagreement while already unhealthy
        // never re-publishes.
        verify(publisher, times(1)).publish(any(), any(), any(), any());
        assertThat(store.get("ETHEREUM:alchemy").lastDisagreementAt()).isEqualTo(NOW);
        assertThat(store.get("ETHEREUM:alchemy").healthy()).isFalse();

        // Recover, then prove the full threshold is needed again - the counter was never
        // incremented while unhealthy above, so only 2 more calls should not trip it.
        tracker.recordHealthy("ETHEREUM", "alchemy");
        tracker.recordDisagreement("ETHEREUM", "alchemy");
        tracker.recordDisagreement("ETHEREUM", "alchemy");
        verify(publisher, times(1)).publish(any(), any(), any(), any());

        tracker.recordDisagreement("ETHEREUM", "alchemy");
        verify(publisher, times(2)).publish(any(), any(), any(), any());
    }

    @Test
    void recordHealthyRejectsNullArguments() {
        assertThatThrownBy(() -> tracker.recordHealthy(null, "alchemy"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("chain");
        assertThatThrownBy(() -> tracker.recordHealthy("ETHEREUM", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("provider");
    }

    @Test
    void recordUnhealthyRejectsNullArguments() {
        assertThatThrownBy(() -> tracker.recordUnhealthy(null, "alchemy", DegradationReason.UNHEALTHY))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("chain");
        assertThatThrownBy(() -> tracker.recordUnhealthy("ETHEREUM", null, DegradationReason.UNHEALTHY))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("provider");
        assertThatThrownBy(() -> tracker.recordUnhealthy("ETHEREUM", "alchemy", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("reason");
    }

    @Test
    void recordDisagreementRejectsNullArguments() {
        assertThatThrownBy(() -> tracker.recordDisagreement(null, "alchemy"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("chain");
        assertThatThrownBy(() -> tracker.recordDisagreement("ETHEREUM", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("provider");
    }

    @Test
    void distinctProvidersOnTheSameChainAreTrackedIndependently() {
        tracker.recordUnhealthy("ETHEREUM", "alchemy", DegradationReason.UNHEALTHY);

        tracker.recordDisagreement("ETHEREUM", "quicknode");

        assertThat(store.get("ETHEREUM:alchemy").healthy()).isFalse();
        assertThat(store.get("ETHEREUM:quicknode").healthy()).isTrue();
        verify(publisher, times(1)).publish(eq("ETHEREUM"), eq("alchemy"), any(), any());
        verify(publisher, never()).publish(eq("ETHEREUM"), eq("quicknode"), any(), any());
    }

    @Test
    void distinctChainsTrackTheSameProviderNameIndependently() {
        // Phase 11 Gap 10: the inverse of the above - same provider name, different chains.
        tracker.recordUnhealthy("ETHEREUM", "alchemy", DegradationReason.UNHEALTHY);

        tracker.recordUnhealthy("TRON", "alchemy", DegradationReason.LAGGING);

        assertThat(store.get("ETHEREUM:alchemy").healthy()).isFalse();
        assertThat(store.get("TRON:alchemy").healthy()).isFalse();
        verify(publisher).publish("ETHEREUM", "alchemy", DegradationReason.UNHEALTHY, NOW);
        verify(publisher).publish("TRON", "alchemy", DegradationReason.LAGGING, NOW);
    }

    @Test
    void recordUnhealthyWithRepeatedDisagreementReasonPassedDirectlyTransitionsAndEmits() {
        // Phase 11 Gap 11: REPEATED_DISAGREEMENT can be passed directly to recordUnhealthy, not
        // only reached indirectly via the disagreement-counter threshold.
        tracker.recordUnhealthy("ETHEREUM", "alchemy", DegradationReason.REPEATED_DISAGREEMENT);

        verify(publisher).publish("ETHEREUM", "alchemy", DegradationReason.REPEATED_DISAGREEMENT, NOW);
        assertThat(store.get("ETHEREUM:alchemy").healthy()).isFalse();
    }

    @Test
    void trackerWiresLastOkAtAndLastDisagreementAtThroughToThePersistedRowEndToEnd() {
        // Phase 11 Gap 9: ProviderHealthTest already proves the mutators in isolation; this proves
        // the tracker actually wires them through the full recordHealthy/recordDisagreement call path.
        tracker.recordHealthy("ETHEREUM", "alchemy");
        assertThat(store.get("ETHEREUM:alchemy").lastOkAt()).isEqualTo(NOW);
        assertThat(store.get("ETHEREUM:alchemy").lastDisagreementAt()).isNull();

        tracker.recordDisagreement("ETHEREUM", "alchemy");
        assertThat(store.get("ETHEREUM:alchemy").lastDisagreementAt()).isEqualTo(NOW);
        // lastOkAt is a historical marker, never cleared by a disagreement signal.
        assertThat(store.get("ETHEREUM:alchemy").lastOkAt()).isEqualTo(NOW);
    }

    @Test
    void allThreePublicMethodsAreTransactional() {
        // Phase 11 Gap 4: regression guard for the documented outbox-atomicity invariant (class
        // Javadoc) - a future refactor removing @Transactional would break save+publish atomicity
        // without failing any other existing test.
        for (String methodName : new String[] {"recordHealthy", "recordUnhealthy", "recordDisagreement"}) {
            Method method = Arrays.stream(ProviderHealthTracker.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            assertThat(method.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                    .as("%s must be @Transactional", methodName)
                    .isTrue();
        }
    }
}
