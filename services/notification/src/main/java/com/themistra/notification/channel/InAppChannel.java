package com.themistra.notification.channel;

import com.themistra.notification.inapp.InappNotificationAppender;
import com.themistra.notification.template.TemplateRenderer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * The real, non-stub {@link NotificationChannel} for {@code IN_APP} - replaces
 * {@code NoOpInAppChannel} (T11, pre-authorized deletion by the established "temporary NoOp -> real
 * implementation" pattern this task mirrors from T06->T11 and T11->T12). Delegates all persistence
 * and live-push behavior to {@link InappNotificationAppender} - the single, sanctioned gateway into
 * the {@code inapp} module (Kimi Phase 8 Finding #1/#9, self-review Finding #1): this class never
 * imports {@code InappNotification}/{@code InappNotificationRepository}/{@code InappStreamRegistry}
 * directly, honoring L11 ("no feature module imports another feature module's entity") the same way
 * every other cross-module interaction in this codebase does (e.g.
 * {@code ContactProjectionUpdater.findEmail} returning a primitive, never its own entity).
 *
 * <p>{@code category}/{@code title}/{@code link} (Kimi Phase 3 Findings #1/#2/#3): {@code category}
 * is passed through by {@code DeliveryOrchestrator} (already resolved from its own
 * {@code NotificationMapping}) and validated here (Kimi Phase 8 Finding #8 - a future
 * {@code DeliveryOrchestrator} mapping bug should surface as a clear
 * {@code IllegalArgumentException}, not a cryptic {@code NOT NULL} constraint violation);
 * {@code title} is derived from {@code message.body()} (no other source exists - every seeded
 * {@code IN_APP} template has a {@code null} {@code subject}); {@code link} is always {@code null}
 * at launch - regex-extracting a URL from free rendered text was considered and rejected as
 * fragile; deep links render inline in {@code body} for now.</p>
 */
@Component
public class InAppChannel implements NotificationChannel {

    private static final int TITLE_MAX_LENGTH = 100;
    private static final String TITLE_TRUNCATION_SUFFIX = "…";

    private final InappNotificationAppender appender;
    private final Clock clock;

    public InAppChannel(InappNotificationAppender appender, Clock clock) {
        this.appender = appender;
        this.clock = clock;
    }

    @Override
    public String channel() {
        return "IN_APP";
    }

    @Override
    public void send(UUID accountUuid, String recipient, String category, TemplateRenderer.RenderedMessage message) {
        // Kimi Phase 8 Finding #8: category is trusted to come from DeliveryOrchestrator's own
        // already-resolved NotificationMapping, but validating it here means a future mapping bug
        // surfaces as a clear IllegalArgumentException, not a cryptic NOT NULL constraint violation
        // deep inside the repository.
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("an in-app notification cannot be sent without a category");
        }
        // Mirrors EmailChannel's own established precedent (T12, added after Kimi Phase 8 Finding
        // #4 there): TemplateRenderer's own contract makes body never-null/never-blank in practice,
        // but validating it here means a future violation of that contract surfaces as a clear
        // IllegalArgumentException, not a raw NullPointerException inside deriveTitle.
        if (message.body() == null || message.body().isBlank()) {
            throw new IllegalArgumentException("an in-app notification cannot be sent without a body");
        }

        appender.appendAndPush(accountUuid, category, deriveTitle(message.body()), message.body(), clock.instant());
    }

    private static String deriveTitle(String body) {
        if (body.length() <= TITLE_MAX_LENGTH) {
            return body;
        }
        return body.substring(0, TITLE_MAX_LENGTH) + TITLE_TRUNCATION_SUFFIX;
    }
}
