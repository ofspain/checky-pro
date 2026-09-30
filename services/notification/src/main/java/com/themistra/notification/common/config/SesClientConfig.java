package com.themistra.notification.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * The real Amazon SES v2 client (O2/Q2, resolved at T03; wired for the first time here). Active
 * only when {@code themistra.notification.email.transport=ses} - conditional at the class level so
 * a {@code fake}-transport run (the default, since this task) never attempts any AWS region or
 * credential resolution at all.
 *
 * <p>No explicit region or credentials property (L10): {@code SesV2Client.builder().build()} relies
 * entirely on the SDK's own default provider chain (IRSA on EKS in real environments) -
 * {@code agents.md} forbids AWS SDK secret-retrieval in application code, which rules out any
 * alternative here.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "themistra.notification.email", name = "transport", havingValue = "ses")
public class SesClientConfig {

    @Bean
    public SesV2Client sesV2Client() {
        return SesV2Client.builder().build();
    }
}
