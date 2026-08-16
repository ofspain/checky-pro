package com.themistra.auth.apikey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one test this task exists to write (T27, R30/R31/R33/R35, L7/L8): proves the full
 * create→exchange→revoke→exchange-fails lifecycle of a single API key through real HTTP calls
 * against a real running server and real Postgres (Testcontainers) — not four isolated
 * operations, which {@link ApiKeyServiceIntegrationTest} (T24, service layer only),
 * {@link ApiKeyExchangeIntegrationTest} (T25, exchange only), and {@link ApiKeyCrudIntegrationTest}
 * (T26, CRUD only) already cover individually. This file adds no new production code and asserts
 * no new behavior — it proves the *sequence*: that a key valid a moment ago is immediately and
 * completely unusable the moment it is revoked, observed at the HTTP boundary throughout.
 *
 * <p>This task's own four header-listed "named tests" ({@code shouldCreateApiKeyAndShowPlaintextExactlyOnce},
 * {@code shouldExchangeValidApiKeyForMerchantJwt}, {@code shouldRejectRevokedOrUnknownApiKeyWithUniform401},
 * {@code shouldListAndRevokeOwnApiKeys}) already exist, correctly, in the three files named above —
 * {@code package.md} §8 has no distinct row for a T27-specific test name at all, confirming those
 * four names were simply carried over from the requirements they trace to. Writing four more
 * methods under those same names in this file would collide in intent, not just risk a compile
 * clash — so this file contributes exactly one, distinctly-named test (Phase 1/2/4 decision).</p>
 *
 * <p>Authenticates its {@code POST}/{@code GET}/{@code DELETE} calls with a JWT minted directly via
 * the already-wired {@link ApiKeyTokenIssuer} bean, exactly as {@link ApiKeyCrudIntegrationTest}
 * (T26) established — confirmed at this task's own Phase 4 gate (D4) as intended platform
 * behavior, not a test-only shortcut: the exchanged JWT carries the account's real,
 * freshly-resolved {@code roles} claim, so a {@code MERCHANT} account's own API-key-exchanged JWT
 * genuinely and correctly authorizes further API-key management in production too. <strong>This
 * also means T27's own successful execution depends on T25's token-issuance path being correct
 * (D3)</strong> — if this test fails, {@code ApiKeyTokenIssuer}/the {@code JwtEncoder} bean is a
 * plausible root cause, not necessarily a defect in this test's own logic.</p>
 *
 * <p>No per-test rollback (same style as every sibling integration test in this module) — not a
 * concern here since this file has exactly one test.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApiKeyLifecycleIntegrationTest {

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

    @Test // R30/R31/R32/R33/R34/R35, L7/L8 - the full lifecycle, one key, real HTTP throughout
    void shouldSupportFullCreateExchangeRevokeExchangeFailsLifecycle() throws Exception {
        UUID accountUuid = seedMerchantWithConfirmedMfa("lifecycle@example.com");
        String bearer = bearerTokenFor(accountUuid);

        // 1. Create (R30).
        ResponseEntity<String> createResponse = postCreate(bearer, "{\"name\":\"lifecycle key\"}");
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = readJson(createResponse);
        String keyUuid = created.get("keyUuid").asText();
        String plaintextKey = created.get("plaintextKey").asText();
        assertThat(plaintextKey).matches("^ck_live_[A-Za-z0-9]{24}\\.[A-Za-z0-9]{32}$");

        // 2. last_used_at is null before any exchange (D1).
        JsonNode beforeExchange = findByKeyUuid(readJson(get(bearer, "/api-keys")), keyUuid);
        assertThat(beforeExchange.get("lastUsedAt").isNull()).isTrue();

        // 3. Exchange succeeds (R31, L8) - decode the JWT and confirm it is a real, usable token,
        // not just a 200 status.
        ResponseEntity<String> firstExchange = postToken("ApiKey " + plaintextKey);
        assertThat(firstExchange.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode exchangeBody = readJson(firstExchange);
        JWTClaimsSet claims = JWTParser.parse(exchangeBody.get("access_token").asText()).getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(accountUuid.toString());
        assertThat(claims.getStringListClaim("scope")).contains("merchant.api");
        assertThat(claims.getStringListClaim("amr")).contains("api_key");

        // 4. last_used_at is now set (R32, D1).
        JsonNode afterExchange = findByKeyUuid(readJson(get(bearer, "/api-keys")), keyUuid);
        assertThat(afterExchange.get("lastUsedAt").isNull()).isFalse();

        // 5. Revoke (R35).
        ResponseEntity<String> deleteResponse = delete(bearer, "/api-keys/" + keyUuid);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 6. revoked_at is now set (D1) - closes the loop between the HTTP action and the
        // persisted state change, so a false pass (something else caused step 7 to fail) is ruled
        // out.
        JsonNode afterRevoke = findByKeyUuid(readJson(get(bearer, "/api-keys")), keyUuid);
        assertThat(afterRevoke.get("revokedAt").isNull()).isFalse();

        // 7. The identical key now fails exchange (R33) - uniform 401.
        ResponseEntity<String> secondExchange = postToken("ApiKey " + plaintextKey);
        assertThat(secondExchange.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 8. Proven uniform by comparison (D2), not asserted in isolation: an independent
        // rejection cause (a malformed key, never valid at all) produces a byte-for-byte
        // identical body on the same running server.
        ResponseEntity<String> malformedExchange = postToken("ApiKey not-a-valid-key-shape");
        assertThat(malformedExchange.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(secondExchange.getBody()).isEqualTo(malformedExchange.getBody());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private ResponseEntity<String> postCreate(String bearer, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        headers.setContentType(MediaType.APPLICATION_JSON);
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

    private ResponseEntity<String> postToken(String authorizationHeaderValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorizationHeaderValue);
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

    private JsonNode findByKeyUuid(JsonNode listResponse, String keyUuid) {
        for (JsonNode item : listResponse) {
            if (item.get("keyUuid").asText().equals(keyUuid)) {
                return item;
            }
        }
        throw new IllegalStateException("No list item found for keyUuid=" + keyUuid);
    }

    /** A real, signed JWT for {@code accountUuid} minted via the already-wired
     * {@link ApiKeyTokenIssuer} - matches {@code ApiKeyCrudIntegrationTest}'s (T26) established
     * technique, confirmed intended (not a test-only shortcut) at this task's own Phase 4 gate. */
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
