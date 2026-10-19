package com.themistra.payment.receipt;

import com.themistra.payment.common.config.ReceiptProperties;
import com.themistra.payment.common.crypto.AttestationResult;
import org.junit.jupiter.api.AfterAll;
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
import software.amazon.awssdk.services.s3.model.ObjectVersion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Threat #5 follow-up FU-T28-1, on a real Object Lock bucket (LocalStack 3.8, which honours
 * {@code If-None-Match: *}). Before the fix, a second store of the same {@code receiptUuid} was
 * accepted as a new current version; now S3 refuses it, and the locked original stays the only
 * version.
 */
@Testcontainers
class ReceiptStoreObjectLockIntegrationTest {

    private static final String BUCKET = "conditional-put-receipts";

    @Container
    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.S3);

    private static S3Client s3;

    @BeforeAll
    static void createObjectLockBucket() {
        s3 = S3Client.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .forcePathStyle(true)
                .build();
        s3.createBucket(b -> b.bucket(BUCKET).objectLockEnabledForBucket(true));
    }

    @AfterAll
    static void closeClient() {
        s3.close();
    }

    private static ReceiptPayload payload(UUID receiptUuid, String amount) {
        return new ReceiptPayload(receiptUuid, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "ETHEREUM", "0xabc", "0xdef", (short) 6, new BigDecimal(amount), "0x789", "0x012",
                Instant.parse("2026-07-13T01:00:00Z"));
    }

    @Test
    void shouldRefuseASecondStoreOfTheSameReceiptAndKeepTheOriginalAsTheOnlyVersion() {
        ReceiptStore store = new ReceiptStore(s3,
                new ReceiptProperties(BUCKET, "receipts/", LOCALSTACK.getRegion(), "http://kms"), Clock.systemUTC());
        UUID receiptUuid = UUID.randomUUID();
        AttestationResult.Signed signed = new AttestationResult.Signed("c2ln", "kms-key-1", Instant.now());

        ReceiptStorageLocation location = store.store(payload(receiptUuid, "1000"), signed);
        byte[] original = store.readJsonObject(BUCKET, location.s3KeyPrefix()).orElseThrow();

        assertThatThrownBy(() -> store.store(payload(receiptUuid, "999999"), signed))
                .isInstanceOf(ReceiptStorageException.class)
                .hasMessageContaining("already exists");

        List<ObjectVersion> versions = s3.listObjectVersions(b -> b.bucket(BUCKET).prefix(location.jsonKey()))
                .versions();
        assertThat(versions).as("the refused put must not add a version").hasSize(1);
        assertThat(store.readJsonObject(BUCKET, location.s3KeyPrefix()))
                .hasValueSatisfying(current -> assertThat(current).isEqualTo(original));
        assertThat(store.listStoredReceiptPrefixes()).containsExactly(location.s3KeyPrefix());
    }
}
