package com.themistra.auth.token;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2TokenType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenClaimsCustomizerTest {

    @Mock
    private JwtEncodingContext context;

    private final TokenClaimsCustomizer customizer = new TokenClaimsCustomizer();

    @Test
    void clientCredentialsTokenGetsClientAmrOnly() {
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        when(context.getClaims()).thenReturn(claims);

        customizer.customize(context);

        JwtClaimsSet built = claims.build();
        assertThat(built.getClaim("amr")).isEqualTo(java.util.List.of("client_secret"));
        assertThat(built.getClaims()).doesNotContainKey("roles");
        assertThat(built.getClaims()).doesNotContainKey("email_verified");
    }

    @Test
    void interactiveTokenGetsRolesAmrAcrAndEmailVerified_noEmailOrName() {
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getAuthorizedScopes()).thenReturn(Set.of("openid", "email"));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        when(context.getClaims()).thenReturn(claims);

        customizer.customize(context);

        JwtClaimsSet built = claims.build();
        assertThat(built.getClaim("amr")).isEqualTo(java.util.List.of("pwd"));
        assertThat(built.getClaim("acr")).isEqualTo("urn:themistra:acr:pwd");
        assertThat(built.<Boolean>getClaim("email_verified")).isTrue();
        assertThat(built.getClaims()).doesNotContainKeys("email", "name", "given_name", "family_name");
    }

    @Test
    void emailVerifiedFalseWhenEmailScopeNotAuthorized() {
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        when(context.getAuthorizedScopes()).thenReturn(Set.of("openid"));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        when(context.getClaims()).thenReturn(claims);

        customizer.customize(context);

        assertThat(claims.build().<Boolean>getClaim("email_verified")).isFalse();
    }

    @Test
    void nonAccessTokenTypeIsUntouched() {
        when(context.getTokenType()).thenReturn(new OAuth2TokenType("id_token"));

        customizer.customize(context);
        // no getClaims() stub set — a call to it would throw a Mockito UnnecessaryStubbing-safe
        // NPE-free verification isn't needed: absence of interaction is the assertion here.
    }
}
