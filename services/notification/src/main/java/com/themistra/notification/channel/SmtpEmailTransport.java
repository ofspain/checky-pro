package com.themistra.notification.channel;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Local development only (2026-10-21): sends plain-text email over SMTP to the Mailpit container
 * of {@code infra/local}, where a developer reads verification and reset links in a browser.
 * Active only when {@code themistra.notification.email.transport=smtp}, and refuses to start
 * outside the {@code local} profile, so no real environment can send through it.
 *
 * <p>Like {@link SesEmailTransport}, a failure never carries the original exception or its
 * message: a rendered body can hold a token-bearing link (AC8).</p>
 */
@Component
@ConditionalOnProperty(prefix = "themistra.notification.email", name = "transport", havingValue = "smtp")
public class SmtpEmailTransport implements EmailTransport {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailTransport.class);

    private final JavaMailSender mailSender;

    public SmtpEmailTransport(JavaMailSender mailSender, Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            throw new IllegalStateException(
                    "themistra.notification.email.transport=smtp is for local development only; use ses");
        }
        this.mailSender = mailSender;
    }

    @Override
    public String send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
            helper.setFrom(message.from());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.body(), false);
            mailSender.send(mime);
            log.debug("SMTP accepted the send: accountUuid={}", message.accountUuid());
            return "smtp-" + UUID.randomUUID();
        } catch (MessagingException | RuntimeException e) {
            throw new EmailDeliveryException("email transport failure: " + e.getClass().getSimpleName());
        }
    }
}
