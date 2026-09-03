package com.themistra.crypto.observation;

import com.themistra.crypto.common.config.SnapshotProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real S3-API round-trip through {@link ObservationSnapshotStore} alone (AC1, AC5, AC7) — S3 is core
 * persistence infrastructure here (like Postgres/Kafka elsewhere in this service), not an external RPC
 * provider to fake, per the frozen brief's own testing-strategy decision.
 *
 * <p>Simpler than the frozen brief's own sketch (a {@code @TestConfiguration} overriding the
 * production {@code S3Client} Spring bean): this test constructs {@link S3Client}/{@link
 * ObservationSnapshotStore} directly, with no Spring context at all. The actual requirement — a real
 * S3 round-trip — is fully met either way; booting the whole application context adds nothing this
 * narrower, faster approach doesn't already prove for {@code ObservationSnapshotStore} specifically.
 * No real AWS credential is used or needed — LocalStack accepts any non-empty static credential pair.</p>
 */
@Testcontainers
class ObservationSnapshotStoreLocalStackIntegrationTest {

    private static final String BUCKET = "observation-snapshots-it";

    @Container
    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.S3);

    private static S3Client s3Client;
    private static ObservationSnapshotStore store;

    @BeforeAll
    static void createClientAndBucket() {
        s3Client = S3Client.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .forcePathStyle(true) // required against LocalStack's endpoint shape
                .build();
        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());

        SnapshotProperties properties = new SnapshotProperties(BUCKET, "chain-observations/", LOCALSTACK.getRegion());
        store = new ObservationSnapshotStore(s3Client, properties);
    }

    @Test
    void putsAndGetsARealObjectFromLocalStack() {
        String rawJson = "{\"exists\":true,\"blockNumber\":12345}";

        Optional<String> key = store.store("ETHEREUM", "0xreal-tx-hash", "alchemy", FactType.EXISTENCE,
                rawJson, Instant.now());

        assertThat(key).isPresent();
        String retrieved = s3Client.getObject(GetObjectRequest.builder().bucket(BUCKET).key(key.get()).build(),
                        software.amazon.awssdk.core.sync.ResponseTransformer.toBytes())
                .asUtf8String();
        assertThat(retrieved).isEqualTo(rawJson);
    }
}
