package com.themistra.notification.preference;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The single place every future consumer calls to record/refresh an account's known email (O1,
 * blocker for R1/R2/R6). {@code @Transactional} relies on Spring's own default {@code REQUIRED}
 * propagation - same rationale as T04's own {@code IdempotencyGuard} - so this method always joins
 * whatever transaction its caller already has open rather than committing independently.
 *
 * <p>Necessary as a public wrapper around the package-private
 * {@link ContactProjectionRepository}, not merely stylistic: a future
 * {@code consumer.AuthEventConsumer} (task 6, a different package) cannot call a package-private
 * repository directly.</p>
 *
 * <p>Takes {@code occurredAt} from the caller (the source event's own timestamp), not a freshly
 * injected {@code Clock} - the out-of-order guard in
 * {@link ContactProjectionRepository#upsertEmail} compares *event* time, not *processing* time, so
 * this class needs no {@code Clock} dependency at all.</p>
 *
 * <p>{@link #upsertEmail} returns whether the write was actually accepted (Kimi Phase 8
 * Finding #5) - {@code false} means the out-of-order guard rejected it as stale, not an error;
 * mirrors {@code IdempotencyGuard.recordIfNew}'s own boolean-result shape so a future caller can
 * log/observe suppressed-stale-write cases without this class needing a follow-up refactor.</p>
 */
@Service
public class ContactProjectionUpdater {

    private final ContactProjectionRepository repository;

    public ContactProjectionUpdater(ContactProjectionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean upsertEmail(UUID accountUuid, String email, Instant occurredAt) {
        return repository.upsertEmail(accountUuid, email, occurredAt) == 1;
    }

    /**
     * T11's own first read path onto this projection - {@code DeliveryOrchestrator} needs the
     * recipient's own real email address for {@code delivery_log.recipient} (EMAIL channel) and to
     * pass to {@code NotificationChannel.send}. Absent (no lifecycle/email event has been consumed
     * for this account yet) is a legitimate, expected case, not an error - the caller decides what
     * to do with an empty result. {@code readOnly = true} (Kimi Phase 8 Finding #8) documents the
     * read-only intent explicitly, rather than relying on Spring Data's own default.
     */
    @Transactional(readOnly = true)
    public Optional<String> findEmail(UUID accountUuid) {
        return repository.findById(accountUuid).map(ContactProjection::getEmail);
    }

    /**
     * T11 Kimi Phase 8 Finding #2: {@code display_name} is always {@code null} today - no data
     * source for it exists anywhere in {@code auth-service}'s own domain (verified directly since
     * T05, unchanged). This method completes the *wiring* so a future task that finally adds a
     * real display-name source needs no further plumbing change here or in
     * {@code DeliveryOrchestrator} - it does not, and cannot, fix the underlying missing-data
     * problem itself.
     */
    @Transactional(readOnly = true)
    public Optional<String> findDisplayName(UUID accountUuid) {
        return repository.findById(accountUuid).map(ContactProjection::getDisplayName);
    }
}
