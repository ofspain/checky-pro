package com.themistra.crypto.attest;

import com.themistra.crypto.common.config.KmsProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.KeyUsageType;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real KMS-API round-trip through {@link KmsSigner} alone (frozen brief's contingent Required Test) -
 * mirrors {@code ObservationSnapshotStoreLocalStackIntegrationTest}'s own established pattern: no
 * Spring context, direct construction via {@code KmsSigner}'s test-seam constructor, a real
 * LocalStack-emulated AWS API called over the network, no fake/mock in this class at all.
 *
 * <p>Verified directly in this phase, before writing this test, that LocalStack 3.8 (the exact image
 * already pinned by the S3 precedent) supports {@code CreateKey} with {@code KeyUsage=SIGN_VERIFY}/
 * {@code KeySpec=ECC_NIST_P256} and {@code Sign} with {@code MessageType.DIGEST}/{@code
 * SigningAlgorithmSpec.ECDSA_SHA_256} - a standalone probe produced a real 72-byte DER-encoded ECDSA
 * signature. Not assumed; the probe is why this test exists rather than a documented skip.</p>
 */
@Testcontainers
class KmsSignerLocalStackIntegrationTest {

    @Container
    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.KMS);

    private static KmsClient kmsClient;
    private static String keyId;
    private static PublicKey publicKey;

    @BeforeAll
    static void createClientAndAsymmetricKey() throws Exception {
        kmsClient = KmsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.KMS))
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .build();

        CreateKeyResponse createKeyResponse = kmsClient.createKey(CreateKeyRequest.builder()
                .keyUsage(KeyUsageType.SIGN_VERIFY)
                .keySpec(KeySpec.ECC_NIST_P256)
                .build());
        keyId = createKeyResponse.keyMetadata().keyId();

        byte[] publicKeyDer = kmsClient.getPublicKey(GetPublicKeyRequest.builder().keyId(keyId).build())
                .publicKey().asByteArray();
        publicKey = KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(publicKeyDer));
    }

    @Test
    void producesARealSignatureVerifiableAgainstTheKeysOwnPublicKey() throws Exception {
        byte[] digest = new byte[32];
        new Random(42).nextBytes(digest);
        Instant fixedInstant = Instant.parse("2026-09-13T00:00:00Z");
        KmsSigner signer = new KmsSigner(new KmsProperties(keyId), Clock.fixed(fixedInstant, ZoneOffset.UTC), kmsClient);

        SignatureResult result = signer.sign(digest);

        // Empirically confirms frozen brief Finding #4's own reasoning: KMS's real response carries
        // the full key ARN, not the bare key id this test configured/passed in as KmsProperties.keyId()
        // - proving kmsKeyId() must come from SignResponse.keyId() itself, never echo the input.
        assertThat(result.kmsKeyId())
                .as("KMS's own response key id, not the bare id that was configured")
                .isNotEqualTo(keyId)
                .contains(keyId);
        assertThat(result.signedAt()).isEqualTo(fixedInstant);

        // NONEwithECDSA, not SHA256withECDSA: MessageType.DIGEST told KMS the input was already a
        // SHA-256 digest, not a raw message to hash itself - verifying with an algorithm that
        // re-hashes would hash the already-hashed bytes a second time and never match. KMS's
        // signature is DER-encoded (ASN.1 SEQUENCE of r,s), which is exactly what JCA's ECDSA
        // Signature.verify(...) expects by default - no re-encoding needed.
        Signature verifier = Signature.getInstance("NONEwithECDSA");
        verifier.initVerify(publicKey);
        verifier.update(digest);
        byte[] signatureBytes = Base64.getDecoder().decode(result.signatureBase64());

        assertThat(verifier.verify(signatureBytes))
                .as("the signature KmsSigner produced must verify against the key's own public key "
                        + "for the exact digest that was signed")
                .isTrue();
    }

    @Test
    void shouldPublishVerificationKeysAtWellKnownUrl() throws Exception {
        // Named test (package.md §8), R24 - a real GetPublicKey round-trip through KmsSigner's actual
        // production code path (test-seam constructor), proving the returned PEM is genuinely valid,
        // not just well-formed-looking text.
        KmsSigner signer = new KmsSigner(new KmsProperties(keyId), Clock.systemUTC(), kmsClient);

        PublicKeyInfo info = signer.publicKeyInfo();

        assertThat(info.kmsKeyId()).contains(keyId);
        assertThat(info.kid()).isEqualTo(info.kmsKeyId());
        assertThat(info.alg()).isEqualTo("ECDSA_SHA_256");
        assertThat(info.publicKeyPem()).startsWith("-----BEGIN PUBLIC KEY-----\n")
                .endsWith("-----END PUBLIC KEY-----\n");

        String base64Body = info.publicKeyPem()
                .replace("-----BEGIN PUBLIC KEY-----\n", "")
                .replace("-----END PUBLIC KEY-----\n", "")
                .replace("\n", "");
        PublicKey parsedFromPem = KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64Body)));
        assertThat(parsedFromPem).isEqualTo(publicKey);
    }

    @Test
    void signAndPublicKeyInfoAgreeOnKmsKeyIdForTheSameKey() {
        // Phase 3 Finding #5: a verifier looks up a receipt's kmsKeyId in the published key list - the
        // two paths (sign vs. get-public-key) must report the identical identifier for the same key.
        KmsSigner signer = new KmsSigner(new KmsProperties(keyId), Clock.systemUTC(), kmsClient);
        byte[] digest = new byte[32];
        new Random(7).nextBytes(digest);

        SignatureResult signResult = signer.sign(digest);
        PublicKeyInfo publicKeyInfo = signer.publicKeyInfo();

        assertThat(signResult.kmsKeyId()).isEqualTo(publicKeyInfo.kmsKeyId());
    }
}
