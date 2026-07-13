package com.themistra.auth.token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Syncs the configured clients into oauth2_registered_client at startup, keyed by a
 * deterministic id (= clientId) so re-seeding updates in place across replicas and restarts.
 */
@Component
public class RegisteredClientSeeder {

    private static final Logger log = LoggerFactory.getLogger(RegisteredClientSeeder.class);

    static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(10);      // target-design §6
    static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);        // target-design §7
    static final Duration AUTHORIZATION_CODE_TTL = Duration.ofMinutes(5);

    private final RegisteredClientRepository repository;
    private final AuthClientsProperties properties;
    private final PasswordEncoder passwordEncoder;

    public RegisteredClientSeeder(RegisteredClientRepository repository,
                                  AuthClientsProperties properties,
                                  PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    public void seed() {
        repository.save(spaClient(properties.spa()));
        properties.services().forEach(service -> repository.save(serviceClient(service)));
        log.info("Seeded {} registered clients", 1 + properties.services().size());
    }

    private RegisteredClient spaClient(AuthClientsProperties.Spa spa) {
        RegisteredClient.Builder builder = RegisteredClient.withId(spa.clientId())
                .clientId(spa.clientId())
                .clientName("Checky Pro SPA")
                // public client: no secret; possession is proven by PKCE (D-002)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)   // first-party client
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                        .refreshTokenTimeToLive(REFRESH_TOKEN_TTL)
                        .reuseRefreshTokens(false)            // rotation on every refresh (D-003)
                        .authorizationCodeTimeToLive(AUTHORIZATION_CODE_TTL)
                        .build());

        spa.redirectUris().forEach(builder::redirectUri);
        spa.postLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);
        return builder.build();
    }

    private RegisteredClient serviceClient(AuthClientsProperties.ServiceClient service) {
        RegisteredClient.Builder builder = RegisteredClient.withId(service.clientId())
                .clientId(service.clientId())
                .clientName(service.clientId())
                .clientSecret(passwordEncoder.encode(service.secret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                        .build());

        service.scopes().forEach(builder::scope);
        return builder.build();
    }
}
