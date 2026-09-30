package com.themistra.notification.channel;

import com.themistra.notification.common.config.EmailProperties;
import com.themistra.notification.common.config.SesClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kimi Phase 3 Findings #1/#3/#9 (AC9): proves exactly one {@link EmailTransport} bean is ever
 * active for a given {@code themistra.notification.email.transport} value - no Testcontainers, no
 * Docker, no real AWS credentials/network access needed (the {@code ses} scenario only proves the
 * {@link software.amazon.awssdk.services.sesv2.SesV2Client} bean is *constructible*, never actually
 * calling AWS; a fixed {@code aws.region} system property lets the client builder resolve a region
 * without needing any real credential).
 */
class EmailTransportWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(EmailProperties.class, () -> new EmailProperties("no-reply@checky.pro", "fake"))
            .withUserConfiguration(FakeEmailTransport.class, SesEmailTransport.class, SesClientConfig.class);

    @Test
    void fakeTransportIsTheOnlyEmailTransportBeanWhenTransportIsFake() {
        contextRunner.withPropertyValues("themistra.notification.email.transport=fake")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailTransport.class);
                    assertThat(context).hasSingleBean(FakeEmailTransport.class);
                    assertThat(context).doesNotHaveBean(SesEmailTransport.class);
                    assertThat(context).doesNotHaveBean(
                            software.amazon.awssdk.services.sesv2.SesV2Client.class);
                });
    }

    @Test
    void sesTransportIsTheOnlyEmailTransportBeanWhenTransportIsSes() {
        contextRunner.withPropertyValues("themistra.notification.email.transport=ses")
                .withSystemProperties("aws.region=us-east-1")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailTransport.class);
                    assertThat(context).hasSingleBean(SesEmailTransport.class);
                    assertThat(context).doesNotHaveBean(FakeEmailTransport.class);
                    assertThat(context).hasSingleBean(
                            software.amazon.awssdk.services.sesv2.SesV2Client.class);
                });
    }

    @Test
    void neitherTransportIsRegisteredForAnUnrecognizedValue() {
        contextRunner.withPropertyValues("themistra.notification.email.transport=sendgrid")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FakeEmailTransport.class);
                    assertThat(context).doesNotHaveBean(SesEmailTransport.class);
                });
    }
}
