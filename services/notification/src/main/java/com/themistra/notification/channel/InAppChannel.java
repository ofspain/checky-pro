package com.themistra.notification.channel;

import com.themistra.notification.inapp.InappNotification;
import com.themistra.notification.inapp.InappNotificationRepository;
import com.themistra.notification.inapp.InappStreamRegistry;
import com.themistra.notification.template.TemplateRenderer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.UUID;

/**
 * The real, non-stub {@link NotificationChannel} for {@code IN_APP} - replaces
 * {@code NoOpInAppChannel} (T11, pre-authorized deletion by the established "temporary NoOp -> real
 * implementation" pattern this task mirrors from T06->T11 and T11->T12). Persists every
 * notification, then makes a best-effort attempt to push it live via {@link InappStreamRegistry}.
 *
 * <p>{@code send} is {@code @Transactional} (default {@code REQUIRED}, Kimi Phase 3 Finding #6) -
 * joins {@code DeliveryOrchestrator.dispatch}'s own already-open transaction (T11), mirroring
 * {@code ContactProjectionUpdater.upsertEmail}'s own established "explicit {@code @Transactional}
 * on every write-path method" precedent (T05).</p>
 *
 * <p>The live push is deferred until <strong>after</strong> that transaction commits
 * ({@link TransactionSynchronizationManager}), not fired immediately after {@code save} - resolved
 * more strongly than Kimi's own Phase 3 Finding #6 literally asked (documenting the race, not
 * eliminating it): a client can never receive a live push for a row
 * {@code InappReadController}'s own unread endpoint can't yet see, since both now happen on
 * opposite sides of the same commit. If no transaction is active on the calling thread (defensive -
 * not expected, given the only real caller is {@code DeliveryOrchestrator}), the push fires
 * immediately instead of being silently dropped.</p>
 *
 * <p>{@code category}/{@code title}/{@code link} (Kimi Phase 3 Findings #1/#2/#3): {@code category}
 * is passed through by {@code DeliveryOrchestrator} (already resolved from its own
 * {@code NotificationMapping}); {@code title} is derived from {@code message.body()} (no other
 * source exists - every seeded {@code IN_APP} template has a {@code null} {@code subject}); {@code
 * link} is always {@code null} at launch - regex-extracting a URL from free rendered text was
 * considered and rejected as fragile; deep links render inline in {@code body} for now.</p>
 */
@Component
public class InAppChannel implements NotificationChannel {

    private static final int TITLE_MAX_LENGTH = 100;
    private static final String TITLE_TRUNCATION_SUFFIX = "…";

    private final InappNotificationRepository repository;
    private final InappStreamRegistry streamRegistry;
    private final Clock clock;

    public InAppChannel(InappNotificationRepository repository, InappStreamRegistry streamRegistry, Clock clock) {
        this.repository = repository;
        this.streamRegistry = streamRegistry;
        this.clock = clock;
    }

    @Override
    public String channel() {
        return "IN_APP";
    }

    @Override
    @Transactional
    public void send(UUID accountUuid, String recipient, String category, TemplateRenderer.RenderedMessage message) {
        // Mirrors EmailChannel's own established precedent (T12, added after Kimi Phase 8 Finding
        // #4 there): TemplateRenderer's own contract makes body never-null/never-blank in practice,
        // but validating it here means a future violation of that contract surfaces as a clear
        // IllegalArgumentException, not a raw NullPointerException inside deriveTitle.
        if (message.body() == null || message.body().isBlank()) {
            throw new IllegalArgumentException("an in-app notification cannot be sent without a body");
        }

        InappNotification notification = new InappNotification(
                UUID.randomUUID(), accountUuid, category, deriveTitle(message.body()),
                message.body(), null, clock.instant());
        repository.save(notification);

        pushAfterCommit(accountUuid, notification.toView());
    }

    private static String deriveTitle(String body) {
        if (body.length() <= TITLE_MAX_LENGTH) {
            return body;
        }
        return body.substring(0, TITLE_MAX_LENGTH) + TITLE_TRUNCATION_SUFFIX;
    }

    private void pushAfterCommit(UUID accountUuid, InappNotification.View view) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    streamRegistry.push(accountUuid, "notification", view);
                }
            });
        } else {
            streamRegistry.push(accountUuid, "notification", view);
        }
    }
}
