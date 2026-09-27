package com.themistra.notification.consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * The single place every future consumer calls to dedupe on a source event's stable key (L1,
 * R7/R8). {@code @Transactional} relies on Spring's own default {@code REQUIRED} propagation -
 * deliberately never {@code REQUIRES_NEW}/{@code NOT_SUPPORTED} - so this method always joins
 * whatever transaction its caller already has open rather than committing independently; a future
 * consumer can therefore call this and then append to {@code delivery_log} in the same transaction
 * (L1's own requirement) without this class needing to know {@code delivery_log} exists.
 *
 * <p>{@link #recordIfNew} uses {@link ProcessedEventRepository#insertIfNew} - a native
 * {@code INSERT ... ON CONFLICT (event_key) DO NOTHING} - rather than a JPA
 * {@code existsById}-then-{@code save}. Two real, empirically-discovered problems ruled out the
 * more "ordinary" approaches during Phase 6 (kept here so nobody reintroduces them):</p>
 * <ul>
 *   <li>{@code existsById} then {@code save} has a genuine TOCTOU race under concurrent duplicate
 *   calls (Kimi Phase 3 Finding #1) - two concurrent transactions can both observe "not yet
 *   processed" before either commits.</li>
 *   <li>Catching the resulting constraint-violation exception around a plain {@code saveAndFlush}
 *   does *not* actually fix this with the default {@code REQUIRED} propagation: Hibernate marks the
 *   physical transaction rollback-only the instant the flush fails, regardless of whether
 *   application code catches the translated exception, so Spring's own transactional proxy throws
 *   {@code UnexpectedRollbackException} back to the caller on return anyway. Switching to
 *   {@code Propagation.NESTED} (a real {@code SAVEPOINT}, which would isolate just the failed
 *   insert) was tried next and also failed empirically -
 *   {@code JpaTransactionManager}/{@code HibernateJpaDialect} do not support savepoints
 *   ({@code NestedTransactionNotSupportedException}). Both were caught by a real concurrent-call
 *   test written during Phase 6 (not committed - superseded by this design) before either was
 *   finalized.</li>
 * </ul>
 * <p>{@code ON CONFLICT DO NOTHING} sidesteps the whole problem: Postgres never raises a constraint
 * violation for the conflicting row in the first place, so there is nothing to catch and nothing
 * that can mark any transaction rollback-only. The returned row count (1 = inserted, 0 = duplicate)
 * is the only signal this method needs.</p>
 */
@Service
public class IdempotencyGuard {

    private final ProcessedEventRepository repository;
    private final Clock clock;

    public IdempotencyGuard(ProcessedEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public boolean recordIfNew(String eventKey, String eventType) {
        int rowsInserted = repository.insertIfNew(eventKey, eventType, clock.instant());
        return rowsInserted == 1;
    }
}
