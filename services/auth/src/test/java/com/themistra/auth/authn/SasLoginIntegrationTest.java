package com.themistra.auth.authn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
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
import com.themistra.auth.token.AuthClientsProperties;
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
import org.springframework.web.util.UriComponentsBuilder;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>T22 extends this class again, with the one thing T20 deliberately declined to attempt: driving
 * the full {@code /oauth2/authorize} → {@code /login} → (resumed) {@code /oauth2/authorize} →
 * {@code /oauth2/token} round trip and inspecting an actually-issued token's claims, matching
 * {@code auth-decisions.md} D-023's precedent against writing that category of test unverified.
 * Confirmed run against a real server (Docker/Testcontainers reliably available as of 2026-08-08) —
 * see that update for the citext/{@code existsByEmail} fix and the CSRF-regex fix this file needed
 * before any of its tests, T20's or T22's, could pass at all.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SasLoginIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";
    // 2026-08-08 fix: the real default-login-page markup is
    // <input name="_csrf" type="hidden" value="..." /> — "type=\"hidden\"" sits between name and
    // value, which the original "name=\"_csrf\"\s+value=\"...\"" pattern (assuming they're
    // adjacent) never matched. Confirmed by capturing and inspecting the actual response body
    // once Docker/Testcontainers made that possible for the first time — this test was written
    // and believed correct but never actually run against a real server before.
    private static final Pattern CSRF_INPUT_PATTERN =
            Pattern.compile("name=\"_csrf\"[^>]*\\bvalue=\"([^\"]+)\"");

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
    private AuthClientsProperties authClientsProperties;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test // T22, R24, named test shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization —
          // full-authorize-flow version: proves the /oauth2/authorize flow itself never yields an
          // authorization code, not just that /login redirects to an error (T20's
          // merchantWithoutMfaEnrollmentCannotLogIn already proved the latter, at the /login layer).
    void merchantWithoutEnrollmentCannotFinishAuthorizeFlow() {
        UUID accountUuid = registerAndActivate("merchant-no-mfa-authorize@example.com");
        ensureRoleExists("MERCHANT");
        roleService.assignRole(accountUuid, "MERCHANT", null);

        FullFlowResult result = attemptFullAuthorizeFlow(
                "merchant-no-mfa-authorize@example.com", PASSWORD, null,
                generatePkce(), UUID.randomUUID().toString());

        assertThat(result.authorizationCode()).isEmpty();
        assertThat(result.loginResponse().getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).contains("/login?error"));
    }

    @Test // T22, R25, named test shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled —
          // full-authorize-flow version: password alone doesn't complete the flow; a valid TOTP
          // code does. Phase 8 finding #7: this proves the TOTP branch only — the named test's
          // "or recovery code" half is deliberately not re-proven at this layer (frozen brief
          // disposition #7), since T20's merchantCanLoginWithAnUnusedRecoveryCodeButNotWithItASecondTime
          // already proves it at /login, and three more HTTP hops per attempt would add runtime
          // without adding coverage of anything R25/R26 don't already establish together.
    void confirmedMfaRequiresCodeToFinishAuthorizeFlow() {
        UUID accountUuid = registerAndActivate("merchant-mfa-authorize@example.com");
        ensureRoleExists("MERCHANT");
        roleService.assignRole(accountUuid, "MERCHANT", null);
        SeededEnrollment enrollment = seedConfirmedTotpEnrollment(accountUuid);

        FullFlowResult passwordOnly = attemptFullAuthorizeFlow(
                "merchant-mfa-authorize@example.com", PASSWORD, null,
                generatePkce(), UUID.randomUUID().toString());
        assertThat(passwordOnly.authorizationCode()).isEmpty();
        // Phase 8 finding #3: match R24's test rigor — a missing code alone doesn't prove *why*;
        // the redirect must actually be the uniform failure target, not some other silent drop.
        assertThat(passwordOnly.loginResponse().getHeaders().getLocation())
                .isNotNull()
                .satisfies(location -> assertThat(location.toString()).contains("/login?error"));

        FullFlowResult withTotp = attemptFullAuthorizeFlow(
                "merchant-mfa-authorize@example.com", PASSWORD,
                referenceGenerateCode(enrollment.secret(), Instant.now()),
                generatePkce(), UUID.randomUUID().toString());
        assertThat(withTotp.authorizationCode()).isPresent();
    }

    @Test // T22, R26, named test shouldIssueTokenWithOtpAmrAndAcrAfterMfa — the genuinely new
          // capability this task adds: inspecting an actually-issued token's claims, rather than
          // TokenClaimsCustomizer directly (T20's unit test) or the /login response shape alone
          // (T20's integration test) — closing the gap auth-decisions.md D-023 and T20 both
          // deliberately left as unverified-without-running-it.
    void issuedTokenHasOtpAmrAndAcrAfterMfa() throws ParseException {
        UUID accountUuid = registerAndActivate("merchant-mfa-token@example.com");
        ensureRoleExists("MERCHANT");
        roleService.assignRole(accountUuid, "MERCHANT", null);
        SeededEnrollment enrollment = seedConfirmedTotpEnrollment(accountUuid);
        PkcePair pkce = generatePkce();
        String state = UUID.randomUUID().toString();

        FullFlowResult result = attemptFullAuthorizeFlow(
                "merchant-mfa-token@example.com", PASSWORD,
                referenceGenerateCode(enrollment.secret(), Instant.now()), pkce, state);
        assertThat(result.authorizationCode()).isPresent();
        // Phase 8 finding #1: the state a real client sent must be the state it gets back —
        // that round trip is exactly what protects the authorization response from CSRF.
        assertThat(result.returnedState()).contains(state);

        String accessToken = exchangeCodeForToken(result.authorizationCode().orElseThrow(), pkce.verifier());
        JWTClaimsSet claims = parseClaims(accessToken);

        assertThat(claims.getStringListClaim("amr")).containsExactly("pwd", "otp");
        assertThat(claims.getStringClaim("acr")).isEqualTo("urn:themistra:acr:otp");
    }

    @Test // T22 positive control (frozen brief disposition #6): a non-mandatory-role account with
          // no enrollment completes the full flow on password alone.
    void issuedTokenHasPwdOnlyAmrThroughFullFlowWhenMfaNotRequired() throws ParseException {
        registerAndActivate("user-no-mfa-token@example.com");
        PkcePair pkce = generatePkce();
        String state = UUID.randomUUID().toString();

        FullFlowResult result = attemptFullAuthorizeFlow(
                "user-no-mfa-token@example.com", PASSWORD, null, pkce, state);
        assertThat(result.authorizationCode()).isPresent();
        assertThat(result.returnedState()).contains(state);

        String accessToken = exchangeCodeForToken(result.authorizationCode().orElseThrow(), pkce.verifier());
        JWTClaimsSet claims = parseClaims(accessToken);

        assertThat(claims.getStringListClaim("amr")).containsExactly("pwd");
        assertThat(claims.getStringClaim("acr")).isEqualTo("urn:themistra:acr:pwd");
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
        return new LoginAttempt(postLoginForm(email, password, mfaCode, csrfToken, setCookies));
    }

    /** T22: factored out of {@link #attemptLogin} so the full-authorize-flow helper below can post
     * the same login form carrying cookies accumulated from an earlier, unauthenticated
     * {@code /oauth2/authorize} hop instead of a freshly-fetched {@code /login} page's cookies —
     * the submission mechanics are identical either way, only where the cookies came from differs. */
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
                baseUrl + "/login", HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
    }

    /** T22: a cookie-carrying GET, reused across every hop of the full authorize flow that isn't
     * already covered by {@link #attemptLogin}'s own {@code /login} fetch. */
    private ResponseEntity<String> getWithCookies(String url, List<String> cookies) {
        HttpHeaders headers = new HttpHeaders();
        if (cookies != null && !cookies.isEmpty()) {
            headers.put(HttpHeaders.COOKIE, toRequestCookiePairs(cookies));
        }
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    /** T22: combines cookies across a redirect chain, replacing (not duplicating) any cookie whose
     * name repeats — essential because Spring Security regenerates the session id on successful
     * authentication (session-fixation protection), so the post-login {@code Set-Cookie} for
     * {@code JSESSIONID} must win over the one captured at the unauthenticated
     * {@code /oauth2/authorize} hop, not be sent alongside it. Stores the raw {@code Set-Cookie}
     * values (attributes and all) purely for name-based de-duplication — {@link #toRequestCookiePairs}
     * is what strips them down to {@code name=value} before anything is actually sent. */
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

    /** Phase 8 finding #4: a {@code Cookie} request header must contain only {@code name=value}
     * pairs (RFC 6265 §4.2) — response-only attributes like {@code Path=/}/{@code HttpOnly} have no
     * business there. Sending raw {@code Set-Cookie} values as-is (this class's original behavior,
     * predating T22) happened to work because Tomcat's cookie parser is lenient, not because it was
     * correct; this strips each value down to its {@code name=value} prefix — the part before the
     * first {@code ;} — right before it's ever placed on an outgoing request. */
    private static List<String> toRequestCookiePairs(List<String> setCookieHeaders) {
        return setCookieHeaders.stream().map(SasLoginIntegrationTest::cookieNameValuePair).toList();
    }

    private static String cookieNameValuePair(String setCookieHeader) {
        int semicolonIndex = setCookieHeader.indexOf(';');
        return (semicolonIndex < 0 ? setCookieHeader : setCookieHeader.substring(0, semicolonIndex)).trim();
    }

    private String resolveLocation(URI location) {
        return location.isAbsolute() ? location.toString() : baseUrl + location;
    }

    /** RFC 7636 S256: a random URL-safe verifier and its SHA-256/base64url challenge. */
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
        return UriComponentsBuilder.fromUriString(baseUrl + "/oauth2/authorize")
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

    /**
     * T22 — the governing mechanism (frozen brief), as corrected by what running it actually
     * showed: unauthenticated {@code /oauth2/authorize} first, which SAS redirects to
     * {@code /login}, with no session/cookie created yet at that point (confirmed empirically —
     * the first response carries no {@code Set-Cookie} at all). A failed login redirects to
     * {@code /login?error} — no code exists anywhere, and that redirect alone is the proof for
     * R24/R25's negative branch, with no further request made or needed.
     *
     * <p>A <em>successful</em> login does <strong>not</strong> resume the original
     * {@code /oauth2/authorize} request the way a standard {@code RequestCache}-protected resource
     * would — confirmed empirically, this is exactly the contingency the frozen brief documented:
     * {@link LoginSuccessHandler}'s {@code SavedRequestAwareAuthenticationSuccessHandler} redirects
     * to the default target ({@code /}) instead, because Spring Authorization Server's
     * authorization endpoint doesn't integrate with that mechanism the way a normal protected
     * resource does. The correct, working completion is to re-issue the <em>same</em>
     * {@code /oauth2/authorize} request with the now-authenticated session — SAS then completes it
     * directly and redirects to the SPA's {@code redirect_uri} with the authorization code.</p>
     */
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
        // Phase 8 finding #2: an unexpected non-redirect here (SAS returning an error page instead
        // of completing the authorization) must fail loudly and specifically, not silently produce
        // an empty authorizationCode that a calling test can only report as "code should be
        // present" - the actual cause would be invisible.
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

    /** No {@code Authorization} header — the SPA client has {@code ClientAuthenticationMethod.NONE}
     * (public client, PKCE is its only proof of possession). Fails loudly on a missing
     * {@code access_token} rather than returning null, so a broken exchange fails the calling test
     * with a clear cause instead of a confusing downstream NPE. */
    private String exchangeCodeForToken(String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", authClientsProperties.spa().redirectUris().getFirst());
        form.add("client_id", authClientsProperties.spa().clientId());
        form.add("code_verifier", codeVerifier);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/oauth2/token", HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
        // Phase 8 finding #8: check the status before trusting the body shape — a non-2xx error
        // response (RFC 6749's {"error":"..."} shape) parses as valid JSON with no access_token
        // and would already fail loudly below, but checking status first makes the actual failure
        // mode explicit instead of inferred from an absent field.
        if (response.getStatusCode() != HttpStatus.OK) {
            throw new IllegalStateException("/oauth2/token returned " + response.getStatusCode() + ": " + response.getBody());
        }

        JsonNode json;
        try {
            json = objectMapper.readTree(response.getBody());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not parse /oauth2/token response body: " + response.getBody(), e);
        }
        JsonNode accessToken = json.get("access_token");
        if (accessToken == null) {
            throw new IllegalStateException("/oauth2/token response had no access_token: " + response.getBody());
        }
        return accessToken.asText();
    }

    /** Claims only — no JWKS fetch, no signature verification (frozen brief: this test is about
     * {@code TokenClaimsCustomizer}'s output, not signing/JWKS correctness, which is covered
     * elsewhere). */
    private static JWTClaimsSet parseClaims(String jwt) {
        try {
            return JWTParser.parse(jwt).getJWTClaimsSet();
        } catch (ParseException e) {
            throw new IllegalStateException("Could not parse issued JWT", e);
        }
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
