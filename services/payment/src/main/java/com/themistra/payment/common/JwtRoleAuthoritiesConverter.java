package com.themistra.payment.common;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Maps the token's {@code roles} claim (`contracts/api/token-claims.md`) into Spring Security
 * authorities, so {@code @PreAuthorize("hasRole('MERCHANT')")} means something. Without this,
 * resource-server default behavior derives authorities only from the {@code scope} claim — this
 * service's first authority-restricted path (T06) needs a role, not a scope. Mirrors
 * {@code auth-service}'s own identical {@code JwtRoleAuthoritiesConverter} verbatim.
 */
@Component
public class JwtRoleAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
