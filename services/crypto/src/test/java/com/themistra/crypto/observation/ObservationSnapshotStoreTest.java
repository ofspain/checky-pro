package com.themistra.crypto.observation;

import com.themistra.crypto.common.config.SnapshotProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** AC2 (S3 attempt, failure does not throw), AC7 (Content-Type/metadata). No real network call
 * (AC6) - {@link S3Client} is mocked. */
@ExtendWith(MockitoExtension.class)
class ObservationSnapshotStoreTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-09-03T12:00:00Z");

    @Mock
    private S3Client s3Client;

    private ObservationSnapshotStore store;

    @BeforeEach
    void setUp() {
        SnapshotProperties properties = new SnapshotProperties("my-bucket", "chain-observations/", "us-east-1");
        store = new ObservationSnapshotStore(s3Client, properties);
    }

    @Test
    void storeReturnsTheComputedKeyOnSuccess() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        Optional<String> key = store.store("ETHEREUM", "0xabc", "alchemy", FactType.EXISTENCE, "{}",
                OBSERVED_AT);

        assertThat(key).isPresent();
        assertThat(key.get()).startsWith("chain-observations/ETHEREUM/0xabc/").endsWith(".json");
    }

    @Test
    void storeSetsContentTypeAndMetadataOnThePutObjectRequest() {
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        when(s3Client.putObject(captor.capture(), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        store.store("ETHEREUM", "0xabc", "alchemy", FactType.EXISTENCE, "{}", OBSERVED_AT);

        PutObjectRequest request = captor.getValue();
        assertThat(request.contentType()).isEqualTo("application/json");
        assertThat(request.metadata())
                .containsEntry("chain", "ETHEREUM")
                .containsEntry("txHash", "0xabc")
                .containsEntry("provider", "alchemy")
                .containsEntry("factType", "EXISTENCE")
                .containsEntry("observedAt", OBSERVED_AT.toString());
    }

    @Test
    void storeReturnsEmptyWhenS3ThrowsAnSdkException() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkException.builder().message("connection refused").build());

        Optional<String> key = store.store("ETHEREUM", "0xabc", "alchemy", FactType.EXISTENCE, "{}",
                OBSERVED_AT);

        assertThat(key).isEmpty();
    }

    @Test
    void keyIsBoundedRegardlessOfInputLength() {
        // Phase 9 (Kimi/self-review Issue 1) regression guard: even maximal-length chain/txHash
        // inputs (matching chain.observations' own declared column widths) must never produce a key
        // exceeding s3_snapshot_key's own VARCHAR(256).
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        String maxChain = "A".repeat(32);
        String maxTxHash = "b".repeat(128);

        Optional<String> key = store.store(maxChain, maxTxHash, "alchemy", FactType.EXISTENCE, "{}",
                OBSERVED_AT);

        assertThat(key).isPresent();
        assertThat(key.get().length()).isLessThanOrEqualTo(256);
    }

    @Test
    void keyIncludesAUniqueComponentAcrossCalls() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        Optional<String> first = store.store("ETHEREUM", "0xabc", "alchemy", FactType.EXISTENCE, "{}",
                OBSERVED_AT);
        Optional<String> second = store.store("ETHEREUM", "0xabc", "alchemy", FactType.EXISTENCE, "{}",
                OBSERVED_AT);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void storeThrowsNullPointerExceptionForANullArgument() {
        // Phase 9 (Kimi/self-review Issue 8): fails fast with a named argument, not a confusing NPE
        // deep inside Map.of(...).
        assertThatThrownBy(() -> store.store(null, "0xabc", "alchemy", FactType.EXISTENCE, "{}", OBSERVED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("chain");
    }
}
