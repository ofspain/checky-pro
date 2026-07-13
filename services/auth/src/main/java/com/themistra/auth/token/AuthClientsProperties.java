package com.themistra.auth.token;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Static OAuth2 client provisioning (target-design §5: no runtime client-registration API in
 * Phase 1). Validated at startup — a missing or blank value fails boot instead of silently
 * defaulting (the reference's config-misread bug class, gap-analysis §3).
 */
@ConfigurationProperties(prefix = "themistra.auth.clients")
@Validated
public record AuthClientsProperties(

        @NotNull @Valid Spa spa,
        @NotEmpty @Valid List<ServiceClient> services
) {

    /** The React PWA: public client, authorization code + PKCE, no secret (D-002/D-012). */
    public record Spa(
            @NotBlank String clientId,
            @NotEmpty List<@NotBlank String> redirectUris,
            List<String> postLogoutRedirectUris
    ) {
        public Spa {
            postLogoutRedirectUris = postLogoutRedirectUris == null ? List.of() : postLogoutRedirectUris;
        }
    }

    /** Sibling services: client_credentials with scoped machine tokens (target-design §8). */
    public record ServiceClient(
            @NotBlank String clientId,
            @NotBlank String secret,
            @NotEmpty List<@NotBlank String> scopes
    ) {
    }
}
