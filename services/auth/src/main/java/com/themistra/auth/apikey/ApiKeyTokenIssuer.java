package com.themistra.auth.apikey;

import com.themistra.auth.authz.RoleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mints the short-lived access token {@code POST /api-keys/token} returns (T25, L8/L9, D1).
 *
 * <p>No SAS grant runs for a key exchange, so {@link com.themistra.auth.token.TokenClaimsCustomizer}
 * cannot apply here (that customizer only fires inside {@code JwtGenerator} for an
 * {@code OAuth2TokenCustomizer<JwtEncodingContext>}, which requires a {@code RegisteredClient} and
 * an {@code Authentication} principal neither of which exist for a key exchange) — this class
 * assembles the full L9 claim set itself, honouring L8's claim contract via a gate-approved
 * assembly-path deviation (frozen brief D1). {@code TokenClaimsCustomizer} is not modified.</p>
 *
 * <p>Singleton, no mutable instance state — {@link JwtEncoder} (backed by
 * {@code NimbusJwtEncoder}) is thread-safe and held as a field, safe for concurrent unauthenticated
 * requests (frozen brief, Constraints: Thread-safety).</p>
 */
@Component
public class ApiKeyTokenIssuer {

    /** Fixed per D2: no {@code RegisteredClient} is seeded for API-key exchanges, so both
     * {@code client_id} and {@code aud} identify this synthetic, unbacked client literally. */
    private static final String CLIENT_ID = "checky-api-key";
    private static final String ACR_API_KEY = "urn:themistra:acr:api_key";
    private static final List<String> AMR_API_KEY = List.of("api_key");

    private final JwtEncoder jwtEncoder;
    private final RoleService roleService;
    private final ApiKeyProperties apiKeyProperties;
    private final Clock clock;
    private final String issuer;

    public ApiKeyTokenIssuer(JwtEncoder jwtEncoder, RoleService roleService,
                              ApiKeyProperties apiKeyProperties, Clock clock,
                              @Value("${spring.security.oauth2.authorizationserver.issuer}") String issuer) {
        this.jwtEncoder = jwtEncoder;
        this.roleService = roleService;
        this.apiKeyProperties = apiKeyProperties;
        this.clock = clock;
        this.issuer = issuer;
    }

    /**
     * Signs and returns a fresh access token for {@code accountUuid} (R31). {@code scopes} is the
     * exchanged key's own {@code scopes} column, echoed verbatim into the {@code scope} claim as a
     * JSON array (D1/Kimi#12) — never widened or narrowed here. {@code roles} is resolved fresh on
     * every call via {@link RoleService#resolveEffectiveRoles}, never cached (frozen brief:
     * Performance constraint, Kimi#10 struck the "no extra account lookups" limit).
     */
    public IssuedToken issue(UUID accountUuid, List<String> scopes) {
        if (accountUuid == null) {
            // ApiKeyService.exchange documents this as "should not happen in practice" (a
            // best-effort account lookup on an otherwise-successful exchange) — surfaced here as
            // an explicit, diagnosable failure rather than a bare NPE. Still yields the same
            // opaque 500 via ApiExceptionHandler.onUnexpected, never the uniform 401 (D5).
            throw new IllegalStateException("Exchanged API key has no resolvable owning account");
        }
        Instant now = clock.instant();
        Instant expiry = now.plus(Duration.ofMinutes(apiKeyProperties.tokenTtlMinutes()));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(accountUuid.toString())
                .audience(List.of(CLIENT_ID))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(expiry)
                .id(UUID.randomUUID().toString())
                .claim("scope", List.copyOf(scopes))
                .claim("roles", List.copyOf(roleService.resolveEffectiveRoles(accountUuid)))
                .claim("client_id", CLIENT_ID)
                .claim("amr", AMR_API_KEY)
                .claim("acr", ACR_API_KEY)
                .claim("email_verified", false)
                .build();

        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        long expiresInSeconds = Duration.between(now, expiry).getSeconds();
        return new IssuedToken(accessToken, expiresInSeconds);
    }

    public record IssuedToken(String accessToken, long expiresInSeconds) {
    }
}
