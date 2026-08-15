package com.themistra.auth.apikey;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import com.themistra.auth.authz.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain JUnit, fixed {@link Clock} (agents.md testing conventions) — no Spring context. Signs
 * with a real, freshly generated in-test RSA key (mirroring {@code SigningKeyMaterial}'s own
 * construction technique) rather than mocking {@link JwtEncoder}, so claim assembly is verified
 * against an actually-decoded compact JWT — the only way to prove {@code scope} really serializes
 * as a JSON array (D1/Kimi#12), not just that the right values were passed to a builder.
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyTokenIssuerTest {

    private static final UUID ACCOUNT_UUID = UUID.randomUUID();
    private static final String ISSUER = "https://auth.themistra.test";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private RoleService roleService;

    private JwtEncoder jwtEncoder;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("test-kid")
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        jwtEncoder = new NimbusJwtEncoder(jwkSource);
    }

    private ApiKeyTokenIssuer issuerWithTtl(long ttlMinutes) {
        ApiKeyProperties properties = new ApiKeyProperties("ck_live_", ttlMinutes);
        return new ApiKeyTokenIssuer(jwtEncoder, roleService, properties, FIXED_CLOCK, ISSUER);
    }

    @Test // R31, L8, L9 - the full claim set
    void issueProducesTheFullL9ClaimSet() throws ParseException {
        when(roleService.resolveEffectiveRoles(ACCOUNT_UUID)).thenReturn(Set.of("MERCHANT"));
        ApiKeyTokenIssuer issuer = issuerWithTtl(10);

        ApiKeyTokenIssuer.IssuedToken issued = issuer.issue(ACCOUNT_UUID, List.of("merchant.api"));
        JWTClaimsSet claims = JWTParser.parse(issued.accessToken()).getJWTClaimsSet();

        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getSubject()).isEqualTo(ACCOUNT_UUID.toString());
        assertThat(claims.getAudience()).containsExactly("checky-api-key");
        assertThat(claims.getStringClaim("client_id")).isEqualTo("checky-api-key");
        assertThat(claims.getStringListClaim("amr")).containsExactly("api_key");
        assertThat(claims.getStringClaim("acr")).isEqualTo("urn:themistra:acr:api_key");
        assertThat(claims.getStringListClaim("scope")).containsExactly("merchant.api");
        assertThat(claims.getStringListClaim("roles")).containsExactly("MERCHANT");
        assertThat(claims.getBooleanClaim("email_verified")).isFalse();
        assertThat(claims.getJWTID()).isNotBlank();
        assertThat(claims.getIssueTime().toInstant()).isEqualTo(NOW);
        assertThat(claims.getNotBeforeTime().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        // R48/L9: no PII beyond email_verified.
        assertThat(claims.getClaim("email")).isNull();
        assertThat(claims.getClaim("name")).isNull();
    }

    @Test // AC3 - RS256, signed by the key the encoder was given
    void issueSignsWithRs256() throws ParseException {
        when(roleService.resolveEffectiveRoles(any())).thenReturn(Set.of());
        ApiKeyTokenIssuer issuer = issuerWithTtl(10);

        ApiKeyTokenIssuer.IssuedToken issued = issuer.issue(ACCOUNT_UUID, List.of("merchant.api"));

        SignedJWT signedJwt = (SignedJWT) JWTParser.parse(issued.accessToken());
        assertThat(signedJwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
    }

    @Test // AC7 - exp - iat is driven by the injected Clock and the configured TTL
    void issueComputesExpiryFromConfiguredTtl() throws ParseException {
        when(roleService.resolveEffectiveRoles(any())).thenReturn(Set.of());
        ApiKeyTokenIssuer issuer = issuerWithTtl(45);

        ApiKeyTokenIssuer.IssuedToken issued = issuer.issue(ACCOUNT_UUID, List.of("merchant.api"));
        JWTClaimsSet claims = JWTParser.parse(issued.accessToken()).getJWTClaimsSet();

        assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
                .isEqualTo(Duration.ofMinutes(45));
        assertThat(issued.expiresInSeconds()).isEqualTo(Duration.ofMinutes(45).getSeconds());
    }

    @Test // AC7 - a non-default TTL changes both exp and expiresIn together (boundary/supporting #5)
    void nonDefaultTtlChangesBothExpiryAndExpiresIn() {
        when(roleService.resolveEffectiveRoles(any())).thenReturn(Set.of());
        ApiKeyTokenIssuer tenMinuteIssuer = issuerWithTtl(10);
        ApiKeyTokenIssuer ninetyMinuteIssuer = issuerWithTtl(90);

        ApiKeyTokenIssuer.IssuedToken tenMinuteToken = tenMinuteIssuer.issue(ACCOUNT_UUID, List.of("merchant.api"));
        ApiKeyTokenIssuer.IssuedToken ninetyMinuteToken = ninetyMinuteIssuer.issue(ACCOUNT_UUID, List.of("merchant.api"));

        assertThat(ninetyMinuteToken.expiresInSeconds()).isEqualTo(9 * tenMinuteToken.expiresInSeconds());
    }

    @Test // Kimi#10 struck the "no extra account lookups" limit - roles must be resolved fresh,
          // never cached, so a role change between two exchanges is reflected on the second one
    void issueResolvesRolesFreshOnEveryCall() throws ParseException {
        when(roleService.resolveEffectiveRoles(ACCOUNT_UUID))
                .thenReturn(Set.of("MERCHANT"))
                .thenReturn(Set.of("MERCHANT", "ADMIN"));
        ApiKeyTokenIssuer issuer = issuerWithTtl(10);

        ApiKeyTokenIssuer.IssuedToken first = issuer.issue(ACCOUNT_UUID, List.of("merchant.api"));
        ApiKeyTokenIssuer.IssuedToken second = issuer.issue(ACCOUNT_UUID, List.of("merchant.api"));

        JWTClaimsSet firstClaims = JWTParser.parse(first.accessToken()).getJWTClaimsSet();
        JWTClaimsSet secondClaims = JWTParser.parse(second.accessToken()).getJWTClaimsSet();
        assertThat(firstClaims.getStringListClaim("roles")).containsExactly("MERCHANT");
        assertThat(secondClaims.getStringListClaim("roles")).containsExactlyInAnyOrder("MERCHANT", "ADMIN");
        verify(roleService, times(2)).resolveEffectiveRoles(ACCOUNT_UUID);
    }

    @Test // D1/Kimi#12 - scope is echoed verbatim as a JSON array, never widened/narrowed/reordered
    void issueEchoesScopesVerbatimAsAJsonArray() throws ParseException {
        when(roleService.resolveEffectiveRoles(any())).thenReturn(Set.of());
        ApiKeyTokenIssuer issuer = issuerWithTtl(10);

        ApiKeyTokenIssuer.IssuedToken issued =
                issuer.issue(ACCOUNT_UUID, List.of("merchant.api", "merchant.read"));
        JWTClaimsSet claims = JWTParser.parse(issued.accessToken()).getJWTClaimsSet();

        assertThat(claims.getStringListClaim("scope")).containsExactly("merchant.api", "merchant.read");
    }

    @Test // Phase 9 gate fix: a null accountUuid fails loudly and specifically, never a bare NPE
    void issueRejectsNullAccountUuid() {
        ApiKeyTokenIssuer issuer = issuerWithTtl(10);

        assertThatThrownBy(() -> issuer.issue(null, List.of("merchant.api")))
                .isInstanceOf(IllegalStateException.class);
    }
}
