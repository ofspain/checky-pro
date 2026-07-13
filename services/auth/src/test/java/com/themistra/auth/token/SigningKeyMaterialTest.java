package com.themistra.auth.token;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SigningKeyMaterialTest {

    private static String generatePkcs8Pem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048); // smaller than prod's 3072 — faster tests, same code path
        KeyPair pair = generator.generateKeyPair();
        String base64 = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    @Test
    void loadsCurrentKeyWithCorrectKid() throws Exception {
        var current = new SigningKeysProperties.KeySlot("key-2026-a", generatePkcs8Pem());
        var properties = new SigningKeysProperties(current, null, true);

        List<RSAKey> keys = SigningKeyMaterial.load(properties);

        assertThat(keys).hasSize(1);
        assertThat(keys.getFirst().getKeyID()).isEqualTo("key-2026-a");
        assertThat(keys.getFirst().isPrivate()).isTrue();
    }

    @Test
    void currentIsFirstWhenBothConfigured_orderIsLoadBearing() throws Exception {
        var current = new SigningKeysProperties.KeySlot("current-kid", generatePkcs8Pem());
        var previous = new SigningKeysProperties.KeySlot("previous-kid", generatePkcs8Pem());
        var properties = new SigningKeysProperties(current, previous, true);

        List<RSAKey> keys = SigningKeyMaterial.load(properties);

        assertThat(keys).hasSize(2);
        assertThat(keys.get(0).getKeyID()).isEqualTo("current-kid");
        assertThat(keys.get(1).getKeyID()).isEqualTo("previous-kid");
    }

    @Test
    void previousOmittedWhenNotConfigured() throws Exception {
        var current = new SigningKeysProperties.KeySlot("only-kid", generatePkcs8Pem());
        var properties = new SigningKeysProperties(current, SigningKeysProperties.KeySlot.empty(), true);

        assertThat(SigningKeyMaterial.load(properties)).hasSize(1);
    }

    @Test
    void refusesToStartWithoutConfiguredKeyWhenRequired() {
        var properties = new SigningKeysProperties(null, null, true);

        assertThatThrownBy(() -> SigningKeyMaterial.load(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("require-configured");
    }

    @Test
    void fallsBackToEphemeralKeyOnlyWhenNotRequired() {
        var properties = new SigningKeysProperties(null, null, false);

        List<RSAKey> keys = SigningKeyMaterial.load(properties);

        assertThat(keys).hasSize(1);
        assertThat(keys.getFirst().getKeyID()).startsWith("dev-");
    }

    @Test
    void invalidPemNeverLeaksKeyMaterialInExceptionMessage() {
        var current = new SigningKeysProperties.KeySlot("bad-kid", "not-a-valid-pem-body");
        var properties = new SigningKeysProperties(current, null, true);

        assertThatThrownBy(() -> SigningKeyMaterial.load(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bad-kid")
                .hasMessageNotContaining("not-a-valid-pem-body");
    }
}
