package com.themistra.auth.token;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Plain JUnit, no Spring context (Kimi Phase 11 Gap 1). Exercises {@link JwksConfig#jwtEncoder}
 * exactly as declared — with a {@link JWKSource} of two RSA keys, neither carrying a {@code
 * keyUse} or {@code algorithm} (mirroring {@link SigningKeyMaterial}'s real construction, which
 * sets neither), the same shape a live key-rotation window produces. Before the Phase 9 gate fix,
 * {@code NimbusJwtEncoder}'s default {@code jwkSelector} would throw here instead of choosing —
 * this test fails on a naive revert of that fix.
 */
class JwksConfigTest {

    @Test // Phase 9 gate fix: CURRENT + PREVIOUS both configured must not break signing
    void jwtEncoderSignsSuccessfullyAndPicksTheFirstKeyWhenTwoKeysArePresent() throws Exception {
        RSAKey currentKey = generateRsaKey("current-kid");
        RSAKey previousKey = generateRsaKey("previous-kid");
        JWKSource<SecurityContext> jwkSource =
                new ImmutableJWKSet<>(new JWKSet(List.of(currentKey, previousKey)));

        JwtEncoder encoder = new JwksConfig().jwtEncoder(jwkSource);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://auth.themistra.test")
                .subject("test-subject")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        assertThatCode(() -> encoder.encode(JwtEncoderParameters.from(claims)))
                .as("must not throw JwtEncodingException for an ambiguous multi-key JWKSource")
                .doesNotThrowAnyException();

        String token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        assertThat(signedKeyId(token)).isEqualTo("current-kid");
    }

    private static String signedKeyId(String jwt) throws ParseException {
        return ((SignedJWT) com.nimbusds.jwt.JWTParser.parse(jwt)).getHeader().getKeyID();
    }

    private static RSAKey generateRsaKey(String kid) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        // Deliberately no .keyUse(...) or .algorithm(...) - matches SigningKeyMaterial.fromPem's
        // real construction exactly, which is what made both keys ambiguously match before the
        // Phase 9 fix.
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(kid)
                .build();
    }
}
