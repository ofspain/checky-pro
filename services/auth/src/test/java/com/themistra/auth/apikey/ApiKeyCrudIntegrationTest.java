package com.themistra.auth.apikey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.authz.DuplicateRoleException;
import com.themistra.auth.authz.RoleService;
import com.themistra.auth.authz.dto.CreateRoleRequest;
import com.themistra.auth.mfa.MfaService;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end against real Postgres (Testcontainers) and the real security filter chain
 * ({@code webEnvironment = RANDOM_PORT} + {@link TestRestTemplate}), mirroring
 * {@code ApiKeyExchangeIntegrationTest}'s (T25) established pattern — kept as its own file rather
 * than extending that class, matching this pipeline's one-file-per-task convention for a
 * structurally distinct set of endpoints.
 *
 * <p>Authentication for these tests is a real, signed JWT minted via the already-wired
 * {@link ApiKeyTokenIssuer} bean (not a parallel hand-rolled JWT-minting implementation) — the
 * resource-server filter only requires a validly-signed, unexpired, correctly-issued token to
 * authenticate a caller; none of {@code /api-keys}'s three CRUD endpoints require a specific
 * role/authority at the filter level (that check lives inside {@code ApiKeyService.create}
 * itself), so this works regardless of the token's {@code roles} claim.</p>
 *
 * <p>No per-test rollback (same style as {@code ApiKeyExchangeIntegrationTest}) — every test uses
 * its own unique email.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApiKeyCrudIntegrationTest {

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
    private ApiKeyTokenIssuer apiKeyTokenIssuer;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test // Named test, R30 - plaintext returned exactly once, 201, no Location header (D8), no
          // hash-shaped value anywhere in the body
    void shouldCreateApiKeyAndShowPlaintextExactlyOnce() throws Exception {
        UUID accountUuid = seedMerchantWithConfirmedMfa("crud-create@example.com");
        String bearer = bearerTokenFor(accountUuid);

        ResponseEntity<String> response = postCreate(bearer, "{\"name\":\"CI pipeline key\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNull();
        JsonNode body = readJson(response);
        assertThat(body.get("plaintextKey").asText()).matches("^ck_live_[A-Za-z0-9]{24}\\.[A-Za-z0-9]{32}$");
        assertThat(body.get("name").asText()).isEqualTo("CI pipeline key");
        assertThat(body.has("keyUuid")).isTrue();
        assertThat(response.getBody()).doesNotContainPattern("[a-f0-9]{64}"); // no SHA-256 hash shape
        assertThat(body.has("keyHash")).isFalse();
    }

    @Test // Named test, R34/R35 - list reflects a revocation; revoked keys stay visible with a
          // non-null revokedAt (matches ApiKeyService's own established contract, T24)
    void shouldListAndRevokeOwnApiKeys() throws Exception {
        UUID accountUuid = seedMerchantWithConfirmedMfa("crud-list-revoke@example.com");
        String bearer = bearerTokenFor(accountUuid);
        JsonNode created = readJson(postCreate(bearer, "{\"name\":\"to be revoked\"}"));
        String keyUuid = created.get("keyUuid").asText();

        JsonNode beforeRevoke = readJson(get(bearer, "/api-keys"));
        assertThat(beforeRevoke).hasSize(1);
        assertThat(beforeRevoke.get(0).get("revokedAt").isNull()).isTrue();

        ResponseEntity<String> deleteResponse = delete(bearer, "/api-keys/" + keyUuid);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode afterRevoke = readJson(get(bearer, "/api-keys"));
        assertThat(afterRevoke).hasSize(1);
        assertThat(afterRevoke.get(0).get("revokedAt").isNull()).isFalse();
    }

    // ---------------------------------------------------------------------
    // Boundary / supporting tests
    // ---------------------------------------------------------------------

    @Test // R30/AC9 - a caller with no MERCHANT role and no confirmed MFA is rejected uniformly
    void createRejectsCallerLackingMerchantRoleOrConfirmedMfa() {
        UUID accountUuid = registerAndActivate("crud-not-merchant@example.com");
        String bearer = bearerTokenFor(accountUuid);

        ResponseEntity<String> response = postCreate(bearer, "{\"name\":\"nope\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody()).doesNotContain("detail");
    }

    @Test // D6/AC10 - a non-ACTIVE account gets the existing, unmodified AccountExceptionHandler's
          // 409-with-detail (accepted as-is at the Phase 4 gate, not a regression)
    void createRejectsNonActiveAccountWith409() {
        AccountResponse registered = accountService.register(
                new RegisterAccountRequest("crud-pending@example.com", PASSWORD));
        String bearer = bearerTokenFor(registered.accountUuid());

        ResponseEntity<String> response = postCreate(bearer, "{\"name\":\"nope\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("PENDING_VERIFICATION");
    }

    @Test // D1 - a blank or over-length name 400s via the framework's existing validation
          // handling, with no rejected value echoed back
    void createRejectsBlankOrOverLengthName() {
        UUID accountUuid = seedMerchantWithConfirmedMfa("crud-bad-name@example.com");
        String bearer = bearerTokenFor(accountUuid);
        String tooLong = "x".repeat(101);

        ResponseEntity<String> blankResponse = postCreate(bearer, "{\"name\":\"\"}");
        ResponseEntity<String> tooLongResponse = postCreate(bearer, "{\"name\":\"" + tooLong + "\"}");

        assertThat(blankResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooLongResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooLongResponse.getBody()).doesNotContain(tooLong);
    }

    @Test // R34 - a caller with no keys gets an empty array, not an error
    void listReturnsEmptyArrayWhenCallerHasNoKeys() throws Exception {
        UUID accountUuid = seedMerchantWithConfirmedMfa("crud-empty-list@example.com");
        String bearer = bearerTokenFor(accountUuid);

        ResponseEntity<String> response = get(bearer, "/api-keys");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readJson(response)).isEmpty();
    }

    @Test // R34 - never leaks another account's keys
    void listNeverReturnsAnotherAccountsKeys() throws Exception {
        UUID accountA = seedMerchantWithConfirmedMfa("crud-isolation-a@example.com");
        UUID accountB = seedMerchantWithConfirmedMfa("crud-isolation-b@example.com");
        postCreate(bearerTokenFor(accountA), "{\"name\":\"A's key\"}");

        JsonNode bKeys = readJson(get(bearerTokenFor(accountB), "/api-keys"));

        assertThat(bKeys).isEmpty();
    }

    @Test // AC6 - list response items contain no hash-shaped field
    void listResponseContainsNoHashShapedField() throws Exception {
        UUID accountUuid = seedMerchantWithConfirmedMfa("crud-list-no-hash@example.com");
        String bearer = bearerTokenFor(accountUuid);
        postCreate(bearer, "{\"name\":\"a key\"}");

        ResponseEntity<String> response = get(bearer, "/api-keys");

        assertThat(response.getBody()).doesNotContainPattern("[a-f0-9]{64}");
        assertThat(response.getBody()).doesNotContain("keyHash");
    }

    @Test // R35/AC7 - an unowned key and a genuinely nonexistent UUID get byte-identical 404s
    void deleteOfUnownedKeyAndNonexistentKeyAreByteIdentical() throws Exception {
        UUID owner = seedMerchantWithConfirmedMfa("crud-owner@example.com");
        UUID stranger = seedMerchantWithConfirmedMfa("crud-stranger@example.com");
        JsonNode created = readJson(postCreate(bearerTokenFor(owner), "{\"name\":\"owner's key\"}"));
        String ownedKeyUuid = created.get("keyUuid").asText();

        Map<String, Object> unowned = rejectionBody(
                delete(bearerTokenFor(stranger), "/api-keys/" + ownedKeyUuid), HttpStatus.NOT_FOUND);
        Map<String, Object> nonexistent = rejectionBody(
                delete(bearerTokenFor(stranger), "/api-keys/" + UUID.randomUUID()), HttpStatus.NOT_FOUND);

        assertThat(unowned).isEqualTo(nonexistent);
    }

    @Test // R35 - revoking an already-revoked key succeeds again (idempotent), not an error
    void deleteOfAlreadyRevokedKeyReturns204() throws Exception {
        UUID accountUuid = seedMerchantWithConfirmedMfa("crud-idempotent-revoke@example.com");
        String bearer = bearerTokenFor(accountUuid);
        JsonNode created = readJson(postCreate(bearer, "{\"name\":\"revoke twice\"}"));
        String keyUuid = created.get("keyUuid").asText();

        ResponseEntity<String> first = delete(bearer, "/api-keys/" + keyUuid);
        ResponseEntity<String> second = delete(bearer, "/api-keys/" + keyUuid);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private ResponseEntity<String> postCreate(String bearer, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                baseUrl() + "/api-keys", HttpMethod.POST, new HttpEntity<>(jsonBody, headers), String.class);
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

    private Map<String, Object> rejectionBody(ResponseEntity<String> response, HttpStatus expectedStatus) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
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

    /** A real, signed JWT for {@code accountUuid} minted via the already-wired
     * {@link ApiKeyTokenIssuer} - not a parallel hand-rolled minting implementation. Works for any
     * account regardless of its actual status/role, since token issuance itself performs no such
     * check (only {@code ApiKeyService.create} does) - exactly what the non-MERCHANT and
     * non-ACTIVE rejection tests above need. */
    private String bearerTokenFor(UUID accountUuid) {
        return apiKeyTokenIssuer.issue(accountUuid, List.of("merchant.api")).accessToken();
    }

    private UUID seedMerchantWithConfirmedMfa(String email) {
        UUID accountUuid = registerAndActivate(email);
        ensureRoleExists("MERCHANT");
        roleService.assignRole(accountUuid, "MERCHANT", accountUuid);
        MfaService.BeginEnrollResult begun = mfaService.beginEnroll(accountUuid);
        String code = referenceGenerateCode(begun.secret(), Instant.now());
        mfaService.confirm(accountUuid, code);
        return accountUuid;
    }

    private UUID registerAndActivate(String email) {
        AccountResponse registered = accountService.register(new RegisterAccountRequest(email, PASSWORD));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        return registered.accountUuid();
    }

    private void ensureRoleExists(String roleName) {
        try {
            roleService.createRole(new CreateRoleRequest(roleName, null));
        } catch (DuplicateRoleException e) {
            // Already created by an earlier test in this class - fine.
        }
    }

    /** Independent RFC 4226/6238 HOTP/TOTP implementation, deliberately separate code from
     * {@code TotpVerifier} - same discipline every other integration test in this module applies. */
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
