package com.themistra.crypto.attest;

import com.themistra.crypto.common.config.KmsProperties;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/**
 * The sole caller of {@code kms:Sign} on the attestation key (R22, L11, ADR-0004). No other class in
 * {@code com.themistra.crypto} may depend on this class or on any type under {@code
 * software.amazon.awssdk.services.kms..} - enforced by {@link KmsSignerArchitectureTest}, the
 * frozen-brief-mandated ArchUnit rule plus its plain-JUnit canary (Kimi Phase 3 Finding #1: {@code
 * @ArchTest} fields alone are not executed under this repo's Surefire setup, confirmed against {@code
 * services/auth}'s own identical, already-documented gap).
 *
 * <p><b>The {@link KmsClient} is built here, not as a separate {@code @Bean} class (Phase 6 redesign,
 * discovered necessary while implementing the ArchUnit rule above).</b> Mirrors {@code
 * services/auth}'s own {@code MfaSeedEncryption} — the actual, already-reviewed precedent in this
 * monorepo for "one class owns both constructing its AWS client and being the sole caller of the
 * sensitive operation," which fits this task's own concentration goal far more precisely than a
 * separate {@code XxxConfig} class would: a would-be {@code KmsSignerConfig} needing to import {@code
 * KmsClient} just to build the bean would itself trip the very ArchUnit rule this class exists to
 * enforce. Unlike {@code MfaSeedEncryption}, there is no local-dev/no-real-client bypass here — this
 * service's own established posture (see {@code ObservationSnapshotStoreConfig}'s S3 client, and its
 * LocalStack-backed integration test) treats core AWS infrastructure as real infrastructure to connect
 * to (real endpoint or LocalStack), not something to fake, unlike the blockchain RPC providers
 * {@code agents.md} says are always scripted/fake in tests and local dev.</p>
 *
 * <p><b>No explicit {@code .region(...)} call (frozen brief Finding #8).</b> {@link KmsProperties} (T03,
 * frozen) carries no {@code region()} field. The AWS SDK v2 builder resolves a region eagerly inside
 * {@code .build()} via its own default region provider chain (env var / instance metadata / IRSA),
 * throwing {@code SdkClientException} immediately if none can be determined — verified directly in this
 * phase (a standalone probe with a clean environment threw exactly this, immediately, at {@code
 * .build()} time) — since this bean is constructed eagerly at Spring context startup, a missing region
 * still fails fast at startup, just via a different exception type than a {@code
 * @ConfigurationProperties} validation failure.</p>
 *
 * <p><b>Deliberately declares no {@link org.slf4j.Logger} field (Finding #11).</b> The strongest
 * available guarantee that the input digest or the output signature can never be logged is a class that
 * has no logging capability at all - not a policy resting on discipline.</p>
 *
 * <p><b>Failures propagate uncaught.</b> Unlike {@code ObservationSnapshotStore}'s S3-failure handling
 * (which swallows into {@code Optional.empty()} because S3 there is a supplementary durability layer),
 * signing is the load-bearing deliverable of the future {@code /attest} endpoint - a {@link
 * software.amazon.awssdk.services.kms.model.KmsException} or any other SDK failure must be visible to
 * the caller, never silently downgraded. All of {@link KmsClient#sign}'s declared exceptions are
 * unchecked ({@code KmsException} extends {@code AwsServiceException} extends {@code SdkException},
 * verified directly against the SDK jar's own bytecode), so no wrapping is needed for this to compile.</p>
 *
 * <p><b>Algorithm pending Q7 ({@code package.md} §11).</b> {@link #SIGNING_ALGORITHM} is a single named
 * constant, not scattered through the class, specifically so the one open question in this design -
 * which KMS key type/algorithm the platform will actually provision - has exactly one place to change
 * once Q7 is answered. {@code ECDSA_SHA_256} is chosen because {@code design.md} §4c's own wire contract
 * already fixes the digest as SHA-256 ({@code receiptDigestSha256}); an ECDSA P-256 key is the natural
 * KMS key spec for a pre-hashed SHA-256 digest sent as {@link MessageType#DIGEST}.</p>
 */
@Component
public class KmsSigner implements DisposableBean {

    private static final SigningAlgorithmSpec SIGNING_ALGORITHM = SigningAlgorithmSpec.ECDSA_SHA_256;
    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(5);

    private final KmsProperties properties;
    private final Clock clock;
    private final KmsClient kmsClient;

    @Autowired
    public KmsSigner(KmsProperties properties, Clock clock) {
        this(properties, clock, resolveKmsClient());
    }

    /** Test seam: lets unit tests inject a mocked {@link KmsClient} directly. */
    KmsSigner(KmsProperties properties, Clock clock, KmsClient kmsClient) {
        this.properties = properties;
        this.clock = clock;
        this.kmsClient = kmsClient;
    }

    private static KmsClient resolveKmsClient() {
        return KmsClient.builder()
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(API_CALL_TIMEOUT)
                        .build())
                .build();
    }

    /**
     * @param digestSha256 an already-computed 32-byte SHA-256 digest; never logged
     * @return the signature and the key KMS actually used to produce it
     * @throws NullPointerException     if {@code digestSha256} is {@code null}
     * @throws IllegalArgumentException if {@code digestSha256} is not exactly 32 bytes
     */
    public SignatureResult sign(byte[] digestSha256) {
        Objects.requireNonNull(digestSha256, "digestSha256");
        if (digestSha256.length != 32) {
            throw new IllegalArgumentException(
                    "digestSha256 must be exactly 32 bytes (SHA-256), was " + digestSha256.length);
        }

        SignRequest request = SignRequest.builder()
                .keyId(properties.keyId())
                .message(SdkBytes.fromByteArray(digestSha256))
                .messageType(MessageType.DIGEST)
                .signingAlgorithm(SIGNING_ALGORITHM)
                .build();

        SignResponse response = kmsClient.sign(request);

        return new SignatureResult(
                Base64.getEncoder().encodeToString(response.signature().asByteArray()),
                response.keyId(),
                clock.instant());
    }

    /** Phase 9 (Kimi Phase 8 Finding #4): {@code KmsClient} owns a connection pool and other I/O
     * resources; {@code services/auth}'s own {@code MfaSeedEncryption} (the precedent this class's
     * shape mirrors) closes its client on context shutdown for the same reason. */
    @Override
    public void destroy() {
        kmsClient.close();
    }
}
