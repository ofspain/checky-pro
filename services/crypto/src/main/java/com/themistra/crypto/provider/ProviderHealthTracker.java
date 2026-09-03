package com.themistra.crypto.provider;

import com.themistra.crypto.common.config.ProviderHealthProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The single operation R5 describes: track a provider's health signal and, on a healthy-to-unhealthy
 * transition, alert ops via {@link ProviderDegradedPublisher}. Composes {@link
 * ProviderHealthRepository} (persistence) and {@link ProviderDegradedPublisher} (the outbox emission);
 * neither orchestrates the other. Not named in design.md §6's own package map, the same "functionally
 * necessary, not spec-named" situation {@code ObservationLog} (T08) and {@code QuorumDecisionService}
 * (T09) were in.
 *
 * <p><b>{@code @Transactional} (unlike T08/T09's coordinators).</b> The transition path here is two
 * calls - {@code repository.save} then {@code publisher.publish} (itself an {@code
 * OutboxEventRepository.save}) - that must commit together or not at all, mirroring agents.md's
 * outbox-in-same-transaction rule. This is the first task since T04 whose coordinator genuinely needs
 * an explicit transactional boundary spanning two collaborators, rather than a single write already
 * individually transactional via {@code SimpleJpaRepository.save}.</p>
 *
 * <p><b>The consecutive-disagreement counter is process-local, not persisted (Phase 3 Kimi Issues 2 and
 * 8, merged).</b> A {@code ConcurrentHashMap}/{@code AtomicInteger} structure, lost on every restart
 * (rolling deploy, crash, eviction) and uncoordinated across replicas - an accepted, disclosed
 * limitation of an operational signal, not a correctness-critical one (T09's quorum evaluation remains
 * the actual source of truth for {@code AGREED}/{@code HELD}). Persisting the count (a schema change)
 * or coordinating it cross-replica (design.md O5, requiring its own author approval) are both
 * explicitly deferred, not solved here.</p>
 *
 * <p>{@code "unhealthy"}/{@code "lagging"} (R5) are direct, caller-supplied signals via {@link
 * #recordUnhealthy} - this task provides the tracking primitive; detecting either condition from
 * adapter/watcher internals belongs to whichever future task first has a concrete signal to report.</p>
 *
 * <p><b>Two concurrency races are accepted, disclosed launch-scope risks, not fixed here (Phase 9,
 * Kimi Phase 8 Issues 1 and 2):</b> (1) two concurrent calls for a `(chain, provider)` pair with no
 * existing row can both attempt an INSERT, the second violating {@code provider_health}'s own {@code
 * UNIQUE (chain, provider)} constraint - a correct retry would need to run in a fresh transaction
 * (Postgres poisons the current one on a constraint violation), which is a larger restructuring than
 * this task's own narrow scope; (2) `ProviderHealth` carries no {@code @Version} column, so two
 * concurrent read-modify-write cycles for the same row can silently lose an update under {@code READ
 * COMMITTED} isolation - the correct fix (optimistic locking) requires a schema change beyond the
 * frozen brief's grant-only `V4` migration. Both are real; neither is solved by this task.</p>
 *
 * <p><b>The disagreement counter is also not transactional (Phase 9, Kimi Phase 8 Issue 3):</b> {@code
 * disagreementCounts} is a plain in-memory structure, incremented before the surrounding {@code
 * @Transactional} method is guaranteed to succeed. If {@code repository.save}/{@code publisher.publish}
 * subsequently throws and the transaction rolls back, the DB state correctly reverts but the counter
 * increment is not undone - self-corrects on the next call (which will still be at or above threshold),
 * but the counter and the persisted state can transiently disagree.</p>
 */
@Component
public class ProviderHealthTracker {

    private final ProviderHealthRepository repository;
    private final ProviderDegradedPublisher publisher;
    private final Clock clock;
    private final ProviderHealthProperties properties;
    private final ConcurrentMap<ProviderKey, AtomicInteger> disagreementCounts = new ConcurrentHashMap<>();

    public ProviderHealthTracker(ProviderHealthRepository repository, ProviderDegradedPublisher publisher,
                                  Clock clock, ProviderHealthProperties properties) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional
    public void recordHealthy(String chain, String provider) {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(provider, "provider");

        ProviderHealth health = fetchOrCreate(chain, provider);
        health.markHealthy(clock.instant());
        repository.save(health);
        disagreementCounts.remove(new ProviderKey(chain, provider));
    }

    @Transactional
    public void recordUnhealthy(String chain, String provider, DegradationReason reason) {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(reason, "reason");

        ProviderHealth health = fetchOrCreate(chain, provider);
        if (health.healthy()) {
            transitionToUnhealthy(chain, provider, health, reason, clock.instant());
        }
    }

    @Transactional
    public void recordDisagreement(String chain, String provider) {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(provider, "provider");

        ProviderHealth health = fetchOrCreate(chain, provider);
        Instant now = clock.instant();
        health.recordDisagreement(now);

        if (!health.healthy()) {
            // Already unhealthy: the timestamp still updates, but the counter is meaningful only for
            // the healthy-to-unhealthy transition (Phase 3 Kimi Issue 6) - never touched here.
            repository.save(health);
            return;
        }

        int count = disagreementCounts
                .computeIfAbsent(new ProviderKey(chain, provider), key -> new AtomicInteger())
                .incrementAndGet();
        if (count < properties.disagreementThreshold()) {
            repository.save(health);
            return;
        }

        transitionToUnhealthy(chain, provider, health, DegradationReason.REPEATED_DISAGREEMENT, now);
    }

    private void transitionToUnhealthy(String chain, String provider, ProviderHealth health,
                                        DegradationReason reason, Instant now) {
        health.markUnhealthy(now);
        repository.save(health);
        disagreementCounts.remove(new ProviderKey(chain, provider));
        publisher.publish(chain, provider, reason, now);
    }

    private ProviderHealth fetchOrCreate(String chain, String provider) {
        return repository.findByChainAndProvider(chain, provider)
                .orElseGet(() -> ProviderHealth.create(chain, provider, clock.instant()));
    }

    private record ProviderKey(String chain, String provider) {
    }
}
