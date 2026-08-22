package com.themistra.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.account.dto.VerifyEmailRequest;
import com.themistra.auth.apikey.ApiKeyTokenIssuer;
import com.themistra.auth.authz.DuplicateRoleException;
import com.themistra.auth.authz.RoleService;
import com.themistra.auth.authz.dto.CreateRoleRequest;
import com.themistra.auth.mfa.MfaService;
import com.themistra.auth.token.AuthClientsProperties;
import com.themistra.auth.token.RefreshTokenFamily;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.kafka.KafkaContainer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T36 — the one scenario no other test file exercises end-to-end: register → verify email →
 * login (password) → admin assigns MERCHANT → next login requires MFA enrollment (blocked) →
 * enroll TOTP → login with TOTP → create API key → exchange key for JWT → call session list →
 * revoke session, against real Postgres + Kafka (Testcontainers) and a real HTTP server
 * ({@code webEnvironment = RANDOM_PORT}).
 *
 * <p>Every step with an HTTP surface goes through real HTTP (frozen brief). Two steps
 * deliberately do not, both gated at Phase 4:
 * <ul>
 *   <li><b>TOTP enrollment</b> — no {@code Controller} exists for it anywhere in this codebase
 *   (Phase 0 finding); {@link #enrollTotp} calls {@link MfaService#beginEnroll}/{@code #confirm}
 *   directly, matching {@code SasLoginIntegrationTest.seedConfirmedTotpEnrollment}'s
 *   already-accepted precedent.</li>
 *   <li><b>The admin identity's own registration/role grant</b> — bootstrap plumbing, not a flow
 *   step under test; only the admin's one HTTP call ({@code POST /admin/accounts/.../roles/MERCHANT})
 *   is real.</li>
 * </ul>
 *
 * <p>The authorize-flow/PKCE/CSRF/cookie-merging machinery and the reference TOTP generator are
 * ported from {@code SasLoginIntegrationTest} rather than shared — no base class exists across
 * this module's integration tests (confirmed at Phase 0), and every sibling file duplicates its
 * own copy the same way.</p>
 *
 * <p><b>Kafka correlation (a gap the Phase 5 plan did not anticipate):</b> {@code POST /accounts}'s
 * response is deliberately enumeration-safe and never reveals the new account's UUID, so the raw
 * verification token — obtainable only via the {@code auth.email.requested} outbox event — cannot
 * be correlated by UUID alone. Because every {@code @SpringBootTest} class in this module with
 * matching configuration shares one long-lived Testcontainers Kafka broker across the whole suite
 * run, an {@code earliest}-offset consumer sees every prior test class's registrations too — a
 * "just take the first record" approach would be flaky. This test resolves the merchant's
 * {@code accountUuid} via {@link AccountService#findLoginView} immediately after the HTTP register
 * call — a direct service call used purely for test-correlation (never asserted on, never a
 * substitute for the HTTP action itself) — and then filters the Kafka consumer by that UUID as the
 * record key, the same key-filtering technique {@code AccountPersistenceIntegrationTest} already
 * established for exactly this shared-container hazard.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class EndToEndLifecycleIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final Pattern CSRF_INPUT_PATTERN =
            Pattern.compile("name=\"_csrf\"[^>]*\\bvalue=\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountService accountService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MfaService mfaService;

    @Autowired
    private ApiKeyTokenIssuer apiKeyTokenIssuer;

    @Autowired
    private AuthClientsProperties authClientsProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaContainer kafka;

    @PersistenceContext
    private EntityManager entityManager;

    private Consumer<String, String> testConsumer;

    private String baseUrl;

    private String baseUrl() {
        if (baseUrl == null) {
            baseUrl = "http://localhost:" + port;
        }
        return baseUrl;
    }

    @BeforeEach
    void setUp() {
        // Phase 11-equivalent guard (established by SasLoginIntegrationTest): TestRestTemplate
        // follows redirects by default, which would silently turn every FOUND assertion below into
        // whatever the Location target returns instead.
        restTemplate.getRestTemplate().setRequestFactory(new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        });

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-lifecycle-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        testConsumer = new KafkaConsumer<>(props);
        testConsumer.subscribe(List.of("auth.email.requested", "auth.user.lifecycle"));
    }

    @AfterEach
    void closeConsumer() {
        if (testConsumer != null) {
            testConsumer.close();
        }
    }

    @Test
    void shouldCompleteFullMerchantIdentityLifecycle() throws Exception {
        String merchantEmail = "e2e-merchant-" + UUID.randomUUID() + "@example.com";

        // 1. Register (R1) - real HTTP, 202 regardless of outcome (enumeration-safe).
        ResponseEntity<String> registerResponse = registerViaHttp(merchantEmail);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Test-correlation only (never a substitute for the HTTP action above) - see class javadoc.
        UUID merchantUuid = accountService.findLoginView(merchantEmail)
                .orElseThrow(() -> new IllegalStateException("No account found for " + merchantEmail))
                .accountUuid();

        // 2. Verify email (R4) - the raw token is only ever observable via the real
        // auth.email.requested outbox event; no API response ever carries it (enumeration-safety).
        String verificationToken = awaitRawVerificationToken(merchantUuid);
        ResponseEntity<String> verifyResponse = verifyEmailViaHttp(verificationToken);
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        // R4's event half: auth.user.registered is a status-transition event with no distinct
        // "eventType" field on the wire (OutboxRelay never serializes one) - status=ACTIVE on
        // auth.user.lifecycle for this account is exactly what that transition looks like on Kafka.
        awaitUserRegisteredLifecycleEvent(merchantUuid);

        // 3. Admin bootstrap (plumbing, not a tested step) + "admin assigns MERCHANT" (real HTTP).
        String adminBearer = bootstrapAdminBearerToken();
        ensureRoleExists("MERCHANT");
        ResponseEntity<String> assignRoleResponse = assignRoleViaHttp(adminBearer, merchantUuid, "MERCHANT");
        assertThat(assignRoleResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 4. Next login requires MFA enrollment - AC2: the freshly MERCHANT-assigned account gets
        // no authorization code at the /oauth2/authorize layer, pre-enrollment.
        FullFlowResult preEnrollment = attemptFullAuthorizeFlow(
                merchantEmail, PASSWORD, null, generatePkce(), UUID.randomUUID().toString());
        assertThat(preEnrollment.authorizationCode()).isEmpty();
        assertThat(preEnrollment.loginResponse().getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).contains("/login?error"));

        // 5. Enroll TOTP - the one step with no HTTP surface in this codebase (Phase 4 gate).
        byte[] totpSecret = enrollTotp(merchantUuid);

        // 6. Login with TOTP (AC3) - full authorize flow now succeeds; the issued token's amr
        // includes "otp", proving the MFA gate was genuinely exercised, not just bypassed.
        PkcePair pkce = generatePkce();
        String state = UUID.randomUUID().toString();
        FullFlowResult withTotp = attemptFullAuthorizeFlow(
                merchantEmail, PASSWORD, referenceGenerateCode(totpSecret, Instant.now()), pkce, state);
        assertThat(withTotp.authorizationCode()).isPresent();
        assertThat(withTotp.returnedState()).contains(state);
        String sasAccessToken = exchangeCodeForToken(withTotp.authorizationCode().orElseThrow(), pkce.verifier());
        assertThat(parseClaims(sasAccessToken).getStringListClaim("amr")).contains("otp");

        // 7. Create API key (AC4) - authenticated with the token this same login just produced.
        ResponseEntity<String> createResponse = post(sasAccessToken, "/api-keys", "{\"name\":\"e2e key\"}");
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = readJson(createResponse);
        String plaintextKey = created.get("plaintextKey").asText();
        assertThat(plaintextKey).matches("^ck_live_[A-Za-z0-9]{24}\\.[A-Za-z0-9]{32}$");

        // 8. Exchange key for JWT (AC5).
        ResponseEntity<String> exchangeResponse = postApiKeyExchange(plaintextKey);
        assertThat(exchangeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode exchangeBody = readJson(exchangeResponse);
        String exchangedToken = exchangeBody.get("access_token").asText();
        JWTClaimsSet exchangedClaims = parseClaims(exchangedToken);
        assertThat(exchangedClaims.getSubject()).isEqualTo(merchantUuid.toString());
        assertThat(exchangedClaims.getStringListClaim("scope")).contains("merchant.api");
        assertThat(exchangedClaims.getStringListClaim("amr")).contains("api_key");

        // 9. Call session list (AC6) - authenticated with the exchanged JWT; the family created by
        // step 6's interactive login is expected to be the caller's only active session.
        ResponseEntity<String> listResponse = get(exchangedToken, "/accounts/me/sessions");
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode sessions = readJson(listResponse);
        assertThat(sessions).hasSize(1);
        UUID familyId = UUID.fromString(sessions.get(0).get("familyId").asText());

        // 10. Revoke session (AC7).
        ResponseEntity<String> revokeResponse = delete(exchangedToken, "/accounts/me/sessions/" + familyId);
        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        JsonNode sessionsAfterRevoke = readJson(get(exchangedToken, "/accounts/me/sessions"));
        assertThat(sessionsAfterRevoke).isEmpty();
        RefreshTokenFamily reloaded = reloadFamily(familyId);
        assertThat(reloaded.getRevokedAt()).isNotNull();
        assertThat(reloaded.getRevokedReason()).isEqualTo("USER_REVOKED");
    }

    // ---------------------------------------------------------------------
    // HTTP helpers
    // ---------------------------------------------------------------------

    /** {@code /accounts} and {@code /accounts/verify-email} are not in {@code SecurityChainsConfig}'s
     * CSRF-ignored matchers ({@code /api/**}, {@code /api-keys/token}) - unlike this test's other
     * calls, which are all Bearer/API-key authenticated and empirically unaffected (confirmed by
     * {@code ApiKeyLifecycleIntegrationTest}'s already-passing precedent), these two are genuinely
     * anonymous session-realm calls and need a real CSRF token, obtained the same way
     * {@code SasLoginIntegrationTest} obtains one for {@code /login}: scrape it off a GET response,
     * carry the session cookie it was issued against, and submit both on the POST - as a header
     * ({@code X-CSRF-TOKEN}) rather than a form field, since these two endpoints take JSON bodies. */
    private CsrfContext fetchCsrfContext() {
        ResponseEntity<String> page = restTemplate.getForEntity(baseUrl() + "/login", String.class);
        List<String> cookies = page.getHeaders().get(HttpHeaders.SET_COOKIE);
        String csrfToken = extractCsrfToken(page.getBody());
        return new CsrfContext(csrfToken, cookies);
    }

    private record CsrfContext(String token, List<String> cookies) {
    }

    private ResponseEntity<String> registerViaHttp(String email) throws Exception {
        CsrfContext csrf = fetchCsrfContext();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-CSRF-TOKEN", csrf.token());
        headers.put(HttpHeaders.COOKIE, toRequestCookiePairs(csrf.cookies()));
        String body = objectMapper.writeValueAsString(new RegisterAccountRequest(email, PASSWORD));
        return restTemplate.exchange(
                baseUrl() + "/accounts", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> verifyEmailViaHttp(String token) throws Exception {
        CsrfContext csrf = fetchCsrfContext();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-CSRF-TOKEN", csrf.token());
        headers.put(HttpHeaders.COOKIE, toRequestCookiePairs(csrf.cookies()));
        String body = objectMapper.writeValueAsString(new VerifyEmailRequest(token));
        return restTemplate.exchange(
                baseUrl() + "/accounts/verify-email", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> assignRoleViaHttp(String bearer, UUID accountUuid, String roleName) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        return restTemplate.exchange(
                baseUrl() + "/admin/accounts/" + accountUuid + "/roles/" + roleName,
                HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> post(String bearer, String path, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                baseUrl() + path, HttpMethod.POST, new HttpEntity<>(jsonBody, headers), String.class);
    }

    private ResponseEntity<String> get(String bearer, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        return restTemplate.exchange(baseUrl() + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> delete(String bearer, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        return restTemplate.exchange(baseUrl() + path, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> postApiKeyExchange(String plaintextKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "ApiKey " + plaintextKey);
        return restTemplate.exchange(
                baseUrl() + "/api-keys/token", HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }

    private JsonNode readJson(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse response body as JSON: " + response.getBody(), e);
        }
    }

    // ---------------------------------------------------------------------
    // Kafka correlation helpers (see class javadoc)
    // ---------------------------------------------------------------------

    private String awaitRawVerificationToken(UUID accountUuid) {
        AtomicReference<String> token = new AtomicReference<>();
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = testConsumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if ("auth.email.requested".equals(record.topic()) && accountUuid.toString().equals(record.key())) {
                    JsonNode payload = objectMapper.readTree(record.value());
                    if ("verify_email".equals(payload.get("purpose").asText())) {
                        token.set(payload.get("token").asText());
                    }
                }
            }
            assertThat(token.get())
                    .as("auth.email.requested(verify_email) event observed for %s", accountUuid)
                    .isNotNull();
        });
        return token.get();
    }

    private void awaitUserRegisteredLifecycleEvent(UUID accountUuid) {
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = testConsumer.poll(Duration.ofMillis(500));
            boolean found = false;
            for (ConsumerRecord<String, String> record : records) {
                if ("auth.user.lifecycle".equals(record.topic()) && accountUuid.toString().equals(record.key())
                        && record.value().contains("\"status\":\"ACTIVE\"")) {
                    found = true;
                }
            }
            assertThat(found).as("auth.user.lifecycle(ACTIVE) event observed for %s", accountUuid).isTrue();
        });
    }

    // ---------------------------------------------------------------------
    // Admin bootstrap / role seeding (plumbing, not tested steps)
    // ---------------------------------------------------------------------

    private String bootstrapAdminBearerToken() {
        String adminEmail = "e2e-admin-" + UUID.randomUUID() + "@example.com";
        AccountResponse registered = accountService.register(new RegisterAccountRequest(adminEmail, PASSWORD));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        ensureRoleExists("ADMIN");
        roleService.assignRole(registered.accountUuid(), "ADMIN", null);
        return apiKeyTokenIssuer.issue(registered.accountUuid(), List.of()).accessToken();
    }

    /** Roles are shared, class-scoped DB state - created once, reused (matches every sibling
     * integration test's established convention). */
    private void ensureRoleExists(String roleName) {
        try {
            roleService.createRole(new CreateRoleRequest(roleName, null));
        } catch (DuplicateRoleException e) {
            // Already created by an earlier test - fine.
        }
    }

    /** The one flow step with no HTTP surface in this codebase (Phase 4 gate, Finding 1). */
    private byte[] enrollTotp(UUID accountUuid) {
        MfaService.BeginEnrollResult begun = mfaService.beginEnroll(accountUuid);
        String code = referenceGenerateCode(begun.secret(), Instant.now());
        mfaService.confirm(accountUuid, code);
        return begun.secret();
    }

    // ---------------------------------------------------------------------
    // Interactive-login flow (ported from SasLoginIntegrationTest - no shared base class exists)
    // ---------------------------------------------------------------------

    private ResponseEntity<String> postLoginForm(
            String email, String password, String mfaCode, String csrfToken, List<String> cookies) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", email);
        form.add("password", password);
        form.add("_csrf", csrfToken);
        if (mfaCode != null) {
            form.add("mfaCode", mfaCode);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (cookies != null) {
            headers.put(HttpHeaders.COOKIE, toRequestCookiePairs(cookies));
        }

        return restTemplate.exchange(
                baseUrl() + "/login", HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
    }

    private ResponseEntity<String> getWithCookies(String url, List<String> cookies) {
        HttpHeaders headers = new HttpHeaders();
        if (cookies != null && !cookies.isEmpty()) {
            headers.put(HttpHeaders.COOKIE, toRequestCookiePairs(cookies));
        }
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static List<String> mergeCookies(List<String> existing, List<String> newSetCookies) {
        Map<String, String> byName = new LinkedHashMap<>();
        if (existing != null) {
            for (String cookie : existing) {
                byName.put(cookieName(cookie), cookie);
            }
        }
        if (newSetCookies != null) {
            for (String cookie : newSetCookies) {
                byName.put(cookieName(cookie), cookie);
            }
        }
        return List.copyOf(byName.values());
    }

    private static String cookieName(String setCookieHeader) {
        int equalsIndex = setCookieHeader.indexOf('=');
        return equalsIndex < 0 ? setCookieHeader : setCookieHeader.substring(0, equalsIndex);
    }

    private static List<String> toRequestCookiePairs(List<String> setCookieHeaders) {
        return setCookieHeaders.stream().map(EndToEndLifecycleIntegrationTest::cookieNameValuePair).toList();
    }

    private static String cookieNameValuePair(String setCookieHeader) {
        int semicolonIndex = setCookieHeader.indexOf(';');
        return (semicolonIndex < 0 ? setCookieHeader : setCookieHeader.substring(0, semicolonIndex)).trim();
    }

    private String resolveLocation(URI location) {
        return location.isAbsolute() ? location.toString() : baseUrl() + location;
    }

    private static PkcePair generatePkce() {
        byte[] verifierBytes = new byte[32];
        new SecureRandom().nextBytes(verifierBytes);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes);
        try {
            byte[] challengeBytes = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
            return new PkcePair(verifier, challenge);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record PkcePair(String verifier, String challenge) {
    }

    private String authorizeUrl(String codeChallenge, String state) {
        return UriComponentsBuilder.fromUriString(baseUrl() + "/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", authClientsProperties.spa().clientId())
                .queryParam("redirect_uri", authClientsProperties.spa().redirectUris().getFirst())
                .queryParam("scope", "openid")
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();
    }

    private FullFlowResult attemptFullAuthorizeFlow(
            String email, String password, String mfaCode, PkcePair pkce, String state) {
        ResponseEntity<String> authorizeRedirect = getWithCookies(authorizeUrl(pkce.challenge(), state), null);
        List<String> cookies = authorizeRedirect.getHeaders().get(HttpHeaders.SET_COOKIE);
        URI loginLocation = authorizeRedirect.getHeaders().getLocation();
        if (loginLocation == null) {
            throw new IllegalStateException(
                    "Expected /oauth2/authorize to redirect an unauthenticated request to /login, got status "
                            + authorizeRedirect.getStatusCode());
        }

        ResponseEntity<String> loginPage = getWithCookies(resolveLocation(loginLocation), cookies);
        cookies = mergeCookies(cookies, loginPage.getHeaders().get(HttpHeaders.SET_COOKIE));
        String csrfToken = extractCsrfToken(loginPage.getBody());

        ResponseEntity<String> loginResponse = postLoginForm(email, password, mfaCode, csrfToken, cookies);
        URI postLoginLocation = loginResponse.getHeaders().getLocation();
        if (postLoginLocation == null || postLoginLocation.toString().contains("/login?error")) {
            return new FullFlowResult(loginResponse, Optional.empty(), Optional.empty());
        }

        cookies = mergeCookies(cookies, loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE));
        ResponseEntity<String> completedAuthorize = getWithCookies(authorizeUrl(pkce.challenge(), state), cookies);
        if (completedAuthorize.getStatusCode() != HttpStatus.FOUND || completedAuthorize.getHeaders().getLocation() == null) {
            throw new IllegalStateException(
                    "Expected the re-issued, now-authenticated /oauth2/authorize to redirect to "
                            + authClientsProperties.spa().redirectUris().getFirst() + " with an authorization "
                            + "code, got status " + completedAuthorize.getStatusCode());
        }
        URI finalLocation = completedAuthorize.getHeaders().getLocation();
        String code = extractQueryParam(finalLocation, "code");
        String returnedState = extractQueryParam(finalLocation, "state");
        return new FullFlowResult(loginResponse, Optional.ofNullable(code), Optional.ofNullable(returnedState));
    }

    private record FullFlowResult(
            ResponseEntity<String> loginResponse, Optional<String> authorizationCode, Optional<String> returnedState) {
    }

    private static String extractQueryParam(URI uri, String name) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(name);
    }

    private String exchangeCodeForToken(String code, String codeVerifier) throws Exception {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", authClientsProperties.spa().redirectUris().getFirst());
        form.add("client_id", authClientsProperties.spa().clientId());
        form.add("code_verifier", codeVerifier);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/oauth2/token", HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
        if (response.getStatusCode() != HttpStatus.OK) {
            throw new IllegalStateException("/oauth2/token returned " + response.getStatusCode() + ": " + response.getBody());
        }

        JsonNode json = objectMapper.readTree(response.getBody());
        JsonNode accessToken = json.get("access_token");
        if (accessToken == null) {
            throw new IllegalStateException("/oauth2/token response had no access_token: " + response.getBody());
        }
        return accessToken.asText();
    }

    private static JWTClaimsSet parseClaims(String jwt) {
        try {
            return JWTParser.parse(jwt).getJWTClaimsSet();
        } catch (ParseException e) {
            throw new IllegalStateException("Could not parse issued JWT", e);
        }
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

    /** Independent RFC 4226/6238 HOTP/TOTP implementation, deliberately separate code from
     * {@code TotpVerifier} - same discipline every sibling integration test in this module applies. */
    private static String referenceGenerateCode(byte[] secret, Instant now) {
        long timeCounter = Math.floorDiv(now.getEpochSecond(), 30);
        byte[] counterBytes = new byte[8];
        long counter = timeCounter;
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (counter & 0xFF);
            counter >>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------------
    // Session-revoke reload (AC7)
    // ---------------------------------------------------------------------

    /** {@code RefreshTokenFamilyRepository} is package-private to {@code token} (a real, Phase 5
     * plan-corrected discovery — this class was planned to autowire it directly, which does not
     * compile from outside that package); {@link RefreshTokenFamily} itself is public, so a plain
     * {@link EntityManager} lookup works from here exactly as {@code SessionIntegrationTest}'s own
     * {@code reloadFamily} does, without needing repository access at all. */
    private RefreshTokenFamily reloadFamily(UUID familyId) {
        entityManager.clear();
        return entityManager.find(RefreshTokenFamily.class, familyId);
    }
}
