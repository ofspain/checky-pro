package com.themistra.notification.channel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The capturing fake transport {@code agents.md} mandates ("Local dev runs against Docker Compose
 * (Postgres + Kafka) and a capturing fake transport - no real email is sent in CI"). Active when
 * {@code themistra.notification.email.transport=fake} - the default in {@code application.properties}
 * since this task, so a local run never reaches AWS unless explicitly configured otherwise.
 *
 * <p>Backed by a {@link CopyOnWriteArrayList} (Kimi Phase 3 Finding #2): {@code EmailChannel} is a
 * singleton called concurrently from Kafka listener container threads (T06's own
 * {@code concurrency: 2} precedent), so this capture list must be thread-safe.</p>
 */
@Component
@ConditionalOnProperty(prefix = "themistra.notification.email", name = "transport", havingValue = "fake")
public class FakeEmailTransport implements EmailTransport {

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public String send(EmailMessage message) {
        sent.add(message);
        return "fake-" + UUID.randomUUID();
    }

    public List<EmailMessage> sentMessages() {
        return Collections.unmodifiableList(sent);
    }

    public void clear() {
        sent.clear();
    }

    public Optional<EmailMessage> findByRecipient(String recipient) {
        EmailMessage match = null;
        for (EmailMessage candidate : sent) {
            if (candidate.to().equals(recipient)) {
                match = candidate;
            }
        }
        return Optional.ofNullable(match);
    }
}
