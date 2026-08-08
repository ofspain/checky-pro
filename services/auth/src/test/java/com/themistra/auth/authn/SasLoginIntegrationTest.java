package com.themistra.auth.authn;

import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.AccountStatus;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.dto.AuditEventResponse;
import com.themistra.auth.authz.DuplicateRoleException;
import com.themistra.auth.authz.RoleService;
import com.themistra.auth.authz.dto.CreateRoleRequest;
import com.themistra.auth.mfa.MfaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end against real Postgres + Kafka (Testcontainers) and a real HTTP server
 * ({@code webEnvironment = RANDOM_PORT}) — the only way to prove the actual SAS form-login filter
 * chain (CSRF, {@link LoginFailureHandler}, {@link LoginSuccessHandler},
 * {@link AccountUserDetailsService}'s {@code isCurrentlyLocked}-driven gate, and — since T20 —
 * {@link TotpAuthenticationProvider}'s single-request password+MFA gate) behaves correctly
 * together, which no mocked unit test can. No {@code MockMvc} precedent exists in this module
 * (confirmed at Phase 0/1); {@link TestRestTemplate} is used instead, per the Phase 5 plan.
 *
 * <p>T20's tests reuse this class rather than a new one deliberately: they exercise the same
 * {@code /login} form-login filter chain, the same CSRF/cookie-handling helpers, and the same
 * {@code registerAndActivate}/{@code attemptLogin} fixtures this class already established and
 * hardened (Phase 11 gaps below). This file wasn't identified as "existing code this task touches"
 * until Phase 10 — a Phase 0 gap, noted honestly rather than silently working around it.</p>
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
    private LockoutStateRepository lockoutStateRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MfaService mfaService;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        // Phase 11 Gap 6: TestRestTemplate follows redirects by default (its underlying
        // SimpleClientHttpRequestFactory sets HttpURLConnection.setInstanceFollowRedirects(true)),
        // which would silently turn every 302 this test expects to observe into whatever the
        // Location target returns instead - making every HttpStatus.FOUND assertion below
        // meaningless without this override.
        restTemplate.getRestTemplate().setRequestFactory(new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        });
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
        // Phase 11 Gap 9: FOUND alone doesn't prove the login succeeded - a redirect back to
        // /login?error is also FOUND. The successful-login redirect target must differ from the
        // failure target.
        assertThat(attempt.response.getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).doesNotContain("/login?error"));
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

        // Phase 11 Gap 4: the response itself must prove the attempt was denied - status alone
        // (LOCKED) could also result from an implementation that let the password check proceed
        // and then re-locked the account via the same recordFailedAttempt call.
        assertThat(attempt.response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(attempt.response.getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).contains("/login?error"));
        // Rejected at Spring's pre-authentication gate before password verification - no
        // lockout_state change from this attempt itself.
        assertThat(accountService.getByUuid(accountUuid).status()).isEqualTo(AccountStatus.LOCKED);
    }

    @Test
    void wrongPasswordAgainstKnownAccountIncrementsCounterAndAudits() {
        UUID accountUuid = registerAndActivate("bad-password@example.com");

        attemptLogin("bad-password@example.com", "wrong-password-entirely");

        // Phase 11 Gap 5: a no-op failure handler would also leave isCurrentlyLocked() == false -
        // that alone doesn't prove the counter moved or the audit fired. Read the real persisted
        // state instead.
        assertThat(lockoutService.isCurrentlyLocked(accountUuid, Instant.now())).isFalse();
        Optional<LockoutState> persisted = lockoutStateRepository.findByAccountUuid(accountUuid);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getFailedAttempts()).isEqualTo(1);

        Page<AuditEventResponse> auditEvents = auditService.list(accountUuid, Pageable.unpaged());
        assertThat(auditEvents.getContent())
                .anySatisfy(event -> assertThat(event.eventType()).isEqualTo("login.failed"));
    }

    @Test
    void unknownEmailProducesTheSameResponseShapeAsAKnownAccountFailure() {
        LoginAttempt unknown = attemptLogin("nobody-at-all@example.com", "irrelevant");
        LoginAttempt known = attemptLogin(registerAndActivateEmail("known-bad-password@example.com"), "wrong-password");

        assertThat(unknown.response.getStatusCode()).isEqualTo(known.response.getStatusCode());
        assertThat(unknown.response.getHeaders().getLocation())
                .isEqualTo(known.response.getHeaders().getLocation());
    }

    @Test // T20, R24, named test shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization:
          // MERCHANT with no confirmed TOTP enrollment is blocked outright, uniformly (governing
          // design decision — same response shape as any other login failure).
    void merchantWithoutMfaEnrollmentCannotLogIn() {
        UUID accountUuid = registerAndActivate("merchant-no-mfa@example.com");
        ensureRoleExists("MERCHANT");
        roleService.assignRole(accountUuid, "MERCHANT", null);

        LoginAttempt attempt = attemptLogin("merchant-no-mfa@example.com", PASSWORD);

        assertThat(attempt.response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(attempt.response.getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).contains("/login?error"));
    }

    @Test // T20, R25, named test shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled: password
          // alone and a wrong code both fail; a correct TOTP code succeeds.
    void merchantWithConfirmedEnrollmentRequiresCorrectTotpOrRecoveryCode() {
        UUID accountUuid = registerAndActivate("merchant-with-mfa@example.com");
        ensureRoleExists("MERCHANT");
        roleService.assignRole(accountUuid, "MERCHANT", null);
        SeededEnrollment enrollment = seedConfirmedTotpEnrollment(accountUuid);

        LoginAttempt passwordOnly = attemptLogin("merchant-with-mfa@example.com", PASSWORD);
        assertThat(passwordOnly.response.getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).contains("/login?error"));

        LoginAttempt wrongCode = attemptLogin("merchant-with-mfa@example.com", PASSWORD, "000000");
        assertThat(wrongCode.response.getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).contains("/login?error"));
        // Phase 11 finding #1: prove the failure actually persisted an audit row, not just that the
        // HTTP response looked like a failure — AuditService.record is REQUIRES_NEW, so a
        // transaction-propagation or event-routing regression could drop this without any existing
        // test noticing.
        assertThat(auditService.list(accountUuid, Pageable.unpaged()).getContent())
                .anySatisfy(event -> assertThat(event.eventType()).isEqualTo("mfa.failed"));

        LoginAttempt withTotp = attemptLoginWithFreshTotpCode(
                "merchant-with-mfa@example.com", PASSWORD, enrollment.secret());
        assertThat(withTotp.response.getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).doesNotContain("/login?error"));
    }

    @Test // T20, R25 recovery-code branch, same named test as above
    void merchantCanLoginWithAnUnusedRecoveryCodeButNotWithItASecondTime() {
        UUID accountUuid = registerAndActivate("merchant-recovery@example.com");
        ensureRoleExists("MERCHANT");
        roleService.assignRole(accountUuid, "MERCHANT", null);
        SeededEnrollment enrollment = seedConfirmedTotpEnrollment(accountUuid);
        String recoveryCode = enrollment.recoveryCodes().getFirst();

        LoginAttempt firstUse = attemptLogin("merchant-recovery@example.com", PASSWORD, recoveryCode);
        assertThat(firstUse.response.getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).doesNotContain("/login?error"));

        // Phase 11 finding #3: the recovery code's single-use guarantee (L6) needs to be proven
        // through the actual login path, not just at MfaServiceTest's mocked-repository level.
        LoginAttempt secondUse = attemptLogin("merchant-recovery@example.com", PASSWORD, recoveryCode);
        assertThat(secondUse.response.getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).contains("/login?error"));
    }

    @Test // T20 Phase 8 finding #2's fix, full-stack: a USER account (no mandatory-MFA role) that
          // voluntarily enrolled must still be required to pass MFA — the original implementation
          // gated the whole MFA step on role and silently skipped this exact case.
    void voluntarilyEnrolledUserAccountStillRequiresMfaAtLogin() {
        UUID accountUuid = registerAndActivate("user-voluntary-mfa@example.com");
        // Deliberately no role assignment — default account, not MERCHANT/ADMIN.
        SeededEnrollment enrollment = seedConfirmedTotpEnrollment(accountUuid);

        LoginAttempt passwordOnly = attemptLogin("user-voluntary-mfa@example.com", PASSWORD);
        assertThat(passwordOnly.response.getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).contains("/login?error"));

        LoginAttempt withTotp = attemptLoginWithFreshTotpCode(
                "user-voluntary-mfa@example.com", PASSWORD, enrollment.secret());
        assertThat(withTotp.response.getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).doesNotContain("/login?error"));
    }

    @Test // Phase 11 finding #2: R27's negative case, explicitly — a plain account with no
          // mandatory-MFA role and no enrollment at all logs in on password alone. Already implied
          // by every other successful-login test in this class using unrelated no-role accounts,
          // but not previously asserted under a name that states the intent directly.
    void userWithoutEnrollmentLogsInWithPasswordOnly() {
        registerAndActivate("user-no-mfa-at-all@example.com");

        LoginAttempt attempt = attemptLogin("user-no-mfa-at-all@example.com", PASSWORD);

        assertThat(attempt.response.getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).doesNotContain("/login?error"));
    }

    @Test // T20 Phase 8 finding #3's fix, full-stack: the same valid TOTP code cannot be reused for
          // a second login attempt immediately after the first.
    void sameValidTotpCodeCannotBeUsedTwice() {
        UUID accountUuid = registerAndActivate("merchant-no-replay@example.com");
        ensureRoleExists("MERCHANT");
        roleService.assignRole(accountUuid, "MERCHANT", null);
        SeededEnrollment enrollment = seedConfirmedTotpEnrollment(accountUuid);
        String code = referenceGenerateCode(enrollment.secret(), Instant.now());

        LoginAttempt first = attemptLogin("merchant-no-replay@example.com", PASSWORD, code);
        assertThat(first.response.getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).doesNotContain("/login?error"));

        LoginAttempt replay = attemptLogin("merchant-no-replay@example.com", PASSWORD, code);
        assertThat(replay.response.getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).contains("/login?error"));
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
        return attemptLogin(email, password, null);
    }

    /** Phase 11 finding #8: generates the TOTP code and submits it in one call, rather than a
     * separate {@code referenceGenerateCode(secret, Instant.now())} followed later by
     * {@code attemptLogin(...)} — structurally guarantees the code is always fresh at submission
     * time (minimizing, not eliminating, the live-clock drift risk inherent to any real HTTP round
     * trip against TOTP's 90s tolerance window), rather than relying on every call site doing this
     * correctly by convention. Not used by the replay test, which deliberately reuses one captured
     * code across two attempts. */
    private LoginAttempt attemptLoginWithFreshTotpCode(String email, String password, byte[] secret) {
        return attemptLogin(email, password, referenceGenerateCode(secret, Instant.now()));
    }

    /** {@code mfaCode} is the single optional field T20's login form adds (O4, single-request
     * design) — {@code null} submits password-only, exactly like the original two-arg overload. */
    private LoginAttempt attemptLogin(String email, String password, String mfaCode) {
        ResponseEntity<String> loginPage = restTemplate.getForEntity(baseUrl + "/login", String.class);
        List<String> setCookies = loginPage.getHeaders().get(HttpHeaders.SET_COOKIE);
        String csrfToken = extractCsrfToken(loginPage.getBody());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", email);
        form.add("password", password);
        form.add("_csrf", csrfToken);
        if (mfaCode != null) {
            form.add("mfaCode", mfaCode);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (setCookies != null) {
            headers.put(HttpHeaders.COOKIE, setCookies);
        }

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/login", HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
        return new LoginAttempt(response);
    }

    /** Roles are shared, class-scoped DB state (no per-test rollback in this test style) — created
     * once and reused, rather than failing every test after the first that needs the same role. */
    private void ensureRoleExists(String roleName) {
        try {
            roleService.createRole(new CreateRoleRequest(roleName, null));
        } catch (DuplicateRoleException e) {
            // Already created by an earlier test in this class - fine.
        }
    }

    /** Enrolls and confirms TOTP MFA through the real {@link MfaService} (no self-service HTTP
     * endpoint exists yet — task 19), so the login-time gate this task builds has a genuinely
     * confirmed enrollment to exercise, the same way production would produce one. */
    private SeededEnrollment seedConfirmedTotpEnrollment(UUID accountUuid) {
        MfaService.BeginEnrollResult begun = mfaService.beginEnroll(accountUuid);
        String code = referenceGenerateCode(begun.secret(), Instant.now());
        MfaService.ConfirmResult confirmed = mfaService.confirm(accountUuid, code);
        return new SeededEnrollment(begun.secret(), confirmed.recoveryCodes());
    }

    private record SeededEnrollment(byte[] secret, List<String> recoveryCodes) {
    }

    /** Independent RFC 4226/6238 HOTP/TOTP implementation, deliberately separate code from {@link
     * com.themistra.auth.mfa.TotpVerifier} — same discipline {@code TotpVerifierTest} and {@code
     * MfaServicePersistenceIntegrationTest} already apply at their own layers. */
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
