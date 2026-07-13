package com.themistra.auth.token;

import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Turns configured PEM material into JWKS entries. Ordering contract: the CURRENT key is first —
 * the JWT encoder selects the first eligible key, so first == signer; everything after exists
 * only so resource servers can verify tokens signed before rotation.
 */
public final class SigningKeyMaterial {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyMaterial.class);
    private static final int RSA_KEY_BITS = 3072;   // target-design §6

    private SigningKeyMaterial() {
    }

    public static List<RSAKey> load(SigningKeysProperties properties) {
        if (properties.current().configured()) {
            List<RSAKey> keys = new ArrayList<>();
            keys.add(fromPem(properties.current()));
            if (properties.previous().configured()) {
                keys.add(fromPem(properties.previous()));
            }
            return List.copyOf(keys);
        }

        if (properties.requireConfigured()) {
            throw new IllegalStateException(
                    "No JWT signing key configured but themistra.auth.jwt.require-configured=true. "
                            + "Refusing to start with an ephemeral key outside local development.");
        }

        log.warn("No JWT signing key configured — generating an EPHEMERAL dev key. "
                + "Tokens will not survive a restart. Never acceptable outside local development.");
        return List.of(ephemeral());
    }

    static RSAKey fromPem(SigningKeysProperties.KeySlot slot) {
        try {
            String base64 = slot.privateKeyPem()
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(base64);

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey =
                    (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(privateKey instanceof RSAPrivateCrtKey crtKey)) {
                throw new IllegalStateException("RSA private key lacks CRT parameters; "
                        + "cannot derive the public key for kid=" + slot.kid());
            }
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                    new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));

            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(slot.kid())
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // never include key material in the failure — kid only
            throw new IllegalStateException(
                    "Invalid JWT signing key material for kid=" + slot.kid(), e);
        }
    }

    private static RSAKey ephemeral() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_KEY_BITS);
            KeyPair pair = generator.generateKeyPair();
            String kid = "dev-" + HexFormat.of().formatHex(
                    UUID.randomUUID().toString().getBytes()).substring(0, 8);
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(kid)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate ephemeral dev key", e);
        }
    }
}
