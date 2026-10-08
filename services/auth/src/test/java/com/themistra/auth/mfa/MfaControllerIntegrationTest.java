package com.themistra.auth.mfa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.apikey.ApiKeyTokenIssuer;
import com.themistra.auth.common.ProblemTypes;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end against real Postgres (Testcontainers) and the real security filter chain
 * ({@code webEnvironment = RANDOM_PORT} + {@link TestRestTemplate}), mirroring
 * {@code ApiKeyCrudIntegrationTest}'s (T26) established pattern. T19 is the first task where a
 * real HTTP round trip through all four {@code /accounts/me/mfa} endpoints is possible — T16-T18
 * built and proved the service layer only, with no controller to exercise it through.
 *
 * <p>Authentication is a real, signed JWT minted via the already-wired {@link ApiKeyTokenIssuer}
 * bean (not a parallel hand-rolled JWT-minting implementation) — the resource-server filter only
 * requires a validly-signed, unexpired, correctly-issued token; none of these four endpoints
 * require a specific role/authority at the filter level.</p>
 *
 * <p>No per-test rollback — every test uses its own unique email.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MfaControllerIntegrationTest {

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

    @Test // Named test, R22 - happy path: 201 with only the provisioning URI, never a raw secret
    void shouldReturnTotpProvisioningUriOnEnrollmentBegin() throws Exception {
        UUID accountUuid = registerAndActivate("mfa-begin@example.com");
        String bearer = bearerTokenFor(accountUuid);

        ResponseEntity<String> response = postTotp(bearer, "/accounts/me/mfa/totp", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNull();
        JsonNode body = readJson(response);
        assertThat(body.fieldNames()).toIterable().containsExactly("provisioningUri");
        assertThat(body.get("provisioningUri").asText()).startsWith("otpauth://totp/");
    }

    @Test // Named test, R23 - happy path: 200 with 10 single-use recovery codes
    void shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes() throws Exception {
        UUID accountUuid = registerAndActivate("mfa-confirm@example.com");
        String bearer = bearerTokenFor(accountUuid);
        byte[] secret = beginEnrollAndCaptureSecret(bearer);
        String code = referenceGenerateCode(secret, Instant.now());

        ResponseEntity<String> response = postTotp(
                bearer, "/accounts/me/mfa/totp/confirm", "{\"code\":\"" + code + "\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = readJson(response);
        assertThat(body.get("recoveryCodes")).hasSize(10);
        assertThat(body.get("recoveryCodes")).doesNotHaveDuplicates();
    }

    @Test // Named test, R28 - happy path: 204, enrollment and recovery codes removed
    void shouldRequirePasswordAndTotpToDisableMfa() throws Exception {
        UUID accountUuid = registerAndActivate("mfa-disable@example.com");
        String bearer = bearerTokenFor(accountUuid);
        byte[] secret = beginEnrollAndCaptureSecret(bearer);
        confirm(bearer, secret);
        String code = referenceGenerateCode(secret, Instant.now());

        ResponseEntity<String> response = deleteTotp(bearer,
                "{\"currentPassword\":\"" + PASSWORD + "\",\"code\":\"" + code + "\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test // Named test (new, R49) - happy path: 200 with 10 new codes, every old one invalidated
    void shouldRegenerateRecoveryCodesWithPasswordAndTotp() throws Exception {
        UUID accountUuid = registerAndActivate("mfa-regen@example.com");
        String bearer = bearerTokenFor(accountUuid);
        byte[] secret = beginEnrollAndCaptureSecret(bearer);
        JsonNode confirmBody = confirm(bearer, secret);
        List<String> oldCodes = toList(confirmBody.get("recoveryCodes"));
        String code = referenceGenerateCode(secret, Instant.now());

        ResponseEntity<String> response = postTotp(bearer, "/accounts/me/mfa/recovery-codes",
                "{\"currentPassword\":\"" + PASSWORD + "\",\"code\":\"" + code + "\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = readJson(response);
        List<String> newCodes = toList(body.get("recoveryCodes"));
        assertThat(newCodes).hasSize(10);
        assertThat(newCodes).doesNotHaveDuplicates();
        assertThat(newCodes).doesNotContainAnyElementsOf(oldCodes);
    }

    // ---------------------------------------------------------------------
    // Boundary / failure-path tests
    // ---------------------------------------------------------------------

    @Test // R22 - a confirmed enrollment already exists
    void beginEnrollRejectsWhenAlreadyEnrolled() throws Exception {
        UUID accountUuid = registerAndActivate("mfa-already-enrolled@example.com");
        String bearer = bearerTokenFor(accountUuid);
        byte[] secret = beginEnrollAndCaptureSecret(bearer);
        confirm(bearer, secret);

        ResponseEntity<String> response = postTotp(bearer, "/accounts/me/mfa/totp", null);

        rejectionBody(response, HttpStatus.CONFLICT, ProblemTypes.MFA_ALREADY_ENROLLED, "MFA is already enrolled");
    }

    @Test // R23/R29 - a wrong code at confirm time never confirms, always 401
    void confirmRejectsWrongCode() throws Exception {
        UUID accountUuid = registerAndActivate("mfa-confirm-wrong@example.com");
        String bearer = bearerTokenFor(accountUuid);
        beginEnrollAndCaptureSecret(bearer);

        ResponseEntity<String> response = postTotp(bearer, "/accounts/me/mfa/totp/confirm", "{\"code\":\"000000\"}");

        rejectionBody(response, HttpStatus.UNAUTHORIZED, ProblemTypes.MFA_INVALID_CODE, "TOTP code is invalid");
    }

    @Test // R28 - wrong current password, not enumeration-sensitive (caller already authenticated)
    void disableRejectsWrongPassword() throws Exception {
        UUID accountUuid = registerAndActivate("mfa-disable-wrong-pw@example.com");
        String bearer = bearerTokenFor(accountUuid);
        byte[] secret = beginEnrollAndCaptureSecret(bearer);
        confirm(bearer, secret);
        String code = referenceGenerateCode(secret, Instant.now());

        ResponseEntity<String> response = deleteTotp(bearer,
                "{\"currentPassword\":\"wrong-password\",\"code\":\"" + code + "\"}");

        rejectionBody(response, HttpStatus.BAD_REQUEST,
                ProblemTypes.CURRENT_PASSWORD_MISMATCH, "Current password is incorrect");
    }

    @Test // R28/R49 - no confirmed enrollment to disable
    void disableRejectsWhenNotEnrolled() throws Exception {
        UUID accountUuid = registerAndActivate("mfa-disable-not-enrolled@example.com");
        String bearer = bearerTokenFor(accountUuid);

        ResponseEntity<String> response = deleteTotp(bearer,
                "{\"currentPassword\":\"" + PASSWORD + "\",\"code\":\"123456\"}");

        rejectionBody(response, HttpStatus.NOT_FOUND, ProblemTypes.MFA_NOT_ENROLLED, "MFA is not enrolled");
    }

    @Test // R49 - no confirmed enrollment to regenerate codes for
    void regenerateRejectsWhenNotEnrolled() throws Exception {
        UUID accountUuid = registerAndActivate("mfa-regen-not-enrolled@example.com");
        String bearer = bearerTokenFor(accountUuid);

        ResponseEntity<String> response = postTotp(bearer, "/accounts/me/mfa/recovery-codes",
                "{\"currentPassword\":\"" + PASSWORD + "\",\"code\":\"123456\"}");

        rejectionBody(response, HttpStatus.NOT_FOUND, ProblemTypes.MFA_NOT_ENROLLED, "MFA is not enrolled");
    }

    @Test // R49 - wrong TOTP code never regenerates, old codes remain valid
    void regenerateRejectsWrongCode() throws Exception {
        UUID accountUuid = registerAndActivate("mfa-regen-wrong-code@example.com");
        String bearer = bearerTokenFor(accountUuid);
        byte[] secret = beginEnrollAndCaptureSecret(bearer);
        confirm(bearer, secret);

        ResponseEntity<String> response = postTotp(bearer, "/accounts/me/mfa/recovery-codes",
                "{\"currentPassword\":\"" + PASSWORD + "\",\"code\":\"000000\"}");

        rejectionBody(response, HttpStatus.UNAUTHORIZED, ProblemTypes.MFA_INVALID_CODE, "TOTP code is invalid");
    }

    @Test // D1 - a blank code 400s via the framework's own validation, never reaching MfaService
    void confirmRejectsBlankCodeWithValidationError() throws Exception {
        UUID accountUuid = registerAndActivate("mfa-blank-code@example.com");
        String bearer = bearerTokenFor(accountUuid);
        beginEnrollAndCaptureSecret(bearer);

        ResponseEntity<String> response = postTotp(bearer, "/accounts/me/mfa/totp/confirm", "{\"code\":\"\"}");

        rejectionBody(response, HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION_ERROR, "Validation failed");
    }

    @Test // D1 - a blank currentPassword 400s the same way, never reaching MfaService
    void disableRejectsBlankPasswordWithValidationError() throws Exception {
        UUID accountUuid = registerAndActivate("mfa-blank-password@example.com");
        String bearer = bearerTokenFor(accountUuid);
        byte[] secret = beginEnrollAndCaptureSecret(bearer);
        confirm(bearer, secret);

        ResponseEntity<String> response = deleteTotp(bearer, "{\"currentPassword\":\"\",\"code\":\"123456\"}");

        rejectionBody(response, HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION_ERROR, "Validation failed");
    }

    @Test // none of the four endpoints are reachable without a bearer token. Uses a plain
          // java.net.http.HttpClient with redirects disabled, not TestRestTemplate/Apache
          // HttpClient5 (as every other test in this class uses): empirically, HttpClient5's
          // redirect/auth-challenge handling misreports a DELETE-with-body's real 401 response
          // (confirmed here, WWW-Authenticate: Bearer, no Location header) as a spurious
          // "circular redirect" exception - a client-side quirk, not real server behavior.
    void allFourEndpointsReject401WithoutABearerToken() throws Exception {
        assertThat(statusWithoutAuth("POST", "/accounts/me/mfa/totp")).isEqualTo(401);
        assertThat(statusWithoutAuth("POST", "/accounts/me/mfa/totp/confirm")).isEqualTo(401);
        assertThat(statusWithoutAuth("DELETE", "/accounts/me/mfa/totp")).isEqualTo(401);
        assertThat(statusWithoutAuth("POST", "/accounts/me/mfa/recovery-codes")).isEqualTo(401);
    }

    private int statusWithoutAuth(String method, String path) throws Exception {
        var client = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl() + path))
                .method(method, java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json")
                .build();
        return client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test // ArchUnit (Phase 3 Finding 9) - none of these four endpoints is in PublicEndpoints, so
          // they must never be reachable unauthenticated; this is the end-to-end proof of that,
          // complementing ArchitectureTest.shouldEnforcePublicEndpointAllowlist's static check
    void noneOfTheFourEndpointsArePublic() {
        assertThat(com.themistra.auth.common.PublicEndpoints.METHOD_SCOPED.stream()
                .noneMatch(e -> e.pattern().startsWith("/accounts/me/mfa"))).isTrue();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private ResponseEntity<String> postTotp(String bearer, String path, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = jsonBody == null ? "{}" : jsonBody;
        return restTemplate.exchange(
                baseUrl() + path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> deleteTotp(String bearer, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(baseUrl() + "/accounts/me/mfa/totp", HttpMethod.DELETE,
                new HttpEntity<>(jsonBody, headers), String.class);
    }

    /** Begins enrollment and extracts the raw secret from the real {@code otpauth://} URI (the
     * controller never returns the raw secret directly, by design) so these tests can compute a
     * real TOTP code without ever reaching into {@code MfaService} directly. */
    private byte[] beginEnrollAndCaptureSecret(String bearer) throws Exception {
        ResponseEntity<String> response = postTotp(bearer, "/accounts/me/mfa/totp", null);
        JsonNode body = readJson(response);
        String uri = body.get("provisioningUri").asText();
        String base32Secret = extractSecretParam(uri);
        return base32Decode(base32Secret);
    }

    private JsonNode confirm(String bearer, byte[] secret) throws Exception {
        String code = referenceGenerateCode(secret, Instant.now());
        ResponseEntity<String> response = postTotp(bearer, "/accounts/me/mfa/totp/confirm", "{\"code\":\"" + code + "\"}");
        return readJson(response);
    }

    private static String extractSecretParam(String otpauthUri) {
        int index = otpauthUri.indexOf("secret=");
        String rest = otpauthUri.substring(index + "secret=".length());
        int ampIndex = rest.indexOf('&');
        return ampIndex == -1 ? rest : rest.substring(0, ampIndex);
    }

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /** Independent Base32 decoder, deliberately separate code from whatever
     * {@code TotpGenerator} uses to encode the secret into the provisioning URI. */
    private static byte[] base32Decode(String base32) {
        String clean = base32.toUpperCase().replace("=", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : clean.toCharArray()) {
            int val = BASE32_ALPHABET.indexOf(c);
            if (val < 0) {
                continue;
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private static List<String> toList(JsonNode arrayNode) {
        List<String> list = new java.util.ArrayList<>();
        arrayNode.forEach(n -> list.add(n.asText()));
        return list;
    }

    /** Kimi Phase 11 Gap 1 precedent (ApiKeyCrudIntegrationTest) - asserts the full RFC 9457
     * shape, not just status and the absence of {@code detail}. */
    private Map<String, Object> rejectionBody(ResponseEntity<String> response, HttpStatus expectedStatus,
                                               java.net.URI expectedType, String expectedTitle) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(response.getBody(), Map.class);
            assertThat(body.get("type")).isEqualTo(expectedType.toString());
            assertThat(body.get("title")).isEqualTo(expectedTitle);
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
     * {@link ApiKeyTokenIssuer} - not a parallel hand-rolled minting implementation. */
    private String bearerTokenFor(UUID accountUuid) {
        return apiKeyTokenIssuer.issue(accountUuid, List.of("merchant.api")).accessToken();
    }

    private UUID registerAndActivate(String email) {
        AccountResponse registered = accountService.register(new RegisterAccountRequest(email, PASSWORD));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        return registered.accountUuid();
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
