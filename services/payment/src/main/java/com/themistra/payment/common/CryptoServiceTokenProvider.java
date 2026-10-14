package com.themistra.payment.common;

import com.themistra.payment.common.config.CryptoClientProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The {@code client_credentials} token this service's {@link CryptoClient} needs to satisfy
 * {@code contracts/api/crypto-internal.yaml}'s own {@code bearerAuth: [internal.crypto:write]}
 * requirement (T06) — this service's first inter-service authenticated HTTP call. Thread-safe,
 * single-flight refresh (AC7): a {@code volatile} cached token is checked lock-free on the
 * common path; only a genuine cache miss/expiry takes the {@link ReentrantLock}, and a second
 * thread that loses the race re-checks the cache under the lock before fetching again.
 *
 * <p>Deliberately never called from inside {@code InvoiceService.create}'s own
 * {@code @Transactional} method (Phase 3 findings 1/7, Phase 4 disposition) — every caller of
 * {@link #getAccessToken()} must be outside any open database transaction.</p>
 */
@Component
public class CryptoServiceTokenProvider {

    private static final Duration EXPIRY_SAFETY_MARGIN = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final CryptoClientProperties.Auth auth;
    private final Clock clock;
    private final ReentrantLock refreshLock = new ReentrantLock();

    private volatile CachedToken cachedToken;

    public CryptoServiceTokenProvider(RestClient.Builder restClientBuilder,
                                       CryptoClientProperties properties, Clock clock) {
        this.restClient = restClientBuilder.build();
        this.auth = properties.auth();
        this.clock = clock;
    }

    public String getAccessToken() {
        CachedToken current = cachedToken;
        if (current != null && current.isValidAt(clock.instant())) {
            return current.accessToken();
        }

        refreshLock.lock();
        try {
            current = cachedToken;
            if (current != null && current.isValidAt(clock.instant())) {
                return current.accessToken();
            }
            CachedToken fetched = fetchNewToken();
            cachedToken = fetched;
            return fetched.accessToken();
        } finally {
            refreshLock.unlock();
        }
    }

    private CachedToken fetchNewToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        TokenResponse response;
        try {
            response = restClient.post()
                    .uri(auth.tokenUri())
                    .headers(headers -> headers.setBasicAuth(auth.clientId(), auth.clientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            throw new CryptoServiceUnavailableException(
                    "Failed to obtain a client_credentials token from auth-service", e);
        }

        if (response == null || response.accessToken() == null) {
            throw new CryptoServiceUnavailableException(
                    "auth-service token endpoint returned no access_token", null);
        }

        Instant expiresAt = clock.instant().plusSeconds(response.expiresInSeconds());
        return new CachedToken(response.accessToken(), expiresAt);
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isValidAt(Instant now) {
            return now.isBefore(expiresAt.minus(EXPIRY_SAFETY_MARGIN));
        }
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresInSeconds
    ) {
    }
}
