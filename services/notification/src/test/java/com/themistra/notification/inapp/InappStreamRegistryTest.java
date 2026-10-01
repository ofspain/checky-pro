package com.themistra.notification.inapp;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Plain JUnit, no Spring context, no Docker. Uses real {@link SseEmitter} instances throughout - a
 * bare, never-initialized emitter silently buffers a {@code send()} call rather than throwing
 * (verified empirically before writing this test), so a real emitter cannot itself prove "the
 * client actually received the event." What <em>is</em> provable, and is this registry's own real
 * logic, is its bookkeeping: register/push/deregister correctness. Calling {@code emitter.complete()}
 * directly (also verified empirically) reliably makes a subsequent {@code send()} throw
 * {@code IllegalStateException} - the same shape a genuinely dead connection produces - giving a
 * real, not simulated, way to exercise the dead-emitter cleanup path.
 *
 * <p>{@code emittersByAccount}'s own private state is inspected via reflection - a deliberate,
 * disclosed choice: the registry exposes no public accessor for it (nor should it, for production
 * callers), but Kimi Phase 8 Finding #4's own fix (atomic removal of an empty map entry) is only
 * verifiable by looking at that exact private state.</p>
 */
class InappStreamRegistryTest {

    private final InappStreamRegistry registry = new InappStreamRegistry();

    @SuppressWarnings("unchecked")
    private static Map<UUID, List<SseEmitter>> emittersByAccount(InappStreamRegistry registry) throws Exception {
        Field field = InappStreamRegistry.class.getDeclaredField("emittersByAccount");
        field.setAccessible(true);
        return (Map<UUID, List<SseEmitter>>) field.get(registry);
    }

    @Test
    void registerReturnsANonNullEmitterAndAddsItToTheRegistry() throws Exception {
        UUID accountUuid = UUID.randomUUID();

        SseEmitter emitter = registry.register(accountUuid);

        assertThat(emitter).isNotNull();
        assertThat(emittersByAccount(registry)).containsKey(accountUuid);
        assertThat(emittersByAccount(registry).get(accountUuid)).containsExactly(emitter);
    }

    @Test
    void registerAllowsMultipleEmittersForTheSameAccount() throws Exception {
        UUID accountUuid = UUID.randomUUID();

        SseEmitter first = registry.register(accountUuid);
        SseEmitter second = registry.register(accountUuid);

        assertThat(emittersByAccount(registry).get(accountUuid)).containsExactly(first, second);
    }

    @Test
    void pushToAnAccountWithNoConnectionIsASilentNoOp() {
        assertThatCode(() -> registry.push(UUID.randomUUID(), "notification", "payload"))
                .doesNotThrowAnyException();
    }

    /** Kimi Phase 8 Finding #4: a dead emitter (simulated via a real {@code complete()} call,
     * verified empirically to make the next {@code send()} throw {@code IllegalStateException} -
     * the same shape a genuinely dropped connection produces) is removed, and since it was the
     * account's only emitter, the account's own map entry is removed entirely - not left behind as
     * a permanent, empty list. */
    @Test
    void pushToACompletedEmitterRemovesItAndTheNowEmptyAccountEntry() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        SseEmitter emitter = registry.register(accountUuid);
        emitter.complete();

        assertThatCode(() -> registry.push(accountUuid, "notification", "payload")).doesNotThrowAnyException();

        assertThat(emittersByAccount(registry)).doesNotContainKey(accountUuid);
    }

    @Test
    void pushRemovesOnlyTheDeadEmitterLeavingLiveOnesInPlace() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        SseEmitter dead = registry.register(accountUuid);
        SseEmitter alive = registry.register(accountUuid);
        dead.complete();

        registry.push(accountUuid, "notification", "payload");

        assertThat(emittersByAccount(registry).get(accountUuid)).containsExactly(alive);
    }

    @Test
    void concurrentRegisterAcrossDistinctAccountsLosesNoRegistration() throws Exception {
        int accountCount = 16;
        List<UUID> accountUuids = java.util.stream.Stream.generate(UUID::randomUUID)
                .limit(accountCount).toList();
        ExecutorService pool = Executors.newFixedThreadPool(accountCount);
        CountDownLatch ready = new CountDownLatch(accountCount);
        CountDownLatch start = new CountDownLatch(1);

        for (UUID accountUuid : accountUuids) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                registry.register(accountUuid);
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        boolean finishedCleanly = pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(finishedCleanly).as("all threads must finish without hanging").isTrue();
        Map<UUID, List<SseEmitter>> state = emittersByAccount(registry);
        for (UUID accountUuid : accountUuids) {
            assertThat(state).as("account %s must be registered", accountUuid).containsKey(accountUuid);
        }
    }

    /** Kimi Phase 11 Gap #6: interleaves {@link InappStreamRegistry#register} and
     * {@link InappStreamRegistry#push} on the *same* account from concurrent threads - a race
     * between adding a new emitter and iterating the list to push could theoretically drop a
     * registration or throw, given {@code CopyOnWriteArrayList}'s own snapshot-iteration semantics
     * are what this test empirically confirms hold under real concurrent load, not merely trusted
     * by reading the JDK's own documented contract. */
    @Test
    void concurrentRegisterAndPushOnTheSameAccountNeverThrowsAndLosesNoRegistration() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        int registerCount = 16;
        int pushCount = 16;
        ExecutorService pool = Executors.newFixedThreadPool(registerCount + pushCount);
        CountDownLatch ready = new CountDownLatch(registerCount + pushCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = new java.util.concurrent.CopyOnWriteArrayList<>();

        for (int i = 0; i < registerCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                await(start);
                try {
                    registry.register(accountUuid);
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
        }
        for (int i = 0; i < pushCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                await(start);
                try {
                    registry.push(accountUuid, "notification", "payload");
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        boolean finishedCleanly = pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(finishedCleanly).as("all threads must finish without hanging").isTrue();
        assertThat(failures).as("neither register nor push may ever throw").isEmpty();
        assertThat(emittersByAccount(registry).get(accountUuid))
                .as("all %s registered emitters must still be present - none dropped by a concurrent push", registerCount)
                .hasSize(registerCount);
    }

    private static void await(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
