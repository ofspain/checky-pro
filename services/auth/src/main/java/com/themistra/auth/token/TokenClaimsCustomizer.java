package com.themistra.auth.token;

import com.themistra.auth.authz.RoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The claims contract with resource servers (target-design §6, contracts/api/token-claims.md).
 * Deliberately minimal: {@code roles}, {@code amr}, {@code acr}, {@code email_verified} — no
 * email or name in access tokens (those live in the id_token/userinfo).
 */
@Component
public class TokenClaimsCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private static final Logger log = LoggerFactory.getLogger(TokenClaimsCustomizer.class);

    private final RoleService roleService;

    public TokenClaimsCustomizer(RoleService roleService) {
        this.roleService = roleService;
    }

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

        // interactive (authorization_code / refresh_token) principal — 'sub' is the account UUID
        // (AccountUserDetailsService), so effective roles are resolved and template-expanded
        // fresh on every issuance (never cached: a template edit affects future tokens only).
        context.getClaims()
                .claim("roles", List.copyOf(resolveRoles(context.getPrincipal().getName())))
                .claim("amr", List.of("pwd"))             // MFA stage appends "otp" when MFA passed
                .claim("acr", "urn:themistra:acr:pwd")
                .claim("email_verified",
                        context.getAuthorizedScopes().contains(OidcScopes.EMAIL));
    }

    private Set<String> resolveRoles(String principalName) {
        try {
            return roleService.resolveEffectiveRoles(UUID.fromString(principalName));
        } catch (IllegalArgumentException e) {
            // defensive only: every interactive principal name is an account UUID by construction
            log.warn("Interactive token principal was not a UUID: {}", principalName);
            return Set.of();
        }
    }
}
