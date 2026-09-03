package com.themistra.crypto.common;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11 Gap 7: {@link ResourceServerConfigIntegrationTest} proves the
 * {@code authorizeHttpRequests}/{@code hasAuthority} rules via {@code SecurityMockMvcRequestPostProcessors.jwt()},
 * which bypasses real claim-to-authority conversion entirely. This test instead exercises Spring
 * Security's actual default {@link JwtGrantedAuthoritiesConverter} - the one
 * {@link ResourceServerConfig} relies on with no custom converter - directly against a JSON-array
 * {@code scope} claim shaped exactly like {@code contracts/api/token-claims.md} Path 2
 * (service-to-service {@code client_credentials} tokens), pinning the load-bearing assumption
 * behind R27's authorization check.
 */
class ScopeClaimConversionTest {

    @Test
    void convertsJsonArrayScopeClaimToScopePrefixedAuthorities() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("scope", List.of("internal.crypto:write", "something.else"))
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(600))
                .build();

        Collection<GrantedAuthority> authorities = new JwtGrantedAuthoritiesConverter().convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("SCOPE_internal.crypto:write", "SCOPE_something.else");
    }

    @Test
    void producesNoAuthoritiesWhenScopeClaimAbsent() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "some-service-client")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(600))
                .build();

        Collection<GrantedAuthority> authorities = new JwtGrantedAuthoritiesConverter().convert(jwt);

        assertThat(authorities).isEmpty();
    }
}
