package com.themistra.notification.channel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

/**
 * The real, non-test {@link EmailTransport} (O2/Q2, resolved at T03 in favor of Amazon SES).
 * Active only when {@code themistra.notification.email.transport=ses}.
 *
 * <p>Sends plain-text only (Kimi Phase 3 Finding #6) - the seeded launch templates
 * ({@code V3__seed_launch_templates.sql}) carry no HTML markup; an HTML body is a future task's own
 * scope if ever needed.</p>
 *
 * <p><strong>Never lets a raw SDK exception escape unmodified</strong> (Kimi Phase 3 Finding #4,
 * AC8): a rendered {@code verify_email}/{@code password_reset} body can carry a real,
 * token-bearing link, and an AWS SDK exception's own message/{@code toString()} could otherwise echo
 * back the request that contained it. Every caught {@link SdkException} is converted to an
 * {@link EmailDeliveryException} carrying only its own extracted-safe message - AWS's own
 * {@code errorCode()}/{@code errorMessage()} for a service-side {@link AwsServiceException}, or a
 * generic, class-name-only message for a client-side failure (a client-side exception's own message
 * is not a trusted-safe value the way AWS's own structured error fields are). The original exception
 * is never attached as a cause - see {@link EmailDeliveryException}'s own Javadoc for why.</p>
 */
@Component
@ConditionalOnProperty(prefix = "themistra.notification.email", name = "transport", havingValue = "ses")
public class SesEmailTransport implements EmailTransport {

    private final SesV2Client sesV2Client;

    public SesEmailTransport(SesV2Client sesV2Client) {
        this.sesV2Client = sesV2Client;
    }

    @Override
    public String send(EmailMessage message) {
        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(message.from())
                .destination(Destination.builder().toAddresses(message.to()).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(Content.builder().data(message.subject()).build())
                                .body(Body.builder()
                                        .text(Content.builder().data(message.body()).build())
                                        .build())
                                .build())
                        .build())
                .build();

        try {
            SendEmailResponse response = sesV2Client.sendEmail(request);
            return response.messageId();
        } catch (SdkException e) {
            throw new EmailDeliveryException(safeMessageFor(e));
        }
    }

    private String safeMessageFor(SdkException e) {
        if (e instanceof AwsServiceException ase) {
            return ase.awsErrorDetails().errorCode() + ": " + ase.awsErrorDetails().errorMessage();
        }
        return "email transport failure: " + e.getClass().getSimpleName();
    }
}
