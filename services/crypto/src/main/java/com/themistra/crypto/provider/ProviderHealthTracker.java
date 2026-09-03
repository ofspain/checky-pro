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
