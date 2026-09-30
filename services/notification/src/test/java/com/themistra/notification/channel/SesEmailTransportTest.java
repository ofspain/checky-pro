package com.themistra.notification.channel;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentCaptor.forClass;

/**
 * Mocked {@link SesV2Client}, no Spring context, no Docker, no real AWS credential/network access.
 */
class SesEmailTransportTest {

    private final SesV2Client sesV2Client = mock(SesV2Client.class);
    private final SesEmailTransport transport = new SesEmailTransport(sesV2Client);

    private static EmailMessage message() {
        return new EmailMessage(UUID.randomUUID(), "recipient@example.com", "no-reply@checky.pro",
                "Verify your email", "Visit https://example.com/verify?token=raw-token to verify");
    }

    @Test
    void sendBuildsTheCorrectRequestAndReturnsTheMessageId() {
        when(sesV2Client.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("mid-123").build());

        String messageId = transport.send(message());

        assertThat(messageId).isEqualTo("mid-123");
        var captor = forClass(SendEmailRequest.class);
        verify(sesV2Client).sendEmail(captor.capture());
        SendEmailRequest request = captor.getValue();
        assertThat(request.fromEmailAddress()).isEqualTo("no-reply@checky.pro");
        assertThat(request.destination().toAddresses()).containsExactly("recipient@example.com");
        assertThat(request.content().simple().subject().data()).isEqualTo("Verify your email");
        assertThat(request.content().simple().body().text().data())
                .isEqualTo("Visit https://example.com/verify?token=raw-token to verify");
        assertThat(request.content().simple().body().html()).isNull();
    }

    /** AC8: converts a real AwsServiceException to a sanitized EmailDeliveryException using only
     * its own trusted, structured awsErrorDetails() fields - never its own untrusted top-level
     * message()/toString(), which could (in a real scenario) echo back the request that contained a
     * genuine token-bearing link. The fixture deliberately puts a token-shaped string ONLY in the
     * untrusted top-level message, proving our own code never reads it. */
    @Test
    void awsServiceExceptionIsSanitizedToOnlyItsOwnTrustedErrorDetailsWithNoCause() {
        AwsServiceException awsException = AwsServiceException.builder()
                .message("RAW-REQUEST-DUMP-token=abc123-must-never-appear")
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode("MessageRejected")
                        .errorMessage("Email address is not verified.")
                        .build())
                .build();
        when(sesV2Client.sendEmail(any(SendEmailRequest.class))).thenThrow(awsException);

        assertThatThrownBy(() -> transport.send(message()))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("MessageRejected: Email address is not verified.")
                .hasNoCause()
                .satisfies(e -> assertThat(e.getMessage())
                        .doesNotContain("RAW-REQUEST-DUMP").doesNotContain("abc123"));
    }

    /** A generic, non-AWS RuntimeException (e.g. a client-side failure) is also converted safely -
     * its own raw message is never trusted, since it could (unlike AWS's own structured error
     * fields) embed arbitrary content. */
    @Test
    void aGenericRuntimeExceptionIsConvertedToASafeGenericMessageWithNoCause() {
        when(sesV2Client.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(new RuntimeException("RAW-CLIENT-SIDE-DUMP-token=xyz789-must-never-appear"));

        assertThatThrownBy(() -> transport.send(message()))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("email transport failure: RuntimeException")
                .hasNoCause();
    }

    /** Kimi Phase 8 Finding #3: a permanent, cheap static guard for the exact structural guarantee
     * the sanitization property rests on - request construction must happen inside the same
     * try block as the SES call itself, not before it, so any construction-time failure is also
     * caught and sanitized. */
    @Test
    void requestConstructionHappensInsideTheTryBlock() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/themistra/notification/channel/SesEmailTransport.java"));

        int tryIndex = source.indexOf("try {");
        int builderIndex = source.indexOf("SendEmailRequest.builder()");

        assertThat(tryIndex).as("try { must appear in the file").isPositive();
        assertThat(builderIndex).as("SendEmailRequest.builder() must appear in the file").isPositive();
        assertThat(tryIndex).as("request construction must be inside the try block, not before it")
                .isLessThan(builderIndex);
    }
}
