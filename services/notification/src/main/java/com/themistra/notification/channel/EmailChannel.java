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
    public void send(UUID accountUuid, String recipient, TemplateRenderer.RenderedMessage message) {
        validate(recipient, message);

        EmailMessage emailMessage = new EmailMessage(recipient, emailProperties.from(),
                message.subject(), message.body());
        String messageId = emailTransport.send(emailMessage);

        log.info("Email sent: accountUuid={}, recipient={}, messageId={}", accountUuid, recipient, messageId);
    }

    private void validate(String recipient, TemplateRenderer.RenderedMessage message) {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient must not be null/blank");
        }
        if (message.subject() == null) {
            throw new IllegalArgumentException("an email cannot be sent without a subject");
        }
    }
}
