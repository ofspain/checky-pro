package com.themistra.auth.mfa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-256-GCM envelope encryption of TOTP seeds with a KMS-enveloped data key (L14, ADR-0003) —
 * the one narrow, named exception to D-010 permitting AWS SDK use in this service (D-025). Byte
 * layout and the local-dev fallback rule are fixed in ADR-0003, not re-specified here beyond what
 * the code needs. The KMS client is built here, not as a separate {@code @Bean} — it is skipped
 * entirely in local-key mode so local dev needs no AWS credentials at all (default AWS credential
 * / region provider chains apply otherwise, e.g. IRSA in EKS).
 *
 * <p><b>Guard mechanism (deviates from the frozen brief's literal wording — see implementation
 * notes):</b> {@code seedKekRequired} gates the local-key fallback exactly like {@code
 * SigningKeysProperties#requireConfigured} gates the ephemeral JWT dev key in {@link
 * com.themistra.auth.token.SigningKeyMaterial} — off by default, explicitly turned on by ops only
 * in deployed environments. ADR-0003 frames this as "active Spring profile is local", but no
 * active profile is set anywhere in this codebase today (no docker-compose, no
 * {@code @ActiveProfiles} on any existing test); matching that literally would fail closed for
 * every current {@code @SpringBootTest}. This boolean-flag shape mirrors the one L13 guard this
 * service has already shipped and tested.</p>
 */
@Component
public class MfaSeedEncryption {

    private static final Logger log = LoggerFactory.getLogger(MfaSeedEncryption.class);

    private static final byte VERSION_LOCAL = 0x00;
    private static final byte VERSION_KMS = 0x01;
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    /**
     * Fixed, local-only AES-256 key (ADR-0003). Used only when {@code seed-kek-arn} is blank and
     * {@code seed-kek-required} is false; never committed as anything a deployed environment
     * could inherit — the constructor guard below refuses to boot rather than let a version-0x00
     * envelope be produced when {@code seed-kek-required=true}.
     */
    private static final byte[] LOCAL_DEV_KEY = {
            0x1f, 0x4b, 0x2a, 0x7e, 0x3c, 0x5d, 0x0a, 0x6f,
            (byte) 0x88, 0x12, 0x39, (byte) 0xab, 0x44, 0x5e, 0x77, 0x1c,
            0x2d, (byte) 0x9e, 0x63, 0x0f, (byte) 0xd4, 0x58, 0x21, (byte) 0xc7,
            0x3a, 0x6b, (byte) 0x90, 0x15, (byte) 0xe2, 0x4f, 0x7a, (byte) 0xb1
    };

    private final MfaProperties properties;
    private final KmsClient kmsClient;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public MfaSeedEncryption(MfaProperties properties) {
        this(properties, resolveKmsClient(properties));
    }

    /** Test seam: lets unit tests inject a mocked {@link KmsClient} directly. */
    MfaSeedEncryption(MfaProperties properties, KmsClient kmsClient) {
        validateGuard(properties);
        this.properties = properties;
        this.kmsClient = kmsClient;
    }

    private static void validateGuard(MfaProperties properties) {
        if (properties.seedKekRequired() && isBlank(properties.seedKekArn())) {
            throw new IllegalStateException(
                    "themistra.auth.mfa.seed-kek-arn is blank but seed-kek-required=true. "
                            + "Refusing to start with a local-only TOTP seed key outside local development.");
        }
    }

    private static KmsClient resolveKmsClient(MfaProperties properties) {
        validateGuard(properties);
        if (isBlank(properties.seedKekArn())) {
            log.warn("No themistra.auth.mfa.seed-kek-arn configured — using the fixed LOCAL-DEV "
                    + "AES key for TOTP seed encryption. Never acceptable outside local development.");
            return null;
        }
        return KmsClient.builder().build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Encrypts a raw TOTP secret into ADR-0003's versioned envelope format. */
    public byte[] encrypt(byte[] rawSecret) {
        return kmsClient == null ? encryptLocal(rawSecret) : encryptKms(rawSecret);
    }

    /** Decrypts a previously-produced envelope, returning the original raw secret. */
    public byte[] decrypt(byte[] envelope) {
        byte version = envelope[0];
        return switch (version) {
            case VERSION_LOCAL -> decryptLocal(envelope);
            case VERSION_KMS -> decryptKms(envelope);
            default -> throw new MfaEncryptionException("Unsupported MFA seed envelope version: " + version);
        };
    }

    private byte[] encryptLocal(byte[] rawSecret) {
        byte[] nonce = newNonce();
        byte[] ciphertext = gcmEncrypt(new SecretKeySpec(LOCAL_DEV_KEY, "AES"), nonce, rawSecret);
        return assembleEnvelope(VERSION_LOCAL, new byte[0], nonce, ciphertext);
    }

    private byte[] decryptLocal(byte[] envelope) {
        Envelope parsed = parseEnvelope(envelope);
        return gcmDecrypt(new SecretKeySpec(LOCAL_DEV_KEY, "AES"), parsed.nonce(), parsed.ciphertext());
    }

    private byte[] encryptKms(byte[] rawSecret) {
        GenerateDataKeyResponse dataKey = kmsClient.generateDataKey(GenerateDataKeyRequest.builder()
                .keyId(properties.seedKekArn())
                .keySpec(DataKeySpec.AES_256)
                .build());

        byte[] plaintextKey = dataKey.plaintext().asByteArray();
        byte[] wrappedKey = dataKey.ciphertextBlob().asByteArray();
        try {
            byte[] nonce = newNonce();
            byte[] ciphertext = gcmEncrypt(new SecretKeySpec(plaintextKey, "AES"), nonce, rawSecret);
            return assembleEnvelope(VERSION_KMS, wrappedKey, nonce, ciphertext);
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);
        }
    }

    private byte[] decryptKms(byte[] envelope) {
        Envelope parsed = parseEnvelope(envelope);

        DecryptResponse decrypted;
        try {
            decrypted = kmsClient.decrypt(DecryptRequest.builder()
                    .keyId(properties.seedKekArn())
                    .ciphertextBlob(SdkBytes.fromByteArray(parsed.wrappedKey()))
                    .build());
        } catch (RuntimeException e) {
            throw new MfaEncryptionException("Failed to unwrap MFA seed data key via KMS", e);
        }

        byte[] plaintextKey = decrypted.plaintext().asByteArray();
        try {
            return gcmDecrypt(new SecretKeySpec(plaintextKey, "AES"), parsed.nonce(), parsed.ciphertext());
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);
        }
    }

    private byte[] newNonce() {
        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);
        return nonce;
    }

    private static byte[] gcmEncrypt(SecretKeySpec key, byte[] nonce, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            return cipher.doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    private static byte[] gcmDecrypt(SecretKeySpec key, byte[] nonce, byte[] ciphertextAndTag) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            return cipher.doFinal(ciphertextAndTag);
        } catch (GeneralSecurityException e) {
            throw new MfaEncryptionException("AES-GCM decryption failed", e);
        }
    }

    private static byte[] assembleEnvelope(byte version, byte[] wrappedKey, byte[] nonce, byte[] ciphertext) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 2 + wrappedKey.length + nonce.length + ciphertext.length);
        buffer.put(version);
        buffer.putShort((short) wrappedKey.length);
        buffer.put(wrappedKey);
        buffer.put(nonce);
        buffer.put(ciphertext);
        return buffer.array();
    }

    private static Envelope parseEnvelope(byte[] envelope) {
        ByteBuffer buffer = ByteBuffer.wrap(envelope);
        buffer.get(); // version byte already dispatched on by the caller
        int wrappedKeyLength = Short.toUnsignedInt(buffer.getShort());
        byte[] wrappedKey = new byte[wrappedKeyLength];
        buffer.get(wrappedKey);
        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        buffer.get(nonce);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        return new Envelope(wrappedKey, nonce, ciphertext);
    }

    private record Envelope(byte[] wrappedKey, byte[] nonce, byte[] ciphertext) {
    }
}
