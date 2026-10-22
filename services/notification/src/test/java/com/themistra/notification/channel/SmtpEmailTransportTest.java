package com.themistra.notification.channel;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Local development email (2026-10-21): {@link SmtpEmailTransport}, against a mocked sender. */
class SmtpEmailTransportTest {

    private static final EmailMessage MESSAGE = new EmailMessage(UUID.randomUUID(), "merchant@example.com",
            "no-reply@checky.pro", "Verify your email", "Open http://localhost:5173/app/verify?token=secret-token");

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    private static MockEnvironment profile(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test
    void sendsThePlainTextMessageToTheRecipient() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        SmtpEmailTransport transport = new SmtpEmailTransport(mailSender, profile("local"));

        assertThat(transport.send(MESSAGE)).startsWith("smtp-");

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage mime = sent.getValue();
        assertThat(mime.getRecipients(Message.RecipientType.TO)).extracting(Object::toString)
                .containsExactly("merchant@example.com");
        assertThat(mime.getFrom()).extracting(Object::toString).containsExactly("no-reply@checky.pro");
        assertThat(mime.getSubject()).isEqualTo("Verify your email");
        assertThat(mime.getContent()).isEqualTo("Open http://localhost:5173/app/verify?token=secret-token");
    }

    @Test
    void refusesToStartOutsideTheLocalProfile() {
        assertThatThrownBy(() -> new SmtpEmailTransport(mailSender, profile("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local development only");
        assertThatThrownBy(() -> new SmtpEmailTransport(mailSender, profile()))
                .isInstanceOf(IllegalStateException.class);
    }

    /** AC8: the failure names only the exception type, never the message, which could echo the link. */
    @Test
    void aSendFailureCarriesNoMessageContentOrCause() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("could not send: secret-token")).when(mailSender).send(any(MimeMessage.class));
        SmtpEmailTransport transport = new SmtpEmailTransport(mailSender, profile("local"));

        assertThatThrownBy(() -> transport.send(MESSAGE))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("email transport failure: MailSendException")
                .hasNoCause();
    }
}
