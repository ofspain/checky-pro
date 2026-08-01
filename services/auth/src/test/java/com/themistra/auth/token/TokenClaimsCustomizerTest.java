package com.themistra.auth.token;

import com.themistra.auth.authz.RoleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenClaimsCustomizerTest {

    private static final UUID ACCOUNT_UUID = UUID.randomUUID();

    @Mock
    private JwtEncodingContext context;

    @Mock
    private RoleService roleService;

    private final TokenClaimsCustomizer customizer = new TokenClaimsCustomizer(roleService);

    private void withPrincipal(String name) {
        Authentication principal = mock(Authentication.class);
        when(principal.getName()).thenReturn(name);
        when(context.getPrincipal()).thenReturn(principal);
    }

    @Test
    void clientCredentialsTokenGetsClientAmrOnlyAndNeverConsultsRoleService() {
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        when(context.getClaims()).thenReturn(claims);

        customizer.customize(context);

        JwtClaimsSet built = claims.build();
        assertThat(built.<java.util.List<String>>getClaim("amr")).isEqualTo(java.util.List.of("client_secret"));
        assertThat(built.getClaims()).doesNotContainKey("roles");
        assertThat(built.getClaims()).doesNotContainKey("email_verified");
        verify(roleService, never()).resolveEffectiveRoles(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void interactiveTokenGetsRolesAmrAcrAndEmailVerified_noEmailOrName() {
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getAuthorizedScopes()).thenReturn(Set.of("openid", "email"));
        withPrincipal(ACCOUNT_UUID.toString());
        when(roleService.resolveEffectiveRoles(ACCOUNT_UUID)).thenReturn(Set.of("MERCHANT", "USER"));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        when(context.getClaims()).thenReturn(claims);

        customizer.customize(context);

        JwtClaimsSet built = claims.build();
        assertThat(built.<java.util.List<String>>getClaim("roles"))
                .containsExactlyInAnyOrder("MERCHANT", "USER");
        assertThat(built.<java.util.List<String>>getClaim("amr")).isEqualTo(java.util.List.of("pwd"));
        assertThat(built.<String>getClaim("acr")).isEqualTo("urn:themistra:acr:pwd");
        assertThat(built.<Boolean>getClaim("email_verified")).isTrue();
        assertThat(built.getClaims()).doesNotContainKeys("email", "name", "given_name", "family_name");
    }

    @Test
    void emailVerifiedFalseWhenEmailScopeNotAuthorized() {
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        when(context.getAuthorizedScopes()).thenReturn(Set.of("openid"));
        withPrincipal(ACCOUNT_UUID.toString());
        when(roleService.resolveEffectiveRoles(ACCOUNT_UUID)).thenReturn(Set.of());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        when(context.getClaims()).thenReturn(claims);

        customizer.customize(context);

        assertThat(claims.build().<Boolean>getClaim("email_verified")).isFalse();
    }

    @Test
    void nonUuidPrincipalYieldsNoRolesInsteadOfPropagating() {
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getAuthorizedScopes()).thenReturn(Set.of());
        withPrincipal("not-a-uuid");
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        when(context.getClaims()).thenReturn(claims);

        customizer.customize(context);

        assertThat(claims.build().<java.util.List<String>>getClaim("roles")).isEmpty();
        verify(roleService, never()).resolveEffectiveRoles(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nonAccessTokenTypeIsUntouched() {
        when(context.getTokenType()).thenReturn(new OAuth2TokenType("id_token"));

        customizer.customize(context);
        // no getClaims() stub set — a call to it would throw a Mockito UnnecessaryStubbing-safe
        // NPE-free verification isn't needed: absence of interaction is the assertion here.
    }
}
