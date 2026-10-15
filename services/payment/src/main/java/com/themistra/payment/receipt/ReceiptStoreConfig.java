package com.themistra.payment.receipt;

import com.themistra.payment.common.config.ReceiptProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Clock;
import java.time.Duration;

/**
 * Builds the real {@link S3Client} {@link ReceiptStore} writes through. No credential is ever
 * set here (L13) — the AWS SDK's own default credential chain applies, mirroring
 * {@code ObservationSnapshotStoreConfig}'s own precedent (crypto-service) exactly. The timeout
 * is widened to 10s rather than that precedent's own 5s (Phase 3/4, Kimi Finding 5) — this call
 * also does local PDF-rendering work and is a load-bearing write, not a supplementary one.
 */
@Configuration
public class ReceiptStoreConfig {

    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public S3Client s3Client(ReceiptProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(API_CALL_TIMEOUT)
                        .build())
                .build();
    }

    @Bean
    public ReceiptStore receiptStore(S3Client s3Client, ReceiptProperties properties, Clock clock) {
        return new ReceiptStore(s3Client, properties, clock);
    }
}
