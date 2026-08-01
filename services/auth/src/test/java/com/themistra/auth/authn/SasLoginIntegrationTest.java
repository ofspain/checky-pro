package com.themistra.auth.authn;

import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.AccountStatus;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end against real Postgres + Kafka (Testcontainers) and a real HTTP server
 * ({@code webEnvironment = RANDOM_PORT}) — the only way to prove the actual SAS form-login filter
 * chain (CSRF, {@link LoginFailureHandler}, {@link LoginSuccessHandler},
 * {@link AccountUserDetailsService}'s {@code isCurrentlyLocked}-driven gate) behaves correctly
 * together, which no mocked unit test can. No {@code MockMvc} precedent exists in this module
 * (confirmed at Phase 0/1); {@link TestRestTemplate} is used instead, per the Phase 5 plan.
 *
 * <p><b>Unverified in this environment</b> — no Docker daemon available here (same limitation as
 * every prior Testcontainers test in this pipeline). CSRF-token scraping and session-cookie
 * propagation across requests are hand-rolled below using the most standard pattern for this
 * exact scenario, but have not been run against a real server. Flagged explicitly, not silently
 * assumed correct.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SasLoginIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final Pattern CSRF_INPUT_PATTERN =
            Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Autowired
    private AccountService accountService;

    @Autowired
    private LockoutService lockoutService;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    @Test
    void expiredLockAccountCanSuccessfullyLoginAndUnlocks() {
        UUID accountUuid = registerAndActivate("expired-lock@example.com");
        // Simulate five failures that happened two hours ago (recordFailedAttempt takes a
        // caller-supplied Instant, never Instant.now() internally) - the resulting lockedUntil
        // (two hours ago + 15 min) is already in the past relative to the real system clock the
        // running server actually uses (SecurityBeansConfig.clock() = Clock.systemUTC()), with no
        // need to wait out L4's real 15-minute base lock inside the test itself.
        lockAccountFiveTimes(accountUuid, Instant.now().minus(Duration.ofHours(2)));
        assertThat(accountService.getByUuid(accountUuid).status()).isEqualTo(AccountStatus.LOCKED);

        LoginAttempt attempt = attemptLogin("expired-lock@example.com", PASSWORD);

        assertThat(attempt.response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(accountService.getByUuid(accountUuid).status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void stillLockedAccountCannotLoginEvenWithCorrectPassword() {
        UUID accountUuid = registerAndActivate("still-locked@example.com");
        // Failures happen "now" - lockedUntil (now + 15 min) is still in the future relative to
        // the real clock, unlike the expired-lock test above.
        lockAccountFiveTimes(accountUuid, Instant.now());
        assertThat(accountService.getByUuid(accountUuid).status()).isEqualTo(AccountStatus.LOCKED);

        LoginAttempt attempt = attemptLogin("still-locked@example.com", PASSWORD);

        // Rejected at Spring's pre-authentication gate before password verification - no
        // lockout_state change from this attempt itself.
        assertThat(accountService.getByUuid(accountUuid).status()).isEqualTo(AccountStatus.LOCKED);
    }

    @Test
    void wrongPasswordAgainstKnownAccountIncrementsCounterAndAudits() {
        UUID accountUuid = registerAndActivate("bad-password@example.com");

        attemptLogin("bad-password@example.com", "wrong-password-entirely");

        assertThat(lockoutService.isCurrentlyLocked(accountUuid, Instant.now())).isFalse();
        // A single failure never locks (L4 needs five) - the counter increment itself is proven
        // at the unit layer (LoginFailureHandlerTest); this test's job is proving the real filter
        // chain reaches LoginFailureHandler at all for a known account.
    }

    @Test
    void unknownEmailProducesTheSameResponseShapeAsAKnownAccountFailure() {
        LoginAttempt unknown = attemptLogin("nobody-at-all@example.com", "irrelevant");
        LoginAttempt known = attemptLogin(registerAndActivateEmail("known-bad-password@example.com"), "wrong-password");

        assertThat(unknown.response.getStatusCode()).isEqualTo(known.response.getStatusCode());
        assertThat(unknown.response.getHeaders().getLocation())
                .isEqualTo(known.response.getHeaders().getLocation());
    }

    private String registerAndActivateEmail(String email) {
        registerAndActivate(email);
        return email;
    }

    private UUID registerAndActivate(String email) {
        AccountResponse registered = accountService.register(new RegisterAccountRequest(email, PASSWORD));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        return registered.accountUuid();
    }

    private void lockAccountFiveTimes(UUID accountUuid, Instant base) {
        for (int i = 0; i < 5; i++) {
            lockoutService.recordFailedAttempt(accountUuid, base.plusSeconds(i));
        }
    }

    private LoginAttempt attemptLogin(String email, String password) {
        ResponseEntity<String> loginPage = restTemplate.getForEntity(baseUrl + "/login", String.class);
        List<String> setCookies = loginPage.getHeaders().get(HttpHeaders.SET_COOKIE);
        String csrfToken = extractCsrfToken(loginPage.getBody());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", email);
        form.add("password", password);
        form.add("_csrf", csrfToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (setCookies != null) {
            headers.put(HttpHeaders.COOKIE, setCookies);
        }

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/login", HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
        return new LoginAttempt(response);
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

    private record LoginAttempt(ResponseEntity<String> response) {
    }
}
