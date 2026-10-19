package com.themistra.auth.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.apikey.ApiKeyTokenIssuer;
import com.themistra.auth.authz.DuplicateRoleException;
import com.themistra.auth.authz.RoleService;
import com.themistra.auth.authz.dto.CreateRoleRequest;
import com.themistra.auth.common.ProblemTypes;
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
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Open-issues sweep (2026-10-19): a role-denied call to an {@code @PreAuthorize}-protected admin
 * endpoint must return {@code 403}, not {@code 500}. Spring Security's method-level denial throws
 * {@code AuthorizationDeniedException}, which {@code ApiExceptionHandler} used to send to its
 * catch-all. {@code AdminAccountControllerTest} only checks the annotation's SpEL by reflection, so
 * this is the first test of the real HTTP behaviour.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AdminAccountAuthorizationIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery-staple-42";

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

    @Test
    void aCallerWithoutAnAdminRoleGetsA403ProblemNotA500() throws Exception {
        UUID caller = activeAccount("plain");
        UUID target = activeAccount("target");

        ResponseEntity<String> response = getAccount(bearerFor(caller), target);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("type").asText()).isEqualTo(ProblemTypes.FORBIDDEN.toString());
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.has("trace_id")).as("a 403 is not an internal error").isFalse();
    }

    @Test
    void anAdminCallerStillGetsTheAccount() {
        UUID admin = activeAccount("admin");
        mfaConfirm(admin); // D-031: ADMIN requires confirmed MFA before assignment
        ensureRoleExists("ADMIN");
        roleService.assignRole(admin, "ADMIN", null);
        UUID target = activeAccount("target");

        ResponseEntity<String> response = getAccount(bearerFor(admin), target);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(target.toString());
    }

    private ResponseEntity<String> getAccount(String bearer, UUID accountUuid) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        return restTemplate.exchange("http://localhost:" + port + "/admin/accounts/" + accountUuid,
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private UUID activeAccount(String label) {
        AccountResponse registered = accountService.register(new RegisterAccountRequest(
                "admin-authz-" + label + "-" + UUID.randomUUID() + "@example.com", PASSWORD));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        return registered.accountUuid();
    }

    private String bearerFor(UUID accountUuid) {
        return apiKeyTokenIssuer.issue(accountUuid, List.of()).accessToken();
    }

    private void ensureRoleExists(String roleName) {
        try {
            roleService.createRole(new CreateRoleRequest(roleName, null));
        } catch (DuplicateRoleException e) {
            // Roles are shared, class-scoped DB state - already created by another test.
        }
    }

    private void mfaConfirm(UUID accountUuid) {
        MfaService.BeginEnrollResult begun = mfaService.beginEnroll(accountUuid);
        mfaService.confirm(accountUuid, totp(begun.secret(), Instant.now()));
    }

    /** RFC 6238 TOTP (HMAC-SHA1, 30 s step, 6 digits), as the sibling integration tests compute it. */
    private static String totp(byte[] secret, Instant at) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(at.getEpochSecond() / 30).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
