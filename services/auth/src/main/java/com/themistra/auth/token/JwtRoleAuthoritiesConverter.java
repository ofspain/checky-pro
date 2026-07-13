package com.themistra.auth.token;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Maps the token's {@code roles} claim (TokenClaimsCustomizer, RBAC stage) into Spring Security
 * authorities, so {@code @PreAuthorize("hasRole('ADMIN')")} means something. Without this,
 * resource-server default behavior derives authorities only from the {@code scope} claim — our
 * roles would be present in the token but invisible to method security.
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
