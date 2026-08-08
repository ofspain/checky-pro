package com.themistra.auth.token;

import com.themistra.auth.authn.TotpAuthenticationProvider;
import com.themistra.auth.authz.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.util.List;
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

    // Not a field initializer on purpose (T20 Phase 8/10 fix): MockitoExtension injects @Mock
    // fields via a TestInstancePostProcessor callback, which runs AFTER JUnit constructs the test
    // instance - an inline `= new TokenClaimsCustomizer(roleService)` field initializer captures
    // roleService while it is still null, permanently. Every test exercising the roles/amr/acr path
    // NPE'd on this until it was moved into @BeforeEach (confirmed pre-existing and unrelated to
    // T20 by reproducing it identically on a clean pre-T20 stash before fixing it here).
    private TokenClaimsCustomizer customizer;

    @BeforeEach
    void setUp() {
        customizer = new TokenClaimsCustomizer(roleService);
    }

    private void withPrincipal(String name, org.springframework.security.core.GrantedAuthority... authorities) {
        Authentication principal = mock(Authentication.class);
        when(principal.getName()).thenReturn(name);
        when(principal.getAuthorities()).thenAnswer(inv -> List.of(authorities));
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

    @Test // R26, named test shouldIssueTokenWithOtpAmrAndAcrAfterMfa: the MFA outcome rides as a
          // synthetic granted authority (T20 Phase 8/9 fix #1) rather than a custom Authentication
          // subclass, specifically so it survives JdbcOAuth2AuthorizationService's Jackson-based
          // persistence between /login and /oauth2/token — this is the exact fact this test checks.
    void shouldIssueTokenWithOtpAmrAndAcrAfterMfa() {
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getAuthorizedScopes()).thenReturn(Set.of());
        withPrincipal(ACCOUNT_UUID.toString(), TotpAuthenticationProvider.OTP_VERIFIED_AUTHORITY);
        when(roleService.resolveEffectiveRoles(ACCOUNT_UUID)).thenReturn(Set.of("MERCHANT"));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        when(context.getClaims()).thenReturn(claims);

        customizer.customize(context);

        JwtClaimsSet built = claims.build();
        assertThat(built.<List<String>>getClaim("amr")).isEqualTo(List.of("pwd", "otp"));
        assertThat(built.<String>getClaim("acr")).isEqualTo("urn:themistra:acr:otp");
        // Phase 11 finding #6: roles come from RoleService, never from the login-time authorities
        // collection — the synthetic OTP_VERIFIED marker must never leak into this claim.
        assertThat(built.<List<String>>getClaim("roles")).doesNotContain("OTP_VERIFIED");
    }

    @Test // R26/R27, Phase 3/4 finding #10's resolution: SAS replays the same Authentication on a
          // refresh-token grant that it captured at the original interactive login, so the MFA
          // outcome must be preserved across refresh with no grant-type branching in production
          // code — this test proves that by using REFRESH_TOKEN as the grant type directly.
    void otpUsedIsPreservedOnARefreshTokenGrant() {
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        when(context.getAuthorizedScopes()).thenReturn(Set.of());
        withPrincipal(ACCOUNT_UUID.toString(), TotpAuthenticationProvider.OTP_VERIFIED_AUTHORITY);
        when(roleService.resolveEffectiveRoles(ACCOUNT_UUID)).thenReturn(Set.of("MERCHANT"));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        when(context.getClaims()).thenReturn(claims);

        customizer.customize(context);

        JwtClaimsSet built = claims.build();
        assertThat(built.<List<String>>getClaim("amr")).isEqualTo(List.of("pwd", "otp"));
        assertThat(built.<String>getClaim("acr")).isEqualTo("urn:themistra:acr:otp");
    }

    @Test // Sanity check that the marker authority is compared by value (SimpleGrantedAuthority's
          // own equals()), not by reference — matters because a Jackson round-trip through
          // JdbcOAuth2AuthorizationService will never hand back the exact same object instance.
    void otpUsedAuthorityMatchesAnEquivalentButDistinctInstance() {
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getAuthorizedScopes()).thenReturn(Set.of());
        withPrincipal(ACCOUNT_UUID.toString(), new SimpleGrantedAuthority("OTP_VERIFIED"));
        when(roleService.resolveEffectiveRoles(ACCOUNT_UUID)).thenReturn(Set.of());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        when(context.getClaims()).thenReturn(claims);

        customizer.customize(context);

        assertThat(claims.build().<List<String>>getClaim("amr")).isEqualTo(List.of("pwd", "otp"));
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
