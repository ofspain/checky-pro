package com.themistra.auth.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisteredClientSeederTest {

    @Mock
    private RegisteredClientRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private RegisteredClientSeeder seeder;

    private final AuthClientsProperties properties = new AuthClientsProperties(
            new AuthClientsProperties.Spa(
                    "checky-spa",
                    List.of("https://app.checky.pro/auth/callback"),
                    List.of("https://app.checky.pro/")),
            List.of(new AuthClientsProperties.ServiceClient(
                    "payment-service", "raw-secret", List.of("internal.accounts:read"))));

    @BeforeEach
    void setUp() {
        seeder = new RegisteredClientSeeder(repository, properties, passwordEncoder);
    }

    private List<RegisteredClient> seededClients() {
        ArgumentCaptor<RegisteredClient> captor = ArgumentCaptor.forClass(RegisteredClient.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private RegisteredClient byClientId(String clientId) {
        return seededClients().stream()
                .filter(c -> c.getClientId().equals(clientId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void spaIsPublicPkceClientWithoutSecretOrClientCredentials() {
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}encoded");
        seeder.seed();

        RegisteredClient spa = byClientId("checky-spa");

        assertThat(spa.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(spa.getClientSecret()).isNull();
        assertThat(spa.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(spa.getAuthorizationGrantTypes()).containsExactlyInAnyOrder(
                AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(spa.getAuthorizationGrantTypes())
                .doesNotContain(AuthorizationGrantType.CLIENT_CREDENTIALS,
                        AuthorizationGrantType.PASSWORD);   // D-002: password grant must never appear
    }

    @Test
    void spaTokenSettingsMatchDesign() {
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}encoded");
        seeder.seed();

        var settings = byClientId("checky-spa").getTokenSettings();

        assertThat(settings.getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(10));
        assertThat(settings.getRefreshTokenTimeToLive()).isEqualTo(Duration.ofDays(30));
        assertThat(settings.isReuseRefreshTokens()).isFalse();   // rotation on (D-003)
    }

    @Test
    void serviceClientUsesEncodedSecretAndClientCredentialsOnly() {
        when(passwordEncoder.encode("raw-secret")).thenReturn("{bcrypt}encoded");
        seeder.seed();

        RegisteredClient service = byClientId("payment-service");

        assertThat(service.getClientSecret()).isEqualTo("{bcrypt}encoded");   // never the raw value
        assertThat(service.getAuthorizationGrantTypes())
                .containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(service.getScopes()).containsExactly("internal.accounts:read");
        assertThat(service.getTokenSettings().getAccessTokenTimeToLive())
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void deterministicIdsMakeSeedingIdempotentAcrossRestarts() {
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}encoded");
        seeder.seed();

        assertThat(seededClients())
                .allSatisfy(client -> assertThat(client.getId()).isEqualTo(client.getClientId()));
    }
}
