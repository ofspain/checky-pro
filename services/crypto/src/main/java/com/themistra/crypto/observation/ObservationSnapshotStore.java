package com.themistra.crypto.observation;

import com.themistra.crypto.common.config.SnapshotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes the verbatim observation payload to S3 as a WORM snapshot (L3). Never throws — an S3
 * failure (including a timeout, since {@link SdkException} covers both) is logged distinctly and
 * reported back as {@link Optional#empty()}, so {@link ObservationLog} never needs to know about any
 * AWS-specific exception type; the Postgres insert proceeds regardless (frozen brief amendment: S3 is
 * a supplementary durability layer, not a blocking dependency of the load-bearing DB write).
 */
public class ObservationSnapshotStore {

    private static final Logger logger = LoggerFactory.getLogger(ObservationSnapshotStore.class);

    private final S3Client s3Client;
    private final SnapshotProperties properties;

    public ObservationSnapshotStore(S3Client s3Client, SnapshotProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    public Optional<String> store(String chain, String txHash, String provider, FactType factType,
                                   String rawResponseJson, Instant observedAt) {
        String key = buildKey(chain, txHash, provider, factType, observedAt);
        PutObjectRequest request = buildRequest(key, chain, txHash, provider, factType);
        try {
            s3Client.putObject(request, RequestBody.fromString(rawResponseJson));
            return Optional.of(key);
        } catch (SdkException e) {
            logger.error("Failed to write observation snapshot to S3 (bucket={}, key={}): {}",
                    properties.bucket(), key, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private String buildKey(String chain, String txHash, String provider, FactType factType,
                             Instant observedAt) {
        return properties.prefix() + chain + "/" + txHash + "/" + factType.name().toLowerCase()
                + "/" + provider + "-" + observedAt + "-" + UUID.randomUUID() + ".json";
    }

    private PutObjectRequest buildRequest(String key, String chain, String txHash, String provider,
                                           FactType factType) {
        return PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType("application/json")
                .metadata(Map.of(
                        "chain", chain,
                        "txHash", txHash,
                        "provider", provider,
                        "factType", factType.name()))
                .build();
    }
}
