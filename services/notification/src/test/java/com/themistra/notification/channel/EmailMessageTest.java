package com.themistra.notification.channel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain JUnit, no Spring context, no Docker.
 */
class EmailMessageTest {

    @Test
    void toStringExcludesTheRecipientAddressSubjectAndBody() {
        UUID accountUuid = UUID.randomUUID();
        EmailMessage message = new EmailMessage(accountUuid, "victim@example.com", "no-reply@checky.pro",
                "Reset your password", "Visit https://example.com/reset?token=raw-secret-token to reset");

        String rendered = message.toString();

        assertThat(rendered).doesNotContain("victim@example.com");
        assertThat(rendered).doesNotContain("Reset your password");
        assertThat(rendered).doesNotContain("raw-secret-token");
        assertThat(rendered).contains(accountUuid.toString());
        assertThat(rendered).contains("no-reply@checky.pro");
    }

    @Test
    void toStringReportsSubjectAndBodyLengthsNotContent() {
        EmailMessage message = new EmailMessage(UUID.randomUUID(), "a@example.com", "no-reply@checky.pro",
                "12345", "1234567890");

        assertThat(message.toString()).contains("subjectLength=5").contains("bodyLength=10");
    }

    @Test
    void toStringHandlesNullSubjectAndBodyAsZeroLength() {
        EmailMessage message = new EmailMessage(UUID.randomUUID(), "a@example.com", "no-reply@checky.pro",
                null, null);

        assertThat(message.toString()).contains("subjectLength=0").contains("bodyLength=0");
    }
}
