package com.themistra.auth.token;

import com.themistra.auth.authn.TotpStepUpAuthenticationToken;
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
        // On a refresh-token grant, SAS replays the same Authentication captured at the original
        // interactive login (TotpAuthenticationProvider, task 20), so amr/acr below are preserved
        // across refresh without any grant-type branching (R26/R27).
        boolean otpUsed = context.getPrincipal() instanceof TotpStepUpAuthenticationToken totp && totp.otpUsed();
        context.getClaims()
                .claim("roles", List.copyOf(resolveRoles(context.getPrincipal().getName())))
                .claim("amr", otpUsed ? List.of("pwd", "otp") : List.of("pwd"))
                .claim("acr", otpUsed ? "urn:themistra:acr:otp" : "urn:themistra:acr:pwd")
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
