package com.themistra.auth.apikey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.authz.DuplicateRoleException;
import com.themistra.auth.authz.RoleService;
import com.themistra.auth.authz.dto.CreateRoleRequest;
import com.themistra.auth.common.PublicEndpoints;
import com.themistra.auth.mfa.MfaService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end against real Postgres (Testcontainers) and the real security filter chain
 * ({@code webEnvironment = RANDOM_PORT} + {@link TestRestTemplate}, mirroring
 * {@code SasLoginIntegrationTest}'s established precedent — no {@code MockMvc} usage exists in
 * this module). Proves the frozen brief's acceptance criteria against an actually-running server,
 * not just against mocked collaborators.
 *
 * <p>No per-test rollback (same style as {@code SasLoginIntegrationTest} /
 * {@code ApiKeyServiceIntegrationTest}) — every test uses its own unique email/prefix.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApiKeyExchangeIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

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
    private ApiKeyService apiKeyService;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** {@code createRawExpiredApiKeyRow}'s raw {@code EntityManager} update is a custom
     * operation called directly from this test, bypassing {@code ApiKeyService}'s own
     * {@code @Transactional} boundary — it needs its own short-lived transaction, matching
     * {@code ApiKeyServiceIntegrationTest}/{@code MfaPersistenceIntegrationTest}'s established
     * {@code inOwnTransaction} convention for exactly this situation. */
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

    // ---------------------------------------------------------------------
    // Named tests (verbatim method names, package.md §8)
    // ---------------------------------------------------------------------

    @Test // Named test, R31/L8/L9/D1-D3 - the full claim set on an actually-issued, actually-signed
          // JWT reachable through the real filter chain
    void shouldExchangeValidApiKeyForMerchantJwt() throws ParseException {
        UUID accountUuid = seedMerchantWithConfirmedMfa("exchange-happy-path@example.com");
        ApiKeyService.CreateApiKeyResult created = apiKeyService.create(accountUuid, "http exchange key");

        ResponseEntity<String> response = postToken("ApiKey " + created.plaintextKey());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = readJson(response);
        assertThat(body.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("access_token", "token_type", "expires_in");
        assertThat(body.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(body.get("expires_in").asLong()).isEqualTo(600L); // default 10-minute TTL

        String accessToken = body.get("access_token").asText();
        SignedJWT signedJwt = (SignedJWT) JWTParser.parse(accessToken);
        assertThat(signedJwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
        // L9's exact claim set (Kimi Phase 11 Gaps 4/7/8): iss, sub, aud, exp, iat, nbf, jti,
        // scope, roles, client_id, amr, acr, email_verified - proven here against the real,
        // actually-signed token, not just the unit test's mocked-encoder assembly.
        assertThat(claims.getIssuer()).isNotBlank();
        assertThat(claims.getSubject()).isEqualTo(accountUuid.toString());
        assertThat(claims.getAudience()).containsExactly("checky-api-key");
        assertThat(claims.getNotBeforeTime()).isNotNull();
        assertThat(claims.getJWTID()).isNotBlank();
        assertThat(claims.getStringListClaim("scope")).containsExactly("merchant.api");
        assertThat(claims.getStringListClaim("amr")).containsExactly("api_key");
        assertThat(claims.getStringClaim("acr")).isEqualTo("urn:themistra:acr:api_key");
        assertThat(claims.getStringListClaim("roles")).contains("MERCHANT");
        assertThat(claims.getStringClaim("client_id")).isEqualTo("checky-api-key");
        assertThat(claims.getBooleanClaim("email_verified")).isFalse();
        assertThat(claims.getClaim("email")).isNull();
        assertThat(claims.getClaim("name")).isNull();
        assertThat(claims.getExpirationTime().toInstant())
                .isEqualTo(claims.getIssueTime().toInstant().plusSeconds(600));
    }

    @Test // Named test, R33/R46/AC10 - revoked, unknown-prefix, malformed, and wrong-secret all
          // produce byte-identical 401 problem+json bodies at the HTTP layer
    void shouldRejectRevokedOrUnknownApiKeyWithUniform401() {
        UUID accountUuid = seedMerchantWithConfirmedMfa("uniform-401-http@example.com");
        ApiKeyService.CreateApiKeyResult created = apiKeyService.create(accountUuid, "to be revoked");
        apiKeyService.revoke(accountUuid, created.keyUuid());

        Map<String, Object> revoked = rejectionBody(postToken("ApiKey " + created.plaintextKey()));
        Map<String, Object> unknownPrefix = rejectionBody(
                postToken("ApiKey ck_live_totallyunknown000000000.abcdefghijklmnopqrstuvwxyzabcdef"));
        Map<String, Object> malformed = rejectionBody(postToken("ApiKey not-a-valid-key-shape"));
        Map<String, Object> wrongSecret = rejectionBody(
                postToken("ApiKey " + created.plaintextKey().split("\\.", 2)[0] + "." + "z".repeat(32)));

        assertThat(revoked).isEqualTo(unknownPrefix);
        assertThat(revoked).isEqualTo(malformed);
        assertThat(revoked).isEqualTo(wrongSecret);
    }

    // ---------------------------------------------------------------------
    // Boundary / supporting tests
    // ---------------------------------------------------------------------

    @Test // R32/AC9 - a rejection never touches last_used_at, on the SAME key that later
          // succeeds (Kimi Phase 11 Gap 6: the original version of this test only proved it for a
          // decoy key rejected by wrong-secret; this proves it across several rejection causes on
          // the exact key that is then successfully exchanged).
    void lastUsedAtWrittenOnSuccessNeverOnRejection() {
        UUID accountUuid = seedMerchantWithConfirmedMfa("touch-last-used-http@example.com");
        ApiKeyService.CreateApiKeyResult created = apiKeyService.create(accountUuid, "touch key");
        String prefix = created.plaintextKey().split("\\.", 2)[0];
        assertThat(soleMetadata(accountUuid, created.keyUuid()).lastUsedAt()).isNull();

        // Wrong secret against this key's own prefix.
        postToken("ApiKey " + prefix + "." + "z".repeat(32));
        assertThat(soleMetadata(accountUuid, created.keyUuid()).lastUsedAt()).isNull();

        // An entirely unrelated rejection cause (unknown prefix) must not touch it either.
        postToken("ApiKey ck_live_completelyunrelated0000.abcdefghijklmnopqrstuvwxyzabcdef");
        assertThat(soleMetadata(accountUuid, created.keyUuid()).lastUsedAt()).isNull();

        // Now the real, correct key succeeds.
        ResponseEntity<String> response = postToken("ApiKey " + created.plaintextKey());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(soleMetadata(accountUuid, created.keyUuid()).lastUsedAt()).isNotNull();
    }

    @Test // Expired key -> uniform 401 (boundary/supporting #4). Exact tie-instant expiry is
          // exercised at the service layer (T24-frozen, ApiKeyServiceIntegrationTest); this proves
          // the HTTP layer surfaces the same rejection, not a different one.
    void expiredKeyRejectedUniformlyThroughTheEndpoint() {
        UUID accountUuid = seedMerchantWithConfirmedMfa("expired-http@example.com");
        Long accountId = apiKeyRepository.findAccountIdByUuid(accountUuid).orElseThrow();
        String expiredPlaintext = createRawExpiredApiKeyRow(accountId, "expired key");
        ApiKeyService.CreateApiKeyResult liveKey = apiKeyService.create(accountUuid, "live key for comparison");

        Map<String, Object> expiredBody = rejectionBody(postToken("ApiKey " + expiredPlaintext));
        ResponseEntity<String> liveResponse = postToken("ApiKey " + liveKey.plaintextKey());

        assertThat(liveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(expiredBody).containsEntry("title", "API key is invalid or revoked");
    }

    @Test // Boundary/supporting #6 - CI-enforceable guard: the route is registered, POST-scoped
    void publicEndpointsRegistersApiKeysTokenAsPostOnly() {
        assertThat(PublicEndpoints.METHOD_SCOPED)
                .contains(new PublicEndpoints.MethodScoped(HttpMethod.POST, "/api-keys/token"));
        assertThat(PublicEndpoints.METHOD_SCOPED)
                .doesNotContain(new PublicEndpoints.MethodScoped(HttpMethod.GET, "/api-keys/token"));
    }

    @Test // Boundary/supporting #7 - reachable anonymously through the real filter chain, with no
          // session and no CSRF token (this success alone is the Phase 9 CSRF-fix regression: had
          // the fix not been applied, CsrfFilter would 403 this exact request before the controller
          // ever ran). Also the D4 regression: an ApiKey-schemed header is not intercepted by
          // BearerTokenAuthenticationFilter, unlike a genuinely Bearer-schemed one.
    void reachableAnonymouslyAndApiKeySchemeAvoidsTheBearerFilter() {
        UUID accountUuid = seedMerchantWithConfirmedMfa("reachability@example.com");
        ApiKeyService.CreateApiKeyResult created = apiKeyService.create(accountUuid, "reachability key");

        ResponseEntity<String> apiKeySchemeResponse = postToken("ApiKey " + created.plaintextKey());
        assertThat(apiKeySchemeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // The identical key material, presented with the Bearer scheme instead, is intercepted by
        // BearerTokenAuthenticationFilter before ApiKeyController ever runs - a 401, but NOT this
        // module's uniform application/problem+json body (documented residual, Phase 7/8 finding).
        ResponseEntity<String> bearerSchemeResponse = postToken("Bearer " + created.plaintextKey());
        assertThat(bearerSchemeResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bearerSchemeResponse.getBody() == null || !bearerSchemeResponse.getBody()
                .contains("api-key-exchange-rejected"))
                .as("a Bearer-schemed request must not reach ApiKeyExceptionHandler's uniform body")
                .isTrue();
    }

    @Test // Boundary/supporting #8 - missing header, wrong (non-Bearer) scheme, blank credential,
          // and an over-256-character credential all produce the same uniform 401 as a genuinely
          // malformed key.
    void missingWrongSchemeBlankAndOverLengthCredentialAllUniform401() {
        Map<String, Object> malformedKeyBody = rejectionBody(postToken("ApiKey not-a-valid-key-shape"));

        Map<String, Object> missingHeaderBody = rejectionBody(postWithHeaders(null));
        Map<String, Object> wrongSchemeBody = rejectionBody(postToken("Basic dXNlcjpwYXNz"));
        Map<String, Object> blankCredentialBody = rejectionBody(postToken("ApiKey "));
        Map<String, Object> overLengthBody = rejectionBody(postToken("ApiKey " + "x".repeat(300)));

        assertThat(missingHeaderBody).isEqualTo(malformedKeyBody);
        assertThat(wrongSchemeBody).isEqualTo(malformedKeyBody);
        assertThat(blankCredentialBody).isEqualTo(malformedKeyBody);
        assertThat(overLengthBody).isEqualTo(malformedKeyBody);
    }

    @Test // R43/AC12 - one SUCCESS row + exactly one outbox mirror on the happy path
    void auditRecordsOneSuccessRowAndOneOutboxMirrorOnSuccess() {
        UUID accountUuid = seedMerchantWithConfirmedMfa("audit-success-http@example.com");
        ApiKeyService.CreateApiKeyResult created = apiKeyService.create(accountUuid, "audit success key");
        Instant before = Instant.now().minusSeconds(1);

        ResponseEntity<String> response = postToken("ApiKey " + created.plaintextKey());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(countAuditRows("api_key.exchanged", accountUuid, before)).isEqualTo(1L);
        assertThat(countOutboxRows("security.api_key.exchanged", accountUuid.toString(), before)).isEqualTo(1L);
    }

    @Test // R43/AC12 - one FAILURE row + exactly one outbox mirror per rejected attempt, even for
          // an account-less (malformed key) rejection
    void auditRecordsOneFailureRowAndOneOutboxMirrorPerRejection() {
        Instant before = Instant.now().minusSeconds(1);

        ResponseEntity<String> response = postToken("ApiKey not-a-valid-key-shape");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(countAuditRowsNullAccount("api_key.exchange_failed", before)).isEqualTo(1L);
        assertThat(countOutboxRowsByEventTypeSince("security.api_key.exchange_failed", before)).isEqualTo(1L);
    }

    @Test // AC11 - the response envelope names are exactly access_token/token_type/expires_in,
          // no extra/renamed fields, and no key/hash/email/internal id is ever echoed
    void responseEnvelopeHasExactlyTheThreeExpectedFieldsAndNoSecretMaterial() {
        String email = "envelope-fields@example.com";
        UUID accountUuid = seedMerchantWithConfirmedMfa(email);
        ApiKeyService.CreateApiKeyResult created = apiKeyService.create(accountUuid, "envelope key");

        ResponseEntity<String> response = postToken("ApiKey " + created.plaintextKey());

        JsonNode body = readJson(response);
        assertThat(body.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("access_token", "token_type", "expires_in");
        assertThat(response.getBody()).doesNotContain(created.plaintextKey());
        assertThat(response.getBody()).doesNotContain(email);
        // Kimi Phase 11 Gap 9: AC11 forbids echoing an internal id - keyUuid is the key's internal
        // handle and must never appear in the response.
        assertThat(response.getBody()).doesNotContain(created.keyUuid().toString());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private ResponseEntity<String> postToken(String authorizationHeaderValue) {
        return postWithHeaders(authorizationHeaderValue);
    }

    private ResponseEntity<String> postWithHeaders(String authorizationHeaderValue) {
        HttpHeaders headers = new HttpHeaders();
        if (authorizationHeaderValue != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeaderValue);
        }
        return restTemplate.exchange(
                baseUrl() + "/api-keys/token", HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }

    private Map<String, Object> rejectionBody(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

    private JsonNode readJson(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse response body as JSON: " + response.getBody(), e);
        }
    }

    private long countAuditRows(String eventType, UUID accountUuid, Instant since) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM auth_audit WHERE event_type = :eventType "
                                + "AND account_uuid = :accountUuid AND occurred_at >= :since")
                .setParameter("eventType", eventType)
                .setParameter("accountUuid", accountUuid)
                .setParameter("since", since)
                .getSingleResult();
        return count.longValue();
    }

    private long countAuditRowsNullAccount(String eventType, Instant since) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM auth_audit WHERE event_type = :eventType "
                                + "AND account_uuid IS NULL AND occurred_at >= :since")
                .setParameter("eventType", eventType)
                .setParameter("since", since)
                .getSingleResult();
        return count.longValue();
    }

    private long countOutboxRows(String eventType, String aggregateId, Instant since) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM outbox WHERE event_type = :eventType "
                                + "AND aggregate_id = :aggregateId AND created_at >= :since")
                .setParameter("eventType", eventType)
                .setParameter("aggregateId", aggregateId)
                .setParameter("since", since)
                .getSingleResult();
        return count.longValue();
    }

    private long countOutboxRowsByEventTypeSince(String eventType, Instant since) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM outbox WHERE event_type = :eventType AND created_at >= :since")
                .setParameter("eventType", eventType)
                .setParameter("since", since)
                .getSingleResult();
        return count.longValue();
    }

    private ApiKeyService.ApiKeyMetadata soleMetadata(UUID accountUuid, UUID keyUuid) {
        return apiKeyService.list(accountUuid).stream()
                .filter(metadata -> metadata.keyUuid().equals(keyUuid))
                .findFirst()
                .orElseThrow();
    }

    /** Bypasses {@link ApiKeyService#create} to construct a row already expired - mirrors
     * {@code ApiKeyServiceIntegrationTest.createRawApiKeyRow}'s established technique (T24 never
     * sets an expiry itself, so a direct native update is the only way to reach this state). */
    private String createRawExpiredApiKeyRow(Long accountId, String name) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String secret = UUID.randomUUID().toString().replace("-", "") + "12345678";
        String prefix = "ck_live_" + suffix;
        String fullKey = prefix + "." + secret;
        ApiKey apiKey = apiKeyRepository.save(ApiKey.create(accountId, prefix,
                com.themistra.auth.common.Hashing.sha256(fullKey), name, List.of("merchant.api"), Instant.now()));
        inOwnTransaction(() -> entityManager
                .createNativeQuery("UPDATE api_keys SET expires_at = :expiresAt WHERE id = :id")
                .setParameter("expiresAt", Instant.now().minusSeconds(3600))
                .setParameter("id", apiKey.getId())
                .executeUpdate());
        return fullKey;
    }

    private UUID seedMerchantWithConfirmedMfa(String email) {
        AccountResponse registered = accountService.register(new RegisterAccountRequest(email, PASSWORD));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        UUID accountUuid = registered.accountUuid();
        try {
            roleService.createRole(new CreateRoleRequest("MERCHANT", null));
        } catch (DuplicateRoleException e) {
            // Already created by an earlier test in this class - fine.
        }
        roleService.assignRole(accountUuid, "MERCHANT", accountUuid);
        MfaService.BeginEnrollResult begun = mfaService.beginEnroll(accountUuid);
        String code = referenceGenerateCode(begun.secret(), Instant.now());
        mfaService.confirm(accountUuid, code);
        return accountUuid;
    }

    /** Independent RFC 4226/6238 HOTP/TOTP implementation, deliberately separate code from
     * {@code TotpVerifier} - same discipline {@code SasLoginIntegrationTest} and
     * {@code ApiKeyServiceIntegrationTest} already apply. */
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
}
