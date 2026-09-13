package com.themistra.crypto.attest;

import com.themistra.crypto.common.config.KmsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AC2/AC4/AC5 (frozen brief Phase 4) - unit tests for {@link KmsSigner}, plus the structural
 * source-scan tests for AC3/AC6/AC7 that lock in properties an interaction test can't express. */
@ExtendWith(MockitoExtension.class)
class KmsSignerTest {

    private static final Instant NOW = Instant.parse("2026-09-13T00:00:00Z");
    private static final byte[] DIGEST = new byte[32];
    private static final String KEY_ID_CONFIGURED = "alias/attestation-key";
    private static final String KEY_ID_FROM_KMS = "arn:aws:kms:us-east-1:111122223333:key/abc-123";

    @Mock
    private KmsClient kmsClient;

    private KmsSigner signer;

    @BeforeEach
    void setUp() {
        signer = new KmsSigner(new KmsProperties(KEY_ID_CONFIGURED), Clock.fixed(NOW, ZoneOffset.UTC), kmsClient);
    }

    @Test
    void signCallsKmsWithTheExpectedRequestShape() {
        when(kmsClient.sign(any(SignRequest.class))).thenReturn(SignResponse.builder()
                .signature(SdkBytes.fromByteArray("sig-bytes".getBytes()))
                .keyId(KEY_ID_FROM_KMS)
                .build());

        signer.sign(DIGEST);

        ArgumentCaptor<SignRequest> captor = ArgumentCaptor.forClass(SignRequest.class);
        verify(kmsClient).sign(captor.capture());
        SignRequest request = captor.getValue();
        assertThat(request.keyId()).isEqualTo(KEY_ID_CONFIGURED);
        assertThat(request.messageType()).isEqualTo(MessageType.DIGEST);
        assertThat(request.signingAlgorithm()).isEqualTo(SigningAlgorithmSpec.ECDSA_SHA_256);
        assertThat(request.message().asByteArray()).isEqualTo(DIGEST);
    }

    @Test
    void signMapsAResponseIntoTheExpectedSignatureResultFields() {
        when(kmsClient.sign(any(SignRequest.class))).thenReturn(SignResponse.builder()
                .signature(SdkBytes.fromByteArray("sig-bytes".getBytes()))
                .keyId(KEY_ID_FROM_KMS)
                .build());

        SignatureResult result = signer.sign(DIGEST);

        // Phase 3 Finding #4: kmsKeyId comes from the KMS response, not the configured input value.
        assertThat(result.kmsKeyId()).isEqualTo(KEY_ID_FROM_KMS);
        // Phase 3 Finding #5: standard RFC 4648 Base64, no line-wrapping/URL-safe variant.
        assertThat(result.signatureBase64()).isEqualTo(Base64.getEncoder().encodeToString("sig-bytes".getBytes()));
        // Phase 3 Finding #6: signedAt comes from the injected Clock, never a direct Instant.now().
        assertThat(result.signedAt()).isEqualTo(NOW);
    }

    @Test
    void aKmsExceptionPropagatesUnwrapped() {
        when(kmsClient.sign(any(SignRequest.class))).thenThrow(
                (KmsException) KmsException.builder().message("kms unavailable").build());

        assertThatThrownBy(() -> signer.sign(DIGEST))
                .isInstanceOf(KmsException.class)
                .hasMessage("kms unavailable");
    }

    // Phase 11 (Kimi) Gap 6: the frozen brief says ANY KmsClient.sign(...) exception propagates
    // uncaught, not just KmsException specifically - a parameterized test makes that contract
    // explicit against several distinct exception types a future refactor might treat differently.
    @ParameterizedTest
    @MethodSource("kmsClientExceptions")
    void anyKmsClientExceptionPropagatesUnwrapped(RuntimeException exception) {
        when(kmsClient.sign(any(SignRequest.class))).thenThrow(exception);

        assertThatThrownBy(() -> signer.sign(DIGEST)).isSameAs(exception);
    }

    static Stream<RuntimeException> kmsClientExceptions() {
        return Stream.of(
                (KmsException) KmsException.builder().message("kms unavailable").build(),
                SdkClientException.create("timeout"),
                new IllegalArgumentException("bad request"));
    }

    @Test
    void destroyClosesTheKmsClient() {
        signer.destroy();

        verify(kmsClient).close();
    }

    @Test
    void signatureResultIsPackagePrivate() {
        assertThat(Modifier.isPublic(SignatureResult.class.getModifiers()))
                .as("SignatureResult must stay package-private so nothing outside attest can depend "
                        + "on it without tripping the boundary rules")
                .isFalse();
    }

    @Test
    void rejectsNullDigestWithoutCallingKms() {
        assertThatNullPointerException().isThrownBy(() -> signer.sign(null));

        verify(kmsClient, never()).sign(any(SignRequest.class));
    }

    @Test
    void rejectsAWrongLengthDigestWithoutCallingKms() {
        assertThatIllegalArgumentException().isThrownBy(() -> signer.sign(new byte[16]));

        verify(kmsClient, never()).sign(any(SignRequest.class));
    }

    // --- Structural source-scan tests (frozen brief AC3/AC6/AC7) ---

    private static final List<String> KEY_MATERIAL_APIS = List.of(
            "java.security.KeyPairGenerator", "java.security.KeyStore", "javax.crypto.SecretKey",
            "java.security.PrivateKey");
    private static final List<String> HOST_ONLY_LITERALS = List.of(
            "file:", "/etc/", "localhost", "127.0.0.1");
    private static final Path KMS_SIGNER_SOURCE =
            Path.of("src/main/java/com/themistra/crypto/attest/KmsSigner.java");

    @Test
    void kmsSignerImportsNoKeyMaterialHandlingApi() {
        List<String> lines = readAllLines(KMS_SIGNER_SOURCE);
        for (String forbidden : KEY_MATERIAL_APIS) {
            assertThat(lines).noneMatch(line -> line.contains(forbidden));
        }
    }

    @Test
    void kmsSignerContainsNoHostOnlyLiteral() {
        List<String> lines = readAllLines(KMS_SIGNER_SOURCE);
        for (String literal : HOST_ONLY_LITERALS) {
            assertThat(lines).noneMatch(line -> line.contains(literal));
        }
    }

    @Test
    void kmsSignerDeclaresNoLoggerField() {
        boolean hasLoggerField = Arrays.stream(KmsSigner.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .anyMatch(typeName -> typeName.contains("Logger"));

        assertThat(hasLoggerField)
                .as("KmsSigner must declare no Logger field - the strongest guarantee it can never "
                        + "log the digest or signature")
                .isFalse();
    }

    private static List<String> readAllLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
