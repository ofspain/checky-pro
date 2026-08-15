package com.themistra.auth.token;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;

import java.util.List;

/**
 * Publishes CURRENT + PREVIOUS keys at /oauth2/jwks (D-011). SigningKeyMaterial guarantees
 * CURRENT is first — the JwtEncoder signs with whichever key its selector resolves first, so
 * key order here is load-bearing, not cosmetic. {@link SigningKeysProperties} is picked up by
 * the application class's {@code @ConfigurationPropertiesScan} — not re-declared here.
 */
@Configuration
public class JwksConfig {

    @Bean
    public JWKSource<SecurityContext> jwkSource(SigningKeysProperties properties) {
        List<RSAKey> keys = SigningKeyMaterial.load(properties);
        JWKSet jwkSet = new JWKSet(List.copyOf(keys));
        return new ImmutableJWKSet<>(jwkSet);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * Declared explicitly (T25, D1) so {@code ApiKeyTokenIssuer} can mint a JWT outside a SAS
     * grant. Behaviourally identical to what SAS would otherwise build for itself:
     * {@code OAuth2ConfigurerUtils.getJwtEncoder} looks for exactly this bean type before falling
     * back to {@code new NimbusJwtEncoder(jwkSource)} — same class, same {@link JWKSource}, same
     * CURRENT-key-first ordering from {@link SigningKeyMaterial} — so declaring it here does not
     * change how any existing grant (password, refresh, client-credentials) is signed.
     */
    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }
}
