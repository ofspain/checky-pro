package com.themistra.crypto.observation;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.themistra.crypto.common.config.SnapshotProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
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
        // Phase 11 Gap 7: also captures the actual PutObjectRequest and asserts its key is identical
        // to the returned value - a bug generating one key for the request and another for the return
        // value would otherwise go uncaught.
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        when(s3Client.putObject(captor.capture(), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        Optional<String> key = store.store("ETHEREUM", "0xabc", "alchemy", FactType.EXISTENCE, "{}",
                OBSERVED_AT);

        assertThat(key).isPresent();
        assertThat(key.get()).startsWith("chain-observations/ETHEREUM/0xabc/").endsWith(".json");
        assertThat(captor.getValue().key()).isEqualTo(key.get());
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

    @Test
    void keyIsBoundedForALongPrefix() {
        // Phase 11 Gap 11: keyIsBoundedRegardlessOfInputLength above only varies chain/txHash: a
        // deployment with a longer-than-default SnapshotProperties.prefix (no max-length validation
        // exists on that record, T03, frozen) is a residual risk this test documents rather than
        // silently ignores. The fixed cost of a maximal chain(32) + "/" + txHash(128) + "/" + a
        // UUID(36) + ".json"(5) is 203 chars, leaving ~53 chars of budget under the 256-char column
        // before the key overflows - a 40-char prefix (double the real "chain-observations/" default)
        // stays safely inside that budget; anything past ~53 chars would not, which is a known,
        // accepted operational constraint on deployment config rather than something this task's own
        // buildKey fix (Phase 9) claims to bound.
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        SnapshotProperties longPrefixProperties =
                new SnapshotProperties("my-bucket", "p".repeat(39) + "/", "us-east-1");
        ObservationSnapshotStore storeWithLongPrefix = new ObservationSnapshotStore(s3Client, longPrefixProperties);
        String maxChain = "A".repeat(32);
        String maxTxHash = "b".repeat(128);

        Optional<String> key = storeWithLongPrefix.store(maxChain, maxTxHash, "alchemy",
                FactType.EXISTENCE, "{}", OBSERVED_AT);

        assertThat(key).isPresent();
        assertThat(key.get().length()).isLessThanOrEqualTo(256);
    }

    @Test
    void storeDoesNotLogRawResponsePayloadOnFailure() {
        // Phase 11 Gap 12: enforces the frozen brief's own Security constraint - "no AWS credential
        // or S3 object content is ever logged; only the computed key ... may appear in logs."
        Logger logger = (Logger) LoggerFactory.getLogger(ObservationSnapshotStore.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String sensitivePayload = "{\"secretField\":\"should-never-appear-in-logs\"}";
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenThrow(SdkException.builder().message("connection refused").build());

            store.store("ETHEREUM", "0xabc", "alchemy", FactType.EXISTENCE, sensitivePayload, OBSERVED_AT);

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(sensitivePayload))
                    .noneMatch(message -> message.contains("secretField"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
