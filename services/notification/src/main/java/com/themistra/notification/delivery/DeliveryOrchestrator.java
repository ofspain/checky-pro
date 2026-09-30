package com.themistra.notification.delivery;

import com.themistra.notification.channel.NotificationChannel;
import com.themistra.notification.common.SecretSafeLogging;
import com.themistra.notification.consumer.NotificationDispatcher;
import com.themistra.notification.preference.ContactProjectionUpdater;
import com.themistra.notification.preference.PreferenceResolver;
import com.themistra.notification.template.TemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The real {@link NotificationDispatcher} implementation - replaces
 * {@code NoOpNotificationDispatcher} (T06, pre-authorized deletion), wiring
 * {@link PreferenceResolver} (T08), {@link TemplateRenderer} (T09), and {@link SecretSafeLogging}
 * (T10) to a real caller for the first time: "resolve prefs → render → dispatch on each enabled
 * channel → append a {@code delivery_log} row per attempt with outcome" (L3, R10, R11).
 *
 * <p><strong>{@code dispatch} must never throw</strong> (AC9) - it runs inside
 * {@code AuthEventConsumer}'s own {@code @Transactional} boundary (T06), and an uncaught exception
 * here would roll back the idempotency record too, causing Kafka to redeliver a message whose real
 * problem (a broken template, a down email provider) redelivery cannot fix. Two levels of
 * {@code try/catch} enforce this: an outer one around the whole method body, and an inner one
 * around each individual channel's own attempt - so a single channel's own failure (including a
 * failure while writing its own {@code delivery_log} row) can never prevent the other channel's own
 * attempt, nor escape to this method's own boundary. Catches {@code Exception}, not
 * {@code Throwable} (Kimi Phase 3 Finding #9) - a genuine {@code Error} (e.g.
 * {@code OutOfMemoryError}) is allowed to propagate.</p>
 *
 * <p>{@code @Transactional} (default {@code REQUIRED}, Kimi Phase 3 Finding #1): joins
 * {@code AuthEventConsumer}'s own already-open transaction when called from there - a
 * {@code delivery_log} row this method writes rolls back together with everything else if the
 * external caller's own transaction later rolls back for an unrelated reason (e.g. a Kafka
 * redelivery scenario), avoiding a duplicate dispute-log entry for the same redelivered event.</p>
 */
@Component
public class DeliveryOrchestrator implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DeliveryOrchestrator.class);

    private static final List<String> LAUNCH_CHANNELS = List.of("EMAIL", "IN_APP");

    /**
     * VERBATIM from {@code design.md} §4c's own topic→template table plus the default-preferences
     * table's own category groupings. {@code user.registered} → {@code SECURITY} is T11's own
     * resolved reading (named in neither category's own parenthetical list; resolved by
     * elimination - clearly not payment-domain - and by safe-default outcome - a welcome message
     * must not silently default to {@code MARKETING}'s own {@code OFF}/{@code OFF}).
     * {@code auth.user.lifecycle(user.suspended) -> account.suspended} is deliberately excluded -
     * unreachable (only {@code AuthEventConsumer} ever calls this interface today, and it only ever
     * dispatches for {@code user.registered}) and unseeded (no such row exists in {@code V3}).
     */
    private record NotificationMapping(String emailTemplateName, String inAppTemplateName, String category) {
    }

    private static final Map<String, NotificationMapping> NOTIFICATION_MAPPINGS = Map.of(
            "verify_email", new NotificationMapping("email.verify", "user.verify", "SECURITY"),
            "password_reset", new NotificationMapping("email.password_reset", "user.password_reset", "SECURITY"),
            "user.registered", new NotificationMapping("user.welcome", "user.welcome", "SECURITY"),
            "invoice.created", new NotificationMapping("invoice.created", "invoice.created", "PAYMENT"),
            "payment.seen", new NotificationMapping("payment.seen", "payment.seen", "PAYMENT"),
            "payment.finalized", new NotificationMapping("payment.finalized", "payment.finalized", "PAYMENT"),
            "receipt.issued", new NotificationMapping("receipt.issued", "receipt.issued", "PAYMENT"));

    private final PreferenceResolver preferenceResolver;
    private final TemplateRenderer templateRenderer;
    private final ContactProjectionUpdater contactProjectionUpdater;
    private final DeliveryLogRepository deliveryLogRepository;
    private final Clock clock;
    private final Map<String, NotificationChannel> channelsByName;

    public DeliveryOrchestrator(PreferenceResolver preferenceResolver, TemplateRenderer templateRenderer,
                                 ContactProjectionUpdater contactProjectionUpdater,
                                 DeliveryLogRepository deliveryLogRepository, Clock clock,
                                 List<NotificationChannel> channels) {
        this.preferenceResolver = preferenceResolver;
        this.templateRenderer = templateRenderer;
        this.contactProjectionUpdater = contactProjectionUpdater;
        this.deliveryLogRepository = deliveryLogRepository;
        this.clock = clock;
        this.channelsByName = channels.stream()
                .collect(Collectors.toMap(NotificationChannel::channel, channel -> channel));
    }

    @Override
    @Transactional
    public void dispatch(UUID accountUuid, String notificationKind, Map<String, String> eventData) {
        try {
            NotificationMapping mapping = notificationKind == null ? null : NOTIFICATION_MAPPINGS.get(notificationKind);
            if (mapping == null) {
                log.debug("No notification mapping for kind={}, skipping dispatch", notificationKind);
                return;
            }

            String sourceEventKey = eventData.get("sourceEventKey");
            String email = contactProjectionUpdater.findEmail(accountUuid).orElse(null);

            for (String channel : LAUNCH_CHANNELS) {
                dispatchOneChannel(accountUuid, email, sourceEventKey, eventData, mapping, channel);
            }
        } catch (Exception e) {
            log.error("Unexpected failure in dispatch for accountUuid={}, notificationKind={}",
                    accountUuid, notificationKind, e);
        }
    }

    private void dispatchOneChannel(UUID accountUuid, String email, String sourceEventKey,
                                     Map<String, String> eventData, NotificationMapping mapping, String channel) {
        try {
            String templateName = "EMAIL".equals(channel) ? mapping.emailTemplateName() : mapping.inAppTemplateName();
            if (templateName == null) {
                return;
            }
            String recipient = "EMAIL".equals(channel) ? email : accountUuid.toString();

            boolean enabled = preferenceResolver.resolve(accountUuid, mapping.category(), channel);
            if (!enabled) {
                save(accountUuid, recipient, channel, sourceEventKey, null, null, "SUPPRESSED", null);
                return;
            }

            if ("EMAIL".equals(channel) && email == null) {
                save(accountUuid, null, channel, sourceEventKey, templateName, null,
                        "FAILED", "no recipient email on file");
                return;
            }

            TemplateRenderer.RenderedMessage message;
            try {
                message = templateRenderer.render(templateName, channel, eventData);
            } catch (Exception e) {
                save(accountUuid, recipient, channel, sourceEventKey, templateName, null, "FAILED", e.getMessage());
                return;
            }

            NotificationChannel channelBean = channelsByName.get(channel);
            if (channelBean == null) {
                save(accountUuid, recipient, channel, sourceEventKey, templateName, message.version(),
                        "FAILED", "no channel bean registered for " + channel);
                return;
            }

            try {
                channelBean.send(accountUuid, recipient, message);
                save(accountUuid, recipient, channel, sourceEventKey, templateName, message.version(), "SENT", null);
            } catch (Exception e) {
                save(accountUuid, recipient, channel, sourceEventKey, templateName, message.version(),
                        "FAILED", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected failure dispatching channel={} for accountUuid={}", channel, accountUuid, e);
        }
    }

    private void save(UUID accountUuid, String recipient, String channel, String sourceEventKey,
                       String templateName, Integer templateVersion, String outcome, String errorDetail) {
        String redactedErrorDetail = errorDetail == null ? null : SecretSafeLogging.redact(errorDetail);
        deliveryLogRepository.save(new DeliveryLog(accountUuid, recipient, channel, sourceEventKey,
                templateName, templateVersion, outcome, redactedErrorDetail, clock.instant()));
    }
}
