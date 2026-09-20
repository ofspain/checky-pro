package com.themistra.auth.token;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.apikey.ApiKeyTokenIssuer;
import com.themistra.auth.common.ProblemTypes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end against real Postgres (Testcontainers) and the real security filter chain
 * ({@code webEnvironment = RANDOM_PORT} + {@link TestRestTemplate}), mirroring the established
 * pattern from {@code ApiKeyExchangeIntegrationTest}/{@code ApiKeyCrudIntegrationTest}. Lives in
 * {@code token}, not {@code account}, even though it drives {@code AccountController}'s HTTP
 * endpoints — constructing {@link RefreshTokenFamily} fixtures directly (via its public factory
 * and a raw {@link EntityManager}, the same technique {@code ApiKeyServiceIntegrationTest} uses
 * for state with no public creation path) is a same-package operation here, avoiding any
 * cross-module entity reference even in test code.
 *
 * <p>Authentication for the HTTP calls is a real signed JWT minted via {@link ApiKeyTokenIssuer}
 * (T26's established technique) — works for any account regardless of role, since none of the
 * session endpoints require a specific authority at the filter level.</p>
 *
 * <p>For the two named tests, a real {@link OAuth2Authorization} is also saved via the actual
 * {@link OAuth2AuthorizationService} bean so the "the live SAS authorization is genuinely removed"
 * half of R37/R38 (not just the family row) is proven against the real JDBC-backed store, not
 * merely inferred.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SessionIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ApiKeyTokenIssuer apiKeyTokenIssuer;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private AuthClientsProperties authClientsProperties;

    @Autowired
    private RefreshTokenFamilyRepository familyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** {@code seedFamily}'s raw {@code EntityManager} persist is a custom operation called
     * directly from this test, bypassing any {@code @Transactional} service boundary — it needs
     * its own short-lived transaction, matching {@code ApiKeyServiceIntegrationTest}/
     * {@code MfaPersistenceIntegrationTest}'s established {@code inOwnTransaction} convention. */
    private void inOwnTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private String baseUrl;

    private String baseUrl() {
        if (baseUrl == null) {
            baseUrl = "http://localhost:" + port;
        }
        return baseUrl;
    }

    @Test // Named test, R36 - only the caller's own active sessions, with the required fields
    void shouldListActiveSessions() throws Exception {
        UUID accountA = registerAndActivate("sessions-list-a@example.com");
        UUID accountB = registerAndActivate("sessions-list-b@example.com");
        RefreshTokenFamily withLabel = seedFamily(accountA, "auth-list-1", "chrome-macos");
        RefreshTokenFamily withoutLabel = seedFamily(accountA, "auth-list-2", null);
        seedFamily(accountB, "auth-list-3", "should-not-appear");

        ResponseEntity<String> response = get(bearerTokenFor(accountA), "/accounts/me/sessions");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body).hasSize(2);
        JsonNode labeled = findByFamilyId(body, withLabel.getFamilyId());
        assertThat(labeled.get("deviceLabel").asText()).isEqualTo("chrome-macos");
        assertThat(labeled.get("createdAt").asText()).isNotBlank();
        assertThat(labeled.get("rotatedAt").asText()).isNotBlank();
        JsonNode unlabeled = findByFamilyId(body, withoutLabel.getFamilyId());
        assertThat(unlabeled.get("deviceLabel").isNull()).isTrue();
    }

    @Test // Named test, R37 - revokes one family and genuinely removes its live SAS authorization
    void shouldRevokeSingleSessionFamily() throws Exception {
        UUID accountUuid = registerAndActivate("sessions-revoke-one@example.com");
        // seedRealAuthorization's own save() already auto-creates the family (the decorator's
        // trackRefreshTokenIfPresent runs on every save with a refresh token) - a second,
        // separately-seeded family for the same authorizationId would violate the
        // UNIQUE(authorization_id) constraint, so the auto-created row is looked up instead.
        String authorizationId = seedRealAuthorization(accountUuid);
        RefreshTokenFamily family = familyRepository.findByAuthorizationId(authorizationId).orElseThrow();
        String bearer = bearerTokenFor(accountUuid);
        Instant before = Instant.now().minusSeconds(1);

        ResponseEntity<String> deleteResponse =
                delete(bearer, "/accounts/me/sessions/" + family.getFamilyId());

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(authorizationService.findById(authorizationId)).isNull();
        JsonNode listAfter = objectMapper.readTree(get(bearer, "/accounts/me/sessions").getBody());
        assertThat(listAfter).isEmpty();
        // Kimi Phase 11 Gap 5: assert the family row itself, not just that it's absent from the
        // active list (which could theoretically be empty for an unrelated reason).
        RefreshTokenFamily reloaded = reloadFamily(family.getFamilyId());
        assertThat(reloaded.getRevokedAt()).isNotNull();
        assertThat(reloaded.getRevokedReason()).isEqualTo("USER_REVOKED");
        // Kimi Phase 11 Gap 6: exactly one audit row for this revoke.
        assertThat(countSessionRevokedAuditRows(accountUuid, before)).isEqualTo(1L);
    }

    @Test // Named test, R38 - revokes every active family and removes every live authorization
    void shouldRevokeAllSessionFamilies() throws Exception {
        UUID accountUuid = registerAndActivate("sessions-revoke-all@example.com");
        // Same reasoning as shouldRevokeSingleSessionFamily: seedRealAuthorization already
        // auto-creates each family via the decorator's own save() path.
        String authorizationIdOne = seedRealAuthorization(accountUuid);
        String authorizationIdTwo = seedRealAuthorization(accountUuid);
        RefreshTokenFamily familyOne = familyRepository.findByAuthorizationId(authorizationIdOne).orElseThrow();
        RefreshTokenFamily familyTwo = familyRepository.findByAuthorizationId(authorizationIdTwo).orElseThrow();
        String bearer = bearerTokenFor(accountUuid);
        Instant before = Instant.now().minusSeconds(1);

        ResponseEntity<String> deleteResponse = delete(bearer, "/accounts/me/sessions");

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(authorizationService.findById(authorizationIdOne)).isNull();
        assertThat(authorizationService.findById(authorizationIdTwo)).isNull();
        JsonNode listAfter = objectMapper.readTree(get(bearer, "/accounts/me/sessions").getBody());
        assertThat(listAfter).isEmpty();
        // Kimi Phase 11 Gap 5: both family rows themselves, not just the empty active list.
        assertThat(reloadFamily(familyOne.getFamilyId()).getRevokedReason()).isEqualTo("USER_REVOKED_ALL");
        assertThat(reloadFamily(familyTwo.getFamilyId()).getRevokedReason()).isEqualTo("USER_REVOKED_ALL");
        // Kimi Phase 11 Gap 6: one audit row per family revoked.
        assertThat(countSessionRevokedAuditRows(accountUuid, before)).isEqualTo(2L);
    }

    @Test // R36 - a caller with no sessions gets an empty array, not an error
    void listReturnsEmptyArrayWhenCallerHasNoSessions() throws Exception {
        UUID accountUuid = registerAndActivate("sessions-empty@example.com");

        ResponseEntity<String> response = get(bearerTokenFor(accountUuid), "/accounts/me/sessions");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(response.getBody())).isEmpty();
    }

    @Test // R37/AC7 - an unowned family and a genuinely nonexistent one get identical 404s on
          // every field except `instance`. `instance` is auto-populated by Spring from the
          // request path itself (verified: no code in this service calls setInstance), so it
          // legitimately differs between these two calls - it echoes the caller's own input
          // back, not a server-side decision that could leak which cause applied. Discovered
          // once this test actually ran against a live server for the first time (Docker was
          // never available before); the original byte-for-byte premise never a real body.
    void revokeOfUnownedAndNonexistentFamilyAreByteIdentical() throws Exception {
        UUID owner = registerAndActivate("sessions-owner@example.com");
        UUID stranger = registerAndActivate("sessions-stranger@example.com");
        RefreshTokenFamily ownersFamily = seedFamily(owner, "auth-owned", "device");
        String strangerBearer = bearerTokenFor(stranger);
        UUID nonexistentFamilyId = UUID.randomUUID();

        ResponseEntity<String> unownedResponse =
                delete(strangerBearer, "/accounts/me/sessions/" + ownersFamily.getFamilyId());
        ResponseEntity<String> nonexistentResponse =
                delete(strangerBearer, "/accounts/me/sessions/" + nonexistentFamilyId);

        Map<String, Object> unowned = rejectionBody(unownedResponse);
        Map<String, Object> nonexistent = rejectionBody(nonexistentResponse);
        assertThat(unowned.get("type")).isEqualTo(ProblemTypes.SESSION_NOT_FOUND.toString());
        assertThat(unowned.get("title")).isEqualTo("Session not found");
        assertThat(unowned.get("instance"))
                .isEqualTo("/accounts/me/sessions/" + ownersFamily.getFamilyId());
        assertThat(nonexistent.get("instance"))
                .isEqualTo("/accounts/me/sessions/" + nonexistentFamilyId);

        Map<String, Object> unownedWithoutInstance = new HashMap<>(unowned);
        unownedWithoutInstance.remove("instance");
        Map<String, Object> nonexistentWithoutInstance = new HashMap<>(nonexistent);
        nonexistentWithoutInstance.remove("instance");
        assertThat(unownedWithoutInstance).isEqualTo(nonexistentWithoutInstance);
    }

    @Test // R37, D1 - revoking an already-revoked family is idempotent, not an error
    void revokeOfAlreadyRevokedFamilyReturns204Again() {
        UUID accountUuid = registerAndActivate("sessions-idempotent@example.com");
        RefreshTokenFamily family = seedFamily(accountUuid, "auth-idempotent", "device");
        String bearer = bearerTokenFor(accountUuid);

        ResponseEntity<String> first = delete(bearer, "/accounts/me/sessions/" + family.getFamilyId());
        ResponseEntity<String> second = delete(bearer, "/accounts/me/sessions/" + family.getFamilyId());

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test // D2 - revoking a family whose SAS authorization is already gone is a no-op, not an error
    void revokeWhenAuthorizationAlreadyGoneSucceeds() {
        UUID accountUuid = registerAndActivate("sessions-auth-gone@example.com");
        RefreshTokenFamily family = seedFamily(accountUuid, "auth-never-existed", "device");
        String bearer = bearerTokenFor(accountUuid);

        ResponseEntity<String> response = delete(bearer, "/accounts/me/sessions/" + family.getFamilyId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test // R38 - bulk revoke with zero active sessions succeeds trivially
    void revokeAllSucceedsTriviallyWithNoSessions() {
        UUID accountUuid = registerAndActivate("sessions-bulk-empty@example.com");

        ResponseEntity<String> response = delete(bearerTokenFor(accountUuid), "/accounts/me/sessions");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

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

    private Map<String, Object> rejectionBody(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(response.getBody(), Map.class);
            assertThat(body).doesNotContainKey("detail");
            return body;
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse rejection body as JSON: " + response.getBody(), e);
        }
    }

    private JsonNode findByFamilyId(JsonNode listResponse, UUID familyId) {
        for (JsonNode item : listResponse) {
            if (item.get("familyId").asText().equals(familyId.toString())) {
                return item;
            }
        }
        throw new IllegalStateException("No list item found for familyId=" + familyId);
    }

    private String bearerTokenFor(UUID accountUuid) {
        return apiKeyTokenIssuer.issue(accountUuid, List.of()).accessToken();
    }

    private UUID registerAndActivate(String email) {
        AccountResponse registered = accountService.register(new RegisterAccountRequest(email, PASSWORD));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        return registered.accountUuid();
    }

    /** Bypasses the real SAS issuance pipeline to construct a family directly (no public creation
     * path exists for this without a full interactive login flow) - mirrors
     * {@code ApiKeyServiceIntegrationTest.createRawApiKeyRow}'s established technique. */
    private RefreshTokenFamily seedFamily(UUID accountUuid, String authorizationId, String deviceLabel) {
        RefreshTokenFamily family = RefreshTokenFamily.start(
                authorizationId, accountUuid.toString(), deviceLabel,
                "hash-" + UUID.randomUUID(), Instant.now());
        inOwnTransaction(() -> {
            entityManager.persist(family);
            entityManager.flush();
        });
        return family;
    }

    /** Kimi Phase 11 Gap 5: reloads a family row directly, bypassing the JPA first-level cache
     * (this test's own {@code seedFamily} persisted the same managed instance, so a plain
     * {@code entityManager.find} would just return the cached object rather than proving anything
     * was actually written) so the assertion reflects what is genuinely in the database. */
    private RefreshTokenFamily reloadFamily(UUID familyId) {
        entityManager.clear();
        return entityManager.find(RefreshTokenFamily.class, familyId);
    }

    /** Kimi Phase 11 Gap 6: one {@code auth_audit} row per family revoked (R43/AC6), queried
     * natively since {@code AuditEventRepository} is package-private to the {@code audit} module. */
    private long countSessionRevokedAuditRows(UUID accountUuid, Instant since) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM auth_audit WHERE event_type = 'session.revoked' "
                                + "AND outcome = 'SUCCESS' AND account_uuid = :accountUuid AND occurred_at >= :since")
                .setParameter("accountUuid", accountUuid)
                .setParameter("since", since)
                .getSingleResult();
        return count.longValue();
    }

    /** Saves one minimal, real {@link OAuth2Authorization} via the actual
     * {@link OAuth2AuthorizationService} bean, so revocation's "the live SAS authorization is
     * genuinely removed" claim (R37/R38) is checked against the real JDBC-backed store, not
     * merely inferred from the family row. Returns the authorization's id. */
    private String seedRealAuthorization(UUID accountUuid) {
        RegisteredClient client = registeredClientRepository.findByClientId(authClientsProperties.spa().clientId());
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .principalName(accountUuid.toString())
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .refreshToken(new OAuth2RefreshToken(
                        "refresh-" + UUID.randomUUID(), Instant.now()))
                .build();
        authorizationService.save(authorization);
        return authorization.getId();
    }
}
