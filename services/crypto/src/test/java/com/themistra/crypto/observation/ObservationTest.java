package com.themistra.crypto.observation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** AC1 (verbatim payload), AC3 (no mutator — grant-enforced immutability). */
class ObservationTest {

    @Test
    void createPopulatesEveryFieldExactlyAsGiven() {
        Instant observedAt = Instant.parse("2026-09-03T12:00:00Z");

        Observation observation = Observation.create("ETHEREUM", "0xabc123", "alchemy",
                FactType.EXISTENCE, "{\"exists\":true}", "chain-observations/key.json", observedAt);

        assertThat(observation.chain()).isEqualTo("ETHEREUM");
        assertThat(observation.txHash()).isEqualTo("0xabc123");
        assertThat(observation.provider()).isEqualTo("alchemy");
        assertThat(observation.factType()).isEqualTo(FactType.EXISTENCE);
        assertThat(observation.rawResponse()).isEqualTo("{\"exists\":true}");
        assertThat(observation.s3SnapshotKey()).isEqualTo("chain-observations/key.json");
        assertThat(observation.observedAt()).isEqualTo(observedAt);
        assertThat(observation.id()).isNull(); // DB-generated, unset until persisted
    }

    @Test
    void createAcceptsANullS3SnapshotKeyForAFailedSnapshotWrite() {
        Observation observation = Observation.create("TRON", "abc", "trongrid", FactType.AMOUNT, "{}",
                null, Instant.now());

        assertThat(observation.s3SnapshotKey()).isNull();
    }

    @Test
    void hasNoPublicMutatorBeyondConstruction() {
        // AC3, grant-enforced: crypto_app has INSERT+SELECT only on chain.observations. Every
        // declared public, non-static method on this class must be a zero-arg, non-void getter -
        // never a setter/mutator, and never a static factory beyond `create`.
        List<Method> nonGetterPublicMethods = java.util.Arrays.stream(Observation.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> m.getParameterCount() > 0 || m.getReturnType() == void.class)
                .toList();

        assertThat(nonGetterPublicMethods).isEmpty();
    }
}
