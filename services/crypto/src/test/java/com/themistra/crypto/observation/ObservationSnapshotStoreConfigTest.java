package com.themistra.crypto.observation;

import com.themistra.crypto.common.config.SnapshotProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Phase 11 Gap 3: a deliberately scoped-down smoke test — {@code S3Client} exposes no public
 * accessor for its configured region/timeout to assert against directly (unlike {@code
 * EthereumAdapterConfigTest}'s reflection into {@code HttpService}'s own fields, which had a public
 * {@code getUrl()} to build on), and reflecting into AWS SDK internal fields for this would be
 * fragile for limited additional confidence beyond what this proves: that {@link
 * ObservationSnapshotStoreConfig#s3Client} and {@link ObservationSnapshotStoreConfig#observationSnapshotStore}
 * construct without error from valid {@link SnapshotProperties} (AC5 — no credential is required for
 * client *construction* itself, only for an actual call, so this never touches the network).
 */
class ObservationSnapshotStoreConfigTest {

    private final ObservationSnapshotStoreConfig config = new ObservationSnapshotStoreConfig();

    @Test
    void s3ClientConstructsWithoutErrorForAValidRegion() {
        SnapshotProperties properties = new SnapshotProperties("my-bucket", "chain-observations/", "us-east-1");

        assertThatCode(() -> config.s3Client(properties)).doesNotThrowAnyException();
    }

    @Test
    void observationSnapshotStoreBeanWrapsTheGivenS3ClientAndProperties() {
        SnapshotProperties properties = new SnapshotProperties("my-bucket", "chain-observations/", "us-east-1");
        S3Client s3Client = config.s3Client(properties);

        ObservationSnapshotStore store = config.observationSnapshotStore(s3Client, properties);

        assertThat(store).isNotNull();
    }
}
