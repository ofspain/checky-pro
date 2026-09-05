package com.themistra.auth.mfa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MfaSeedEncryption} — R22, L14, L13, ADR-0003, AC3-AC8 of the T16 frozen
 * brief, plus the Phase 8/9 review fixes (malformed-envelope handling, {@code generateDataKey}
 * exception wrapping, version-0x00 wrapped-key-length enforcement, {@code destroy()}). Plain
 * JUnit + Mockito, no Spring context — {@link MockEnvironment} stands in for {@link Environment}
 * so the guard/mode-switch logic (ADR-0003's "active Spring profile is local") is exercised
 * without booting a container. Uses the package-private test-seam constructor to inject a mocked
 * {@link KmsClient} directly, per the Phase 5 plan.
 */
@ExtendWith(MockitoExtension.class)
class MfaSeedEncryptionTest {

    private static final byte VERSION_LOCAL = 0x00;
    private static final byte VERSION_KMS = 0x01;
    private static final String SEED_KEK_ARN = "arn:aws:kms:eu-west-1:111111111111:key/fake-cmk";

    @Mock
    private KmsClient kmsClient;

    private final SecureRandom secureRandom = new SecureRandom();

    private Environment localEnvironment;
    private Environment devEnvironment;
    private MfaProperties localProperties;
    private MfaProperties kmsProperties;

    @BeforeEach
    void setUp() {
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");
        localEnvironment = local;

        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        devEnvironment = dev;

        localProperties = new MfaProperties("Themistra", "");
        kmsProperties = new MfaProperties("Themistra", SEED_KEK_ARN);
    }

    private byte[] randomSecret() {
        byte[] secret = new byte[20];
        secureRandom.nextBytes(secret);
        return secret;
    }

    // --- AC5: constructor guard -------------------------------------------------------------

    @Test
    void constructorRefusesToBootInNonLocalProfileWithBlankArn() {
        assertThatThrownBy(() -> new MfaSeedEncryption(localProperties, devEnvironment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("seed-kek-arn")
                .hasMessageContaining("local");
    }

    @Test
    void constructorSucceedsInLocalProfileWithBlankArn() {
        assertThatCode(() -> new MfaSeedEncryption(localProperties, localEnvironment, null))
                .doesNotThrowAnyException();
    }

    @Test
    void constructorSucceedsInNonLocalProfileWithArnConfigured() {
        assertThatCode(() -> new MfaSeedEncryption(kmsProperties, devEnvironment, kmsClient))
                .doesNotThrowAnyException();
    }

    @Test // Phase 11 finding #5 — no active profile at all (SPRING_PROFILES_ACTIVE absent) is
          // the deployed-misconfiguration case; it must fail closed exactly like a named
          // non-local profile does, not be treated as accidentally "local enough"
    void constructorRefusesToBootWithBlankArnWhenNoProfileIsActive() {
        Environment noProfileEnvironment = new MockEnvironment(); // zero active profiles

        assertThatThrownBy(() -> new MfaSeedEncryption(localProperties, noProfileEnvironment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("seed-kek-arn");
    }

    @Test // Phase 11 finding #6 — Environment.acceptsProfiles must match on ANY active profile
          // containing "local", not just when it's the sole one; locks in this semantic against
          // a future rewrite that compares a single profile string
    void constructorAllowsLocalModeWhenLocalProfileIsPresentAmongOthers() {
        MockEnvironment localAndTest = new MockEnvironment();
        localAndTest.setActiveProfiles("local", "test");

        MfaSeedEncryption encryption = new MfaSeedEncryption(localProperties, localAndTest);
        byte[] secret = randomSecret();

        byte[] envelope = encryption.encrypt(secret);

        assertThat(envelope[0]).isEqualTo(VERSION_LOCAL);
        assertThat(encryption.decrypt(envelope)).isEqualTo(secret);
    }

    @Test // exercises the real Spring-facing constructor, not the test seam: proves local mode
          // never attempts to build a real AWS KmsClient (which would fail immediately without
          // credentials/region in this test JVM if it were attempted)
    void publicConstructorNeverBuildsARealKmsClientInLocalMode() {
        MfaSeedEncryption encryption = new MfaSeedEncryption(localProperties, localEnvironment);

        byte[] secret = randomSecret();
        byte[] envelope = encryption.encrypt(secret);

        assertThat(envelope[0]).isEqualTo(VERSION_LOCAL);
        assertThat(encryption.decrypt(envelope)).isEqualTo(secret);
    }

    // --- AC4: local mode -------------------------------------------------------------------

    @Test
    void localModeRoundTripsAndNeverCallsKms() {
        MfaSeedEncryption encryption = new MfaSeedEncryption(localProperties, localEnvironment, null);
        byte[] secret = randomSecret();

        byte[] envelope = encryption.encrypt(secret);

        assertThat(envelope[0]).isEqualTo(VERSION_LOCAL);
        assertThat(encryption.decrypt(envelope)).isEqualTo(secret);
    }

    @Test // AC6
    void localModeCiphertextNeverContainsRawSecretAsSubstring() {
        MfaSeedEncryption encryption = new MfaSeedEncryption(localProperties, localEnvironment, null);
        byte[] secret = randomSecret();

        byte[] envelope = encryption.encrypt(secret);

        assertThat(indexOf(envelope, secret)).isEqualTo(-1);
    }

    // --- AC3: KMS mode -----------------------------------------------------------------------

    @Test
    void kmsModeProducesAdr0003EnvelopeLayoutAndRoundTrips() {
        byte[] plaintextDataKey = new byte[32];
        secureRandom.nextBytes(plaintextDataKey);
        byte[] wrappedDataKey = "fake-kms-ciphertext-blob".getBytes(StandardCharsets.UTF_8);

        when(kmsClient.generateDataKey(any(GenerateDataKeyRequest.class))).thenReturn(
                GenerateDataKeyResponse.builder()
                        .plaintext(SdkBytes.fromByteArray(plaintextDataKey))
                        .ciphertextBlob(SdkBytes.fromByteArray(wrappedDataKey))
                        .build());
        when(kmsClient.decrypt(any(DecryptRequest.class))).thenReturn(
                DecryptResponse.builder().plaintext(SdkBytes.fromByteArray(plaintextDataKey)).build());

        MfaSeedEncryption encryption = new MfaSeedEncryption(kmsProperties, devEnvironment, kmsClient);
        byte[] secret = randomSecret();

        byte[] envelope = encryption.encrypt(secret);

        ByteBuffer buffer = ByteBuffer.wrap(envelope);
        assertThat(buffer.get()).isEqualTo(VERSION_KMS);
        int wrappedKeyLength = Short.toUnsignedInt(buffer.getShort());
        assertThat(wrappedKeyLength).isEqualTo(wrappedDataKey.length);
        byte[] wrappedKeyField = new byte[wrappedKeyLength];
        buffer.get(wrappedKeyField);
        assertThat(wrappedKeyField).isEqualTo(wrappedDataKey);
        byte[] nonce = new byte[12];
        buffer.get(nonce);
        assertThat(buffer.remaining()).isEqualTo(secret.length + 16); // ciphertext + GCM tag

        ArgumentCaptor<GenerateDataKeyRequest> generateCaptor = ArgumentCaptor.forClass(GenerateDataKeyRequest.class);
        verify(kmsClient).generateDataKey(generateCaptor.capture());
        assertThat(generateCaptor.getValue().keyId()).isEqualTo(SEED_KEK_ARN);
        assertThat(generateCaptor.getValue().keySpec()).isEqualTo(DataKeySpec.AES_256);

        assertThat(encryption.decrypt(envelope)).isEqualTo(secret);

        ArgumentCaptor<DecryptRequest> decryptCaptor = ArgumentCaptor.forClass(DecryptRequest.class);
        verify(kmsClient).decrypt(decryptCaptor.capture());
        assertThat(decryptCaptor.getValue().keyId()).isEqualTo(SEED_KEK_ARN);
        assertThat(decryptCaptor.getValue().ciphertextBlob().asByteArray()).isEqualTo(wrappedDataKey);
    }

    @Test // AC6
    void kmsModeCiphertextNeverContainsRawSecretAsSubstring() {
        byte[] plaintextDataKey = new byte[32];
        secureRandom.nextBytes(plaintextDataKey);
        when(kmsClient.generateDataKey(any(GenerateDataKeyRequest.class))).thenReturn(
                GenerateDataKeyResponse.builder()
                        .plaintext(SdkBytes.fromByteArray(plaintextDataKey))
                        .ciphertextBlob(SdkBytes.fromByteArray("wrapped".getBytes(StandardCharsets.UTF_8)))
                        .build());

        MfaSeedEncryption encryption = new MfaSeedEncryption(kmsProperties, devEnvironment, kmsClient);
        byte[] secret = randomSecret();

        byte[] envelope = encryption.encrypt(secret);

        assertThat(indexOf(envelope, secret)).isEqualTo(-1);
    }

    @Test // AC6 — a KMS-level rejection (network/API error) of the wrapped key must fail
          // distinctly, never return a silently-wrong plaintext
    void wrongKeyDecryptFailsDistinctlyViaKmsRejection() {
        byte[] plaintextDataKey = new byte[32];
        secureRandom.nextBytes(plaintextDataKey);
        byte[] wrappedDataKey = "wrapped".getBytes(StandardCharsets.UTF_8);
        when(kmsClient.generateDataKey(any(GenerateDataKeyRequest.class))).thenReturn(
                GenerateDataKeyResponse.builder()
                        .plaintext(SdkBytes.fromByteArray(plaintextDataKey))
                        .ciphertextBlob(SdkBytes.fromByteArray(wrappedDataKey))
                        .build());
        when(kmsClient.decrypt(any(DecryptRequest.class)))
                .thenThrow(software.amazon.awssdk.services.kms.model.KmsException.builder()
                        .message("simulated: ciphertext rejected by KMS").build());

        MfaSeedEncryption encryption = new MfaSeedEncryption(kmsProperties, devEnvironment, kmsClient);
        byte[] envelope = encryption.encrypt(randomSecret());

        assertThatThrownBy(() -> encryption.decrypt(envelope))
                .isInstanceOf(MfaEncryptionException.class)
                .hasMessageContaining("unwrap");
    }

    @Test // AC6, Phase 11 finding #3 — the real cryptographic concern: KMS returns a *valid*
          // response with a *different* (rotated/wrong) 32-byte data key. This must fail via GCM
          // tag authentication, not silently return a bad plaintext. A KMS-rejection test alone
          // (above) wouldn't catch a regression that skipped GCM authentication entirely.
    void wrongButValidDataKeyFailsGcmAuthenticationInsteadOfSilently() {
        byte[] encryptionDataKey = new byte[32];
        secureRandom.nextBytes(encryptionDataKey);
        byte[] wrappedDataKey = "wrapped".getBytes(StandardCharsets.UTF_8);
        byte[] differentDataKey = new byte[32];
        secureRandom.nextBytes(differentDataKey);
        assertThat(differentDataKey).isNotEqualTo(encryptionDataKey);

        when(kmsClient.generateDataKey(any(GenerateDataKeyRequest.class))).thenReturn(
                GenerateDataKeyResponse.builder()
                        .plaintext(SdkBytes.fromByteArray(encryptionDataKey))
                        .ciphertextBlob(SdkBytes.fromByteArray(wrappedDataKey))
                        .build());

        MfaSeedEncryption encryption = new MfaSeedEncryption(kmsProperties, devEnvironment, kmsClient);
        byte[] envelope = encryption.encrypt(randomSecret());

        // KMS now (validly) returns a different plaintext key than the one used to encrypt —
        // simulates a rotated or misconfigured CMK, not a network/API failure.
        when(kmsClient.decrypt(any(DecryptRequest.class))).thenReturn(
                DecryptResponse.builder().plaintext(SdkBytes.fromByteArray(differentDataKey)).build());

        assertThatThrownBy(() -> encryption.decrypt(envelope))
                .isInstanceOf(MfaEncryptionException.class)
                .hasMessageContaining("decryption failed");
    }

    @Test // AC6, Phase 11 finding #4 — a corrupted ciphertext must fail GCM authentication, not
          // decrypt to silently-wrong bytes
    void localModeTamperedCiphertextFailsGcmAuthentication() {
        MfaSeedEncryption encryption = new MfaSeedEncryption(localProperties, localEnvironment, null);
        byte[] envelope = encryption.encrypt(randomSecret());
        envelope[envelope.length - 1] ^= 0x01; // flip one bit in the ciphertext/tag region

        assertThatThrownBy(() -> encryption.decrypt(envelope))
                .isInstanceOf(MfaEncryptionException.class)
                .hasMessageContaining("decryption failed");
    }

    @Test // AC6, Phase 11 finding #4
    void kmsModeTamperedCiphertextFailsGcmAuthentication() {
        byte[] plaintextDataKey = new byte[32];
        secureRandom.nextBytes(plaintextDataKey);
        when(kmsClient.generateDataKey(any(GenerateDataKeyRequest.class))).thenReturn(
                GenerateDataKeyResponse.builder()
                        .plaintext(SdkBytes.fromByteArray(plaintextDataKey))
                        .ciphertextBlob(SdkBytes.fromByteArray("wrapped".getBytes(StandardCharsets.UTF_8)))
                        .build());
        when(kmsClient.decrypt(any(DecryptRequest.class))).thenReturn(
                DecryptResponse.builder().plaintext(SdkBytes.fromByteArray(plaintextDataKey)).build());

        MfaSeedEncryption encryption = new MfaSeedEncryption(kmsProperties, devEnvironment, kmsClient);
        byte[] envelope = encryption.encrypt(randomSecret());
        envelope[envelope.length - 1] ^= 0x01;

        assertThatThrownBy(() -> encryption.decrypt(envelope))
                .isInstanceOf(MfaEncryptionException.class)
                .hasMessageContaining("decryption failed");
    }

    @Test // Phase 8/9 fix: generateDataKey failures must be wrapped like decrypt failures are
    void generateDataKeyFailureIsWrappedAsMfaEncryptionException() {
        when(kmsClient.generateDataKey(any(GenerateDataKeyRequest.class)))
                .thenThrow(software.amazon.awssdk.services.kms.model.KmsException.builder()
                        .message("simulated KMS outage").build());

        MfaSeedEncryption encryption = new MfaSeedEncryption(kmsProperties, devEnvironment, kmsClient);

        assertThatThrownBy(() -> encryption.encrypt(randomSecret()))
                .isInstanceOf(MfaEncryptionException.class)
                .hasMessageContaining("generate");
    }

    // --- AC8: unsupported envelope version ---------------------------------------------------

    @Test
    void unsupportedVersionByteThrowsMfaEncryptionException() {
        MfaSeedEncryption encryption = new MfaSeedEncryption(localProperties, localEnvironment, null);
        byte[] envelope = encryption.encrypt(randomSecret());
        envelope[0] = 0x05;

        assertThatThrownBy(() -> encryption.decrypt(envelope))
                .isInstanceOf(MfaEncryptionException.class)
                .hasMessageContaining("Unsupported");
    }

    // --- Phase 8/9 fix: malformed / truncated envelopes must not leak raw JDK exceptions -----

    @Test
    void nullEnvelopeThrowsMfaEncryptionException() {
        MfaSeedEncryption encryption = new MfaSeedEncryption(localProperties, localEnvironment, null);

        assertThatThrownBy(() -> encryption.decrypt(null))
                .isInstanceOf(MfaEncryptionException.class);
    }

    @Test
    void emptyEnvelopeThrowsMfaEncryptionException() {
        MfaSeedEncryption encryption = new MfaSeedEncryption(localProperties, localEnvironment, null);

        assertThatThrownBy(() -> encryption.decrypt(new byte[0]))
                .isInstanceOf(MfaEncryptionException.class);
    }

    @Test
    void truncatedEnvelopeThrowsMfaEncryptionException() {
        MfaSeedEncryption encryption = new MfaSeedEncryption(localProperties, localEnvironment, null);
        // valid version byte, but far too short to contain the length/nonce/ciphertext fields
        byte[] truncated = {VERSION_LOCAL, 0x00, 0x00, 0x01, 0x02};

        assertThatThrownBy(() -> encryption.decrypt(truncated))
                .isInstanceOf(MfaEncryptionException.class);
    }

    // --- Phase 8/9 fix: version 0x00 must carry a zero-length wrapped key (ADR-0003) ---------

    @Test
    void localEnvelopeWithNonZeroWrappedKeyLengthIsRejected() {
        MfaSeedEncryption encryption = new MfaSeedEncryption(localProperties, localEnvironment, null);
        byte[] envelope = encryption.encrypt(randomSecret());
        // corrupt the wrapped-key-length field (bytes [1..2]) to claim 5 bytes on a local envelope
        ByteBuffer corrupted = ByteBuffer.wrap(envelope.clone());
        corrupted.put(0, VERSION_LOCAL);
        corrupted.putShort(1, (short) 5);

        assertThatThrownBy(() -> encryption.decrypt(corrupted.array()))
                .isInstanceOf(MfaEncryptionException.class)
                .hasMessageContaining("zero-length");
    }

    // --- AC7: thread-safety -------------------------------------------------------------------

    @Test
    void concurrentEncryptAndDecryptAreThreadSafe() throws Exception {
        MfaSeedEncryption encryption = new MfaSeedEncryption(localProperties, localEnvironment, null);
        int taskCount = 100;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Boolean>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                byte[] secret = randomSecret();
                tasks.add(() -> {
                    byte[] envelope = encryption.encrypt(secret);
                    return java.util.Arrays.equals(secret, encryption.decrypt(envelope));
                });
            }
            List<Future<Boolean>> results = pool.invokeAll(tasks);
            for (Future<Boolean> result : results) {
                assertThat(result.get()).isTrue();
            }
        } finally {
            pool.shutdown();
        }
    }

    // --- destroy() / KmsClient cleanup --------------------------------------------------------

    @Test
    void destroyClosesKmsClientWhenPresent() {
        MfaSeedEncryption encryption = new MfaSeedEncryption(kmsProperties, devEnvironment, kmsClient);

        encryption.destroy();

        verify(kmsClient, times(1)).close();
    }

    @Test
    void destroyDoesNothingWhenNoKmsClientWasBuilt() {
        MfaSeedEncryption encryption = new MfaSeedEncryption(localProperties, localEnvironment, null);

        assertThatCode(encryption::destroy).doesNotThrowAnyException();
    }

    @Test // Phase 11 finding #9 — AWS SDK clients document close() as idempotent; a repeated
          // destroy() (e.g. an odd Spring shutdown sequence) must stay safe
    void destroyIsSafeToCallTwice() {
        MfaSeedEncryption encryption = new MfaSeedEncryption(kmsProperties, devEnvironment, kmsClient);

        assertThatCode(() -> {
            encryption.destroy();
            encryption.destroy();
        }).doesNotThrowAnyException();

        verify(kmsClient, times(2)).close();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
