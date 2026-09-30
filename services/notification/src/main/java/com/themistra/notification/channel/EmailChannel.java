package com.themistra.notification.channel;

import com.themistra.notification.common.config.EmailProperties;
import com.themistra.notification.template.TemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The real, non-stub {@link NotificationChannel} for {@code EMAIL} - replaces
 * {@code NoOpEmailChannel} (T11, pre-authorized deletion). Delegates the actual send to an
 * {@link EmailTransport} (real SES vs. capturing fake, selected by
 * {@code themistra.notification.email.transport} - never both active at once, Kimi Phase 3
 * Finding #9), so this class itself is unaware whether a real email goes out.
 *
 * <p>Does not catch anything {@link EmailTransport#send} throws (AC4, L11) -
 * {@code DeliveryOrchestrator}'s own already-implemented outcome-recording {@code try/catch} (T11,
 * unchanged) is the single place a channel's own failure becomes a {@code FAILED}
 * {@code delivery_log} row; duplicating that responsibility here would violate module
 * boundaries.</p>
 *
 * <p>Its own success log line names only {@code accountUuid}/{@code messageId} - never
 * {@code recipient} (Kimi Phase 8 Finding #2: an email address is PII, and {@code agents.md}'s own
 * observability rule forbids logging PII, not only secrets/tokens).</p>
 */
@Component
public class EmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

    private final EmailTransport emailTransport;
    private final EmailProperties emailProperties;

    public EmailChannel(EmailTransport emailTransport, EmailProperties emailProperties) {
        this.emailTransport = emailTransport;
        this.emailProperties = emailProperties;
    }

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public void send(UUID accountUuid, String recipient, String category, TemplateRenderer.RenderedMessage message) {
        // category (T13, Kimi Phase 3 Finding #1) is accepted per NotificationChannel's own
        // interface contract but never read here - SES has no equivalent concept.
        validate(recipient, message);

        EmailMessage emailMessage = new EmailMessage(accountUuid, recipient, emailProperties.from(),
                message.subject(), message.body());
        String messageId = emailTransport.send(emailMessage);

        // Kimi Phase 8 Finding #2: recipient (an email address) is PII - agents.md's own
        // observability rule forbids logging it. accountUuid is already a sufficient correlation
        // key back to the real recipient, via contact_projection, for anyone who genuinely needs it.
        log.info("Email sent: accountUuid={}, messageId={}", accountUuid, messageId);
    }

    private void validate(String recipient, TemplateRenderer.RenderedMessage message) {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient must not be null/blank");
        }
        if (message.subject() == null) {
            throw new IllegalArgumentException("an email cannot be sent without a subject");
        }
        // Kimi Phase 8 Finding #4 / self-review Finding #4: TemplateRenderer's own contract makes
        // body never-null in practice, but validating it here means a future violation of that
        // contract surfaces as a clear IllegalArgumentException at this boundary, not a cryptic
        // builder failure deep inside SesEmailTransport.
        if (message.body() == null || message.body().isBlank()) {
            throw new IllegalArgumentException("an email cannot be sent without a body");
        }
    }
}
