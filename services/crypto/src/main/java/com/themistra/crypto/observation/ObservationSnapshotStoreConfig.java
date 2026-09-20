package com.themistra.crypto.observation;

import com.themistra.crypto.common.config.SnapshotProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

/**
 * Builds the real {@link S3Client} {@link ObservationSnapshotStore} writes through. No credential is
 * ever set here (L13) — the AWS SDK's own default credential chain applies. No AWS-SDK-client-wiring
 * precedent exists anywhere else in this codebase yet (KMS's own real usage is still unbuilt, confined
 * to the future attest module) — this class sets that precedent from a blank slate.
 */
@Configuration
public class ObservationSnapshotStoreConfig {

    /** {@code SnapshotProperties} has no timeout field of its own (T03, frozen) - a fixed value is
     * used instead, deliberately, rather than modifying that already-shipped config shape. */
    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public S3Client s3Client(SnapshotProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(API_CALL_TIMEOUT)
                        .build())
                .build();
    }

    @Bean
    public ObservationSnapshotStore observationSnapshotStore(S3Client s3Client, SnapshotProperties properties) {
        return new ObservationSnapshotStore(s3Client, properties);
    }
}
