package com.themistra.auth.token;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The claims contract with resource servers (target-design §6, contracts/api/token-claims.md).
 * Deliberately minimal: {@code roles}, {@code amr}, {@code acr}, {@code email_verified} — no
 * email or name in access tokens (those live in the id_token/userinfo). {@code roles} is a
 * placeholder authority list here; the RBAC stage replaces it with real DB-sourced roles —
 * tracked as an explicit interim, not a silent gap.
 */
@Component
public class TokenClaimsCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    @Override
    public void customize(JwtEncodingContext context) {
        if (context.getTokenType().getValue().equals("access_token")) {
            customizeAccessToken(context);
        }
    }

    private void customizeAccessToken(JwtEncodingContext context) {
        AuthorizationGrantType grantType = context.getAuthorizationGrantType();

        if (grantType.equals(AuthorizationGrantType.CLIENT_CREDENTIALS)) {
            context.getClaims().claim("amr", List.of("client_secret"));
            return;
        }

        // interactive (authorization_code / refresh_token) principal
        context.getClaims()
                .claim("roles", List.of())               // RBAC stage: real template-expanded roles
                .claim("amr", List.of("pwd"))             // MFA stage appends "otp" when MFA passed
                .claim("acr", "urn:themistra:acr:pwd")
                .claim("email_verified",
                        context.getAuthorizedScopes().contains(OidcScopes.EMAIL));
    }
}
