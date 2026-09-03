package com.themistra.crypto.observation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * The single operation R4/L3 describe: "persist a provider response verbatim." Composes {@link
 * ObservationSnapshotStore} (S3) and {@link ObservationRepository} (Postgres) — neither low-level
 * component orchestrates the other. Not named in design.md §6's package map, the same
 * "functionally necessary, not spec-named" situation {@code OutboxRelay} and the chain-adapter
 * {@code *Config} classes were in for T04/T06/T07.
 *
 * <p><b>Ordering (the frozen brief's central decision, not a preference):</b> the S3 write is
 * attempted first, then exactly one Postgres insert carries whatever key resulted (possibly {@code
 * null}). This is forced by {@code crypto_app}'s grant on {@code chain.observations} having no
 * {@code UPDATE} — an "insert then backfill the S3 key" pattern is structurally impossible against
 * this schema, not merely undesirable.</p>
 *
 * <p><b>Deliberately not {@code @Transactional} (Phase 9, Kimi/self-review Issue 2).</b> This method's
 * only database write is the single {@link ObservationRepository#save} call, and Spring Data's own
 * {@code SimpleJpaRepository.save(...)} is already individually {@code @Transactional} (confirmed by
 * reading its source — class-level {@code @Transactional(readOnly = true)}, method-level
 * {@code @Transactional} on {@code save}). Annotating this method too would have widened the
 * transactional/connection-held scope to include the S3 call above it — up to 5 seconds
 * (see {@link ObservationSnapshotStoreConfig}) of network I/O holding a pooled DB connection for no
 * reason, exactly the connection-pool-exhaustion risk the frozen brief's own transaction constraint
 * exists to avoid.</p>
 */
@Component
public class ObservationLog {

    private final ObservationSnapshotStore snapshotStore;
    private final ObservationRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ObservationLog(ObservationSnapshotStore snapshotStore, ObservationRepository repository,
                           ObjectMapper objectMapper, Clock clock) {
        this.snapshotStore = snapshotStore;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Observation record(String chain, String txHash, String provider, FactType factType,
                               String rawResponseJson) {
        validateJson(rawResponseJson);

        Instant observedAt = clock.instant();
        Optional<String> s3Key = snapshotStore.store(chain, txHash, provider, factType, rawResponseJson,
                observedAt);

        Observation observation = Observation.create(chain, txHash, provider, factType, rawResponseJson,
                s3Key.orElse(null), observedAt);
        return repository.save(observation);
    }

    private void validateJson(String rawResponseJson) {
        try {
            objectMapper.readTree(rawResponseJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "rawResponseJson is not valid JSON - refusing to persist a non-verbatim payload", e);
        }
    }
}
