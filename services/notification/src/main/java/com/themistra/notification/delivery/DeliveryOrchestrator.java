package com.themistra.notification.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.notification.channel.NotificationChannel;
import com.themistra.notification.common.SecretSafeLogging;
import com.themistra.notification.common.config.RetryProperties;
import com.themistra.notification.consumer.NotificationDispatcher;
import com.themistra.notification.preference.ContactProjectionUpdater;
import com.themistra.notification.preference.PreferenceResolver;
import com.themistra.notification.template.TemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
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
 *
 * <p>Also resolves {@code displayName} via {@link ContactProjectionUpdater#findDisplayName} and
 * merges it into the render data passed to {@link TemplateRenderer#render} (Kimi Phase 8
 * Finding #2) - {@code display_name} is always {@code null} today (no data source exists anywhere
 * in {@code auth-service}'s own domain), so this only completes the wiring for a future data
 * source, it does not fix the underlying missing-data problem.</p>
 *
 * <p>If a failure occurs before the per-channel loop even starts (e.g.
 * {@code contactProjectionUpdater.findEmail} itself throwing), the outer catch now records a
 * best-effort {@code FAILED} row for each of {@link #LAUNCH_CHANNELS} rather than none at all
 * (Kimi Phase 8 Finding #4), so R11's own "every delivery attempt" guarantee still holds for
 * pre-loop failures. {@code dispatchOneChannel}'s own outer catch carries the identical fallback
 * (Kimi Phase 11 Gap #1/#5) - a failure not already converted to a row above (e.g.
 * {@code preferenceResolver.resolve} itself throwing, or one of this method's own {@code save}
 * calls throwing) still leaves a best-effort record for that one channel. Both fallback saves have
 * their own inner safety net in case {@code save} itself throws (e.g. a genuinely null
 * {@code sourceEventKey}, Finding #6) - falling back to a synthetic key and, failing that, a
 * log-only record.</p>
 *
 * <p><strong>T14 (bounded retry, L7, R12/R13):</strong> {@link #attemptSend} is the one shared
 * classification helper both the original dispatch path and {@link #replay} call - a
 * {@code channelBean.send} failure is permanent ({@code IllegalArgumentException} - deterministic,
 * retrying changes nothing) or transient (everything else). A transient failure below
 * {@code retryProperties.maxAttempts()} schedules a bounded-backoff retry; one that would exceed it
 * writes a terminal {@code DEAD_LETTERED} row instead - evaluated on every attempt, including the
 * very first, so a {@code maxAttempts=1} configuration dead-letters immediately rather than
 * scheduling a retry it would instantly exhaust. {@link #replay} re-checks preferences (a channel
 * disabled since the original attempt is honored, not overridden) and re-renders, since nothing
 * durable stores the originally-rendered message.</p>
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

    /** T14's own 5-outcome classification result, returned by {@link #attemptSend} and
     * {@link #replay} - {@code RetryScheduler} decides what to do with its own {@code DeliveryRetry}
     * row purely from this value, never by re-deriving it from the {@code delivery_log} row. */
    enum DeliveryOutcome {
        SENT, SUPPRESSED, PERMANENT_FAILURE, TRANSIENT_FAILURE, TRANSIENT_EXHAUSTED
    }

    private final PreferenceResolver preferenceResolver;
    private final TemplateRenderer templateRenderer;
    private final ContactProjectionUpdater contactProjectionUpdater;
    private final DeliveryLogRepository deliveryLogRepository;
    private final DeliveryRetryRepository deliveryRetryRepository;
    private final RetryProperties retryProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, NotificationChannel> channelsByName;

    public DeliveryOrchestrator(PreferenceResolver preferenceResolver, TemplateRenderer templateRenderer,
                                 ContactProjectionUpdater contactProjectionUpdater,
                                 DeliveryLogRepository deliveryLogRepository,
                                 DeliveryRetryRepository deliveryRetryRepository,
                                 RetryProperties retryProperties, ObjectMapper objectMapper, Clock clock,
                                 List<NotificationChannel> channels) {
        this.preferenceResolver = preferenceResolver;
        this.templateRenderer = templateRenderer;
        this.contactProjectionUpdater = contactProjectionUpdater;
        this.deliveryLogRepository = deliveryLogRepository;
        this.deliveryRetryRepository = deliveryRetryRepository;
        this.retryProperties = retryProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.channelsByName = channels.stream()
                .collect(Collectors.toMap(NotificationChannel::channel, channel -> channel));
    }

    @Override
    @Transactional
    public void dispatch(UUID accountUuid, String notificationKind, Map<String, String> eventData) {
        String sourceEventKey = eventData == null ? null : eventData.get("sourceEventKey");
        try {
            NotificationMapping mapping = notificationKind == null ? null : NOTIFICATION_MAPPINGS.get(notificationKind);
            if (mapping == null) {
                log.debug("No notification mapping for kind={}, skipping dispatch", notificationKind);
                return;
            }

            String email = contactProjectionUpdater.findEmail(accountUuid).orElse(null);
            // Kimi Phase 8 Finding #2: display_name is always null today (no data source exists
            // anywhere in auth's own domain, T05's own already-disclosed limitation) - wired
            // through regardless so a future task that finally populates it needs no further
            // plumbing change here.
            String displayName = contactProjectionUpdater.findDisplayName(accountUuid).orElse(null);
            Map<String, String> renderData = new HashMap<>(eventData == null ? Map.of() : eventData);
            if (displayName != null) {
                renderData.put("displayName", displayName);
            }

            for (String channel : LAUNCH_CHANNELS) {
                dispatchOneChannel(accountUuid, email, sourceEventKey, notificationKind, renderData, mapping, channel);
            }
        } catch (Exception e) {
            // Kimi Phase 8 Finding #4: a failure before any channel is attempted (e.g. findEmail
            // itself throwing) would otherwise leave R11's own "every attempt" guarantee unmet -
            // record one best-effort row per launch channel rather than none at all. This save
            // attempt has its own inner safety net, since sourceEventKey could itself be the
            // reason the outer block failed (e.g. genuinely null - Finding #6). A pre-loop failure
            // like this one is never classified transient/permanent (T14) - it never reached a real
            // channel send attempt, so there is nothing a retry would be replaying.
            log.error("Unexpected failure in dispatch for accountUuid={}, notificationKind={}",
                    accountUuid, notificationKind, e);
            for (String channel : LAUNCH_CHANNELS) {
                try {
                    save(accountUuid, null, channel, sourceEventKey == null ? "unknown:" + accountUuid : sourceEventKey,
                            null, null, (short) 1, "FAILED", e.getMessage());
                } catch (Exception saveFailure) {
                    log.error("Unable to record fallback FAILED row for accountUuid={}, channel={}",
                            accountUuid, channel, saveFailure);
                }
            }
        }
    }

    private void dispatchOneChannel(UUID accountUuid, String email, String sourceEventKey, String notificationKind,
                                     Map<String, String> eventData, NotificationMapping mapping, String channel) {
        try {
            String templateName = "EMAIL".equals(channel) ? mapping.emailTemplateName() : mapping.inAppTemplateName();
            if (templateName == null) {
                return;
            }
            String recipient = "EMAIL".equals(channel) ? email : accountUuid.toString();

            boolean enabled = preferenceResolver.resolve(accountUuid, mapping.category(), channel);
            if (!enabled) {
                save(accountUuid, recipient, channel, sourceEventKey, null, null, (short) 1, "SUPPRESSED", null);
                return;
            }

            if ("EMAIL".equals(channel) && email == null) {
                save(accountUuid, null, channel, sourceEventKey, templateName, null,
                        (short) 1, "FAILED", "no recipient email on file");
                return;
            }

            TemplateRenderer.RenderedMessage message;
            try {
                message = templateRenderer.render(templateName, channel, eventData);
            } catch (Exception e) {
                save(accountUuid, recipient, channel, sourceEventKey, templateName, null, (short) 1, "FAILED", e.getMessage());
                return;
            }

            NotificationChannel channelBean = channelsByName.get(channel);
            if (channelBean == null) {
                save(accountUuid, recipient, channel, sourceEventKey, templateName, message.version(),
                        (short) 1, "FAILED", "no channel bean registered for " + channel);
                return;
            }

            DeliveryOutcome outcome = attemptSend(accountUuid, recipient, channel, sourceEventKey, templateName,
                    message.version(), mapping.category(), channelBean, message, (short) 1);
            if (outcome == DeliveryOutcome.TRANSIENT_FAILURE) {
                scheduleFirstRetry(accountUuid, channel, sourceEventKey, notificationKind, eventData);
            }
        } catch (Exception e) {
            // Kimi Phase 11 Gap #1/#5: a failure not already converted to a FAILED row above (e.g.
            // preferenceResolver.resolve itself throwing, or one of this method's own save() calls
            // throwing - most plausibly a NOT NULL violation from a missing sourceEventKey,
            // Finding #6) must still leave a best-effort record, mirroring dispatch's own outer
            // fallback (Finding #4) exactly, including its synthetic-key handling and inner safety
            // net. Never classified transient/permanent (T14) - it never reached a real channel send.
            log.error("Unexpected failure dispatching channel={} for accountUuid={}", channel, accountUuid, e);
            try {
                save(accountUuid, null, channel, sourceEventKey == null ? "unknown:" + accountUuid : sourceEventKey,
                        null, null, (short) 1, "FAILED", e.getMessage());
            } catch (Exception saveFailure) {
                log.error("Unable to record fallback FAILED row for accountUuid={}, channel={}",
                        accountUuid, channel, saveFailure);
            }
        }
    }

    /**
     * T14's own one shared classification helper - the sole place a {@code channelBean.send}
     * failure is turned into a {@link DeliveryLog} row and a {@link DeliveryOutcome}. Called by both
     * the original dispatch path ({@code attemptNumber=1}) and {@link #replay}. Never throws -
     * {@code IllegalArgumentException} is permanent (deterministic validation failure, identical on
     * every retry); any other exception is transient, bounded by {@code retryProperties.maxAttempts()}
     * evaluated against {@code attemptNumber} itself, so even the very first attempt dead-letters
     * immediately under a {@code maxAttempts=1} configuration rather than scheduling a retry that
     * would instantly exhaust.
     */
    private DeliveryOutcome attemptSend(UUID accountUuid, String recipient, String channel, String sourceEventKey,
                                         String templateName, Integer templateVersion, String category,
                                         NotificationChannel channelBean, TemplateRenderer.RenderedMessage message,
                                         short attemptNumber) {
        try {
            channelBean.send(accountUuid, recipient, category, message);
            save(accountUuid, recipient, channel, sourceEventKey, templateName, templateVersion,
                    attemptNumber, "SENT", null);
            return DeliveryOutcome.SENT;
        } catch (IllegalArgumentException e) {
            save(accountUuid, recipient, channel, sourceEventKey, templateName, templateVersion,
                    attemptNumber, "FAILED", e.getMessage());
            return DeliveryOutcome.PERMANENT_FAILURE;
        } catch (Exception e) {
            int nextAttemptNumber = attemptNumber + 1;
            if (nextAttemptNumber > retryProperties.maxAttempts()) {
                save(accountUuid, recipient, channel, sourceEventKey, templateName, templateVersion,
                        attemptNumber, "DEAD_LETTERED", e.getMessage());
                return DeliveryOutcome.TRANSIENT_EXHAUSTED;
            }
            save(accountUuid, recipient, channel, sourceEventKey, templateName, templateVersion,
                    attemptNumber, "FAILED", e.getMessage());
            return DeliveryOutcome.TRANSIENT_FAILURE;
        }
    }

    /** T14: the very first retry row for a channel/event, inserted only when {@link #attemptSend}
     * (called with {@code attemptNumber=1}) returns {@code TRANSIENT_FAILURE}. Best-effort - a
     * serialization failure here is logged, never thrown; the {@code FAILED} {@link DeliveryLog} row
     * {@code attemptSend} already wrote stands on its own either way (R11's guarantee is unaffected). */
    private void scheduleFirstRetry(UUID accountUuid, String channel, String sourceEventKey,
                                     String notificationKind, Map<String, String> eventData) {
        try {
            String eventDataJson = objectMapper.writeValueAsString(eventData == null ? Map.of() : eventData);
            Instant nextAttemptAt = clock.instant().plusSeconds(retryProperties.initialBackoffSeconds());
            deliveryRetryRepository.save(new DeliveryRetry(sourceEventKey, accountUuid, channel, notificationKind,
                    eventDataJson, (short) 1, nextAttemptAt, clock.instant()));
        } catch (Exception e) {
            log.error("Unable to schedule a retry for accountUuid={}, channel={}, sourceEventKey={}",
                    accountUuid, channel, sourceEventKey, e);
        }
    }

    /**
     * T14: replays exactly one channel's own delivery attempt, called only by {@code RetryScheduler}
     * for a due {@code DeliveryRetry} row. Re-resolves the recipient and re-checks preferences
     * (Kimi Phase 3 Finding #3) - state may have changed since the original attempt - then re-renders
     * (nothing durable stores the originally-rendered message) before delegating to the same
     * {@link #attemptSend} the original dispatch path uses. Every early-return guard here writes its
     * own terminal {@code delivery_log} row classified {@code PERMANENT_FAILURE}: a guard failure
     * (unknown mapping, still-missing recipient, a broken template, a missing channel bean) is never
     * itself "a channel delivery fails" in R12's own sense - out of this task's retry scope, mirroring
     * the original dispatch path's own identical, unretried guard failures.
     */
    DeliveryOutcome replay(UUID accountUuid, String channel, String notificationKind, String sourceEventKey,
                           Map<String, String> eventData, short attemptsAlreadyMade) {
        short attemptNumber = (short) (attemptsAlreadyMade + 1);
        NotificationMapping mapping = NOTIFICATION_MAPPINGS.get(notificationKind);
        if (mapping == null) {
            save(accountUuid, null, channel, sourceEventKey, null, null, attemptNumber, "FAILED",
                    "unknown notification kind on replay: " + notificationKind);
            return DeliveryOutcome.PERMANENT_FAILURE;
        }

        String templateName = "EMAIL".equals(channel) ? mapping.emailTemplateName() : mapping.inAppTemplateName();
        String recipient = "EMAIL".equals(channel)
                ? contactProjectionUpdater.findEmail(accountUuid).orElse(null)
                : accountUuid.toString();

        boolean enabled = preferenceResolver.resolve(accountUuid, mapping.category(), channel);
        if (!enabled) {
            save(accountUuid, recipient, channel, sourceEventKey, null, null, attemptNumber, "SUPPRESSED", null);
            return DeliveryOutcome.SUPPRESSED;
        }

        if ("EMAIL".equals(channel) && recipient == null) {
            save(accountUuid, null, channel, sourceEventKey, templateName, null, attemptNumber, "FAILED",
                    "no recipient email on file");
            return DeliveryOutcome.PERMANENT_FAILURE;
        }

        TemplateRenderer.RenderedMessage message;
        try {
            message = templateRenderer.render(templateName, channel, eventData);
        } catch (Exception e) {
            save(accountUuid, recipient, channel, sourceEventKey, templateName, null, attemptNumber, "FAILED",
                    e.getMessage());
            return DeliveryOutcome.PERMANENT_FAILURE;
        }

        NotificationChannel channelBean = channelsByName.get(channel);
        if (channelBean == null) {
            save(accountUuid, recipient, channel, sourceEventKey, templateName, message.version(), attemptNumber,
                    "FAILED", "no channel bean registered for " + channel);
            return DeliveryOutcome.PERMANENT_FAILURE;
        }

        return attemptSend(accountUuid, recipient, channel, sourceEventKey, templateName, message.version(),
                mapping.category(), channelBean, message, attemptNumber);
    }

    /** T14 (Kimi Phase 3 Finding #7): {@code RetryScheduler}'s own poison-pill path - a
     * {@code delivery_retry} row whose {@code event_data_json} cannot be deserialized can never be
     * replayed, so it is dead-lettered directly rather than ever calling {@link #replay}. Kept here,
     * not on {@code RetryScheduler}, so the one {@link SecretSafeLogging}-redacting {@code save}
     * helper is never duplicated. */
    void recordUnrecoverableFailure(UUID accountUuid, String channel, String sourceEventKey, short attempt,
                                     String detail) {
        save(accountUuid, null, channel, sourceEventKey, null, null, attempt, "DEAD_LETTERED", detail);
    }

    private void save(UUID accountUuid, String recipient, String channel, String sourceEventKey,
                       String templateName, Integer templateVersion, short attempt, String outcome,
                       String errorDetail) {
        String redactedErrorDetail = errorDetail == null ? null : SecretSafeLogging.redact(errorDetail);
        deliveryLogRepository.save(new DeliveryLog(accountUuid, recipient, channel, sourceEventKey,
                templateName, templateVersion, attempt, outcome, redactedErrorDetail, clock.instant()));
    }
}
