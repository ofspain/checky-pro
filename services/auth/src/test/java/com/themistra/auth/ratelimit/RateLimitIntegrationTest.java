package com.themistra.auth.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.auth.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end for T31 (R41/R42) against real Postgres/Kafka (Testcontainers) and the real security
 * filter chain — {@code webEnvironment = RANDOM_PORT} + {@link TestRestTemplate}, mirroring
 * {@code SasLoginIntegrationTest}'s established precedent (including its exact CSRF-scraping
 * technique, needed for {@code /login} and {@code /accounts/password-reset} — both on chain 2 —
 * but not {@code /oauth2/token}, confirmed CSRF-exempt by {@code SasLoginIntegrationTest}'s own
 * already-passing direct POST to it with no CSRF token at all).
 *
 * <p>Drives each path past its configured per-minute threshold (login 10, password-reset 5,
 * oauth-token 30 — {@code application.properties} defaults) directly, rather than waiting for the
 * threshold to reset, since the refill window is a fixed real 60 seconds not worth waiting out in
 * every test; only exhaustion is proven here, not post-window recovery (Bucket4j's own refill
 * mechanism is well-established, not this task's own logic to re-verify).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RateLimitIntegrationTest {

    private static final Pattern CSRF_INPUT_PATTERN =
            Pattern.compile("name=\"_csrf\"[^>]*\\bvalue=\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        // Without this, a 302 (failed login) or a redirect-shaped response would be silently
        // followed and its target's status returned instead - SasLoginIntegrationTest's own
        // established fix for the same TestRestTemplate default behavior.
        restTemplate.getRestTemplate().setRequestFactory(new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        });
    }

    @Test // Named test, R41 - drives the login path (10/minute) past its threshold
    void shouldReturn429WhenPerAccountRateLimitExceeded() {
        String email = "ratelimit-login-" + UUID.randomUUID() + "@example.com";

        List<ResponseEntity<String>> responses = attemptLoginRepeatedly(email, 11);

        for (int i = 0; i < 10; i++) {
            assertThat(responses.get(i).getStatusCode())
                    .as("attempt %d should not yet be rate-limited", i + 1)
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
        ResponseEntity<String> eleventh = responses.get(10);
        assertThat(eleventh.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // Tomcat appends a default charset to the Content-Type header; .contains(...) tolerates
        // that the same way SessionIntegrationTest's own rejectionBody helper already does.
        assertThat(eleventh.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(eleventh.getHeaders().getFirst("Retry-After")).isNotBlank();

        JsonNode body = readJson(eleventh);
        assertThat(body.get("type").asText()).isEqualTo("https://checky.pro/problems/rate-limit-exceeded");
        assertThat(body.get("title").asText()).isEqualTo("Too many requests");
        assertThat(body.get("instance").asText()).isEqualTo("/login");
    }

    @Test // AC4 - a different account's login bucket is unaffected by another account's exhaustion
    void differentAccountsHaveIndependentLoginBuckets() {
        String exhausted = "ratelimit-exhausted-" + UUID.randomUUID() + "@example.com";
        String fresh = "ratelimit-fresh-" + UUID.randomUUID() + "@example.com";

        attemptLoginRepeatedly(exhausted, 11); // exhausts `exhausted`'s own bucket
        ResponseEntity<String> freshAttempt = attemptLoginRepeatedly(fresh, 1).get(0);

        assertThat(freshAttempt.getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test // R41 - drives the password-reset confirmation path (5/minute) past its threshold
    void shouldReturn429ForPasswordResetConfirmationRateLimit() {
        List<ResponseEntity<String>> responses = attemptPasswordResetRepeatedly(6);

        for (int i = 0; i < 5; i++) {
            assertThat(responses.get(i).getStatusCode())
                    .as("attempt %d should not yet be rate-limited", i + 1)
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
        ResponseEntity<String> sixth = responses.get(5);
        assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(sixth.getHeaders().getFirst("Retry-After")).isNotBlank();
    }

    @Test // R41 - drives /oauth2/token's refresh_token grant (30/minute) past its threshold.
          // No CSRF handling needed: SasLoginIntegrationTest's own real authorization_code
          // exchange already proves /oauth2/token requires no CSRF token at all.
    void shouldReturn429ForOauthTokenRefreshGrantRateLimit() {
        String refreshTokenValue = "ratelimit-refresh-" + UUID.randomUUID();

        List<ResponseEntity<String>> responses = postOauthTokenRepeatedly(
                "refresh_token", "refresh_token", refreshTokenValue, 31);

        for (int i = 0; i < 30; i++) {
            assertThat(responses.get(i).getStatusCode())
                    .as("attempt %d should not yet be rate-limited", i + 1)
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
        assertThat(responses.get(30).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test // AC8 - client_credentials must never be throttled by this per-account mechanism, even
          // well past what would be the refresh_token grant's own 30/minute threshold
    void oauthTokenClientCredentialsGrantIsNeverRateLimited() {
        List<ResponseEntity<String>> responses =
                postOauthTokenRepeatedly("client_credentials", null, null, 35);

        assertThat(responses).noneMatch(r -> r.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS);
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private List<ResponseEntity<String>> attemptLoginRepeatedly(String email, int attempts) {
        return java.util.stream.IntStream.range(0, attempts)
                .mapToObj(i -> attemptLoginOnce(email))
                .toList();
    }

    /** Mirrors {@code SasLoginIntegrationTest.attemptLogin}'s exact GET-then-scrape-then-POST
     * CSRF technique. The password is deliberately wrong - this test only needs the request to
     * reach the rate limiter (which runs before any credential check, D4), not to succeed. */
    private ResponseEntity<String> attemptLoginOnce(String email) {
        ResponseEntity<String> loginPage = restTemplate.getForEntity(baseUrl + "/login", String.class);
        List<String> setCookies = loginPage.getHeaders().get(HttpHeaders.SET_COOKIE);
        String csrfToken = extractCsrfToken(loginPage.getBody());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", email);
        form.add("password", "wrong-password-" + UUID.randomUUID());
        form.add("_csrf", csrfToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (setCookies != null) {
            headers.put(HttpHeaders.COOKIE, setCookies);
        }

        return restTemplate.exchange(
                baseUrl + "/login", HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
    }

    /** The SAME fabricated token is reused across every attempt - the bucket key is the token's
     * own hash (Phase 4 D3/Phase 9 disposition), so a fresh token per call would independently
     * consume a different bucket each time and never accumulate toward the threshold. */
    private List<ResponseEntity<String>> attemptPasswordResetRepeatedly(int attempts) {
        String sharedToken = "fabricated-" + UUID.randomUUID();
        return java.util.stream.IntStream.range(0, attempts)
                .mapToObj(i -> attemptPasswordResetOnce(sharedToken))
                .toList();
    }

    /** Same CSRF session-scraping technique as {@link #attemptLoginOnce} - confirmed necessary
     * because {@code /accounts/password-reset} is one of the sibling self-service POST endpoints
     * never added to the CSRF-ignore list (a pre-existing, separately-tracked gap, not this
     * task's to fix). A fabricated token is deliberately used - this test only needs the request
     * to reach the rate limiter, not to actually reset a password. */
    private ResponseEntity<String> attemptPasswordResetOnce(String token) {
        ResponseEntity<String> loginPage = restTemplate.getForEntity(baseUrl + "/login", String.class);
        List<String> setCookies = loginPage.getHeaders().get(HttpHeaders.SET_COOKIE);
        String csrfToken = extractCsrfToken(loginPage.getBody());

        String body = String.format(
                "{\"token\":\"%s\",\"newPassword\":\"correct-horse-battery\"}", token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-CSRF-TOKEN", csrfToken);
        if (setCookies != null) {
            headers.put(HttpHeaders.COOKIE, setCookies);
        }

        return restTemplate.exchange(baseUrl + "/accounts/password-reset", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private List<ResponseEntity<String>> postOauthTokenRepeatedly(
            String grantType, String refreshTokenParamName, String refreshTokenValue, int attempts) {
        return java.util.stream.IntStream.range(0, attempts)
                .mapToObj(i -> postOauthTokenOnce(grantType, refreshTokenParamName, refreshTokenValue))
                .toList();
    }

    private ResponseEntity<String> postOauthTokenOnce(
            String grantType, String refreshTokenParamName, String refreshTokenValue) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        if (refreshTokenParamName != null) {
            form.add(refreshTokenParamName, refreshTokenValue);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        return restTemplate.exchange(
                baseUrl + "/oauth2/token", HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
    }

    private String extractCsrfToken(String loginPageHtml) {
        if (loginPageHtml == null) {
            throw new IllegalStateException("Login page returned no body - cannot extract CSRF token");
        }
        Matcher matcher = CSRF_INPUT_PATTERN.matcher(loginPageHtml);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not find _csrf hidden input on the login page");
        }
        return matcher.group(1);
    }

    private JsonNode readJson(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse response body as JSON: " + response.getBody(), e);
        }
    }
}
