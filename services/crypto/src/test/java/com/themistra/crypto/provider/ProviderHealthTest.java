package com.themistra.crypto.provider;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AC1 (upsert per (chain, provider)), the three named mutators (no raw setters). */
class ProviderHealthTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    void createIsHealthyByDefaultWithLastOkAndLastDisagreementUnset() {
        ProviderHealth health = ProviderHealth.create("ETHEREUM", "alchemy", NOW);

        assertThat(health.chain()).isEqualTo("ETHEREUM");
        assertThat(health.provider()).isEqualTo("alchemy");
        assertThat(health.healthy()).isTrue();
        assertThat(health.updatedAt()).isEqualTo(NOW);
        assertThat(health.lastOkAt()).isNull();
        assertThat(health.lastDisagreementAt()).isNull();
        assertThat(health.id()).isNull();
    }

    @Test
    void createRejectsNullArguments() {
        assertThatThrownBy(() -> ProviderHealth.create(null, "alchemy", NOW))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("chain");
        assertThatThrownBy(() -> ProviderHealth.create("ETHEREUM", null, NOW))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("provider");
        assertThatThrownBy(() -> ProviderHealth.create("ETHEREUM", "alchemy", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("now");
    }

    @Test
    void markHealthySetsHealthyAndLastOkAtButDoesNotClearLastDisagreementAt() {
        ProviderHealth health = ProviderHealth.create("ETHEREUM", "alchemy", NOW);
        Instant disagreedAt = NOW.plusSeconds(10);
        health.recordDisagreement(disagreedAt);
        health.markUnhealthy(disagreedAt);

        Instant recoveredAt = NOW.plusSeconds(20);
        health.markHealthy(recoveredAt);

        assertThat(health.healthy()).isTrue();
        assertThat(health.lastOkAt()).isEqualTo(recoveredAt);
        assertThat(health.updatedAt()).isEqualTo(recoveredAt);
        // Phase 3 Kimi Issue 10: lastDisagreementAt is a historical marker, never cleared by recovery.
        assertThat(health.lastDisagreementAt()).isEqualTo(disagreedAt);
    }

    @Test
    void markUnhealthySetsHealthyFalseButDoesNotTouchLastOkAt() {
        ProviderHealth health = ProviderHealth.create("ETHEREUM", "alchemy", NOW);
        Instant okAt = NOW.plusSeconds(5);
        health.markHealthy(okAt);

        Instant unhealthyAt = NOW.plusSeconds(15);
        health.markUnhealthy(unhealthyAt);

        assertThat(health.healthy()).isFalse();
        assertThat(health.updatedAt()).isEqualTo(unhealthyAt);
        assertThat(health.lastOkAt()).isEqualTo(okAt);
    }

    @Test
    void recordDisagreementSetsLastDisagreementAtButDoesNotTouchHealthy() {
        ProviderHealth health = ProviderHealth.create("ETHEREUM", "alchemy", NOW);
        Instant disagreedAt = NOW.plusSeconds(7);

        health.recordDisagreement(disagreedAt);

        assertThat(health.lastDisagreementAt()).isEqualTo(disagreedAt);
        assertThat(health.updatedAt()).isEqualTo(disagreedAt);
        assertThat(health.healthy()).isTrue();
    }

    @Test
    void hasNoVersionFieldYetLostUpdatesUnderConcurrentAccessAreAnAcceptedRisk() {
        // Phase 11 Gap 3 (Kimi Phase 8 Issue 2): no @Version/optimistic-locking column exists today -
        // a documented, accepted launch-scope risk (ProviderHealthTracker's class Javadoc). This is a
        // tripwire, not a defense: if a future change adds @Version, this test should be revisited
        // alongside a deliberate review of whether the lost-update risk was actually addressed.
        boolean hasVersionField = java.util.Arrays.stream(ProviderHealth.class.getDeclaredFields())
                .anyMatch(f -> f.isAnnotationPresent(jakarta.persistence.Version.class));

        assertThat(hasVersionField).isFalse();
    }

    @Test
    void hasOnlyTheThreeNamedMutatorsBeyondGetters() {
        // AC1/entity-shape: every declared public, non-static method must be a zero-arg getter or
        // one of the three named mutators - never a raw setter.
        Set<String> allowedMutators = Set.of("markHealthy", "markUnhealthy", "recordDisagreement");

        List<Method> unexpected = java.util.Arrays.stream(ProviderHealth.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> !(m.getParameterCount() == 0 && m.getReturnType() != void.class))
                .filter(m -> !allowedMutators.contains(m.getName()))
                .toList();

        assertThat(unexpected).isEmpty();
    }
}
