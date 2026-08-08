package com.themistra.auth.authn;

import com.themistra.auth.authz.RoleService;
import com.themistra.auth.mfa.InvalidTotpCodeException;
import com.themistra.auth.mfa.MfaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain JUnit, mocked collaborators, no Spring context — mirrors this codebase's established unit
 * testing convention. Covers R24, R25 (including T20 Phase 8 finding #2's fix — MFA must trigger
 * on confirmed enrollment alone, independent of role), R29, and the uniform-failure/details
 * handling introduced resolving Phase 7/8/9 findings #1, #4, #5, #7.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class TotpAuthenticationProviderTest {

    private static final UUID ACCOUNT_UUID = UUID.randomUUID();
    private static final String EMAIL = "merchant@example.com";
    private static final String RAW_PASSWORD = "correct-horse-battery";
    private static final String ENCODED_PASSWORD = "{bcrypt}encoded";

    @Mock
    private AccountUserDetailsService accountUserDetailsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleService roleService;

    @Mock
    private MfaService mfaService;

    private TotpAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TotpAuthenticationProvider(accountUserDetailsService, passwordEncoder, roleService, mfaService);
    }

    @Test
    void unknownEmailFailsUniformly() {
        when(accountUserDetailsService.loadUserByUsername(EMAIL))
                .thenThrow(new UsernameNotFoundException("Bad credentials"));

        assertThatThrownBy(() -> provider.authenticate(request(EMAIL, RAW_PASSWORD, null)))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(passwordEncoder, roleService, mfaService);
    }

    @Test
    void disabledAccountFailsUniformlyBeforePasswordIsChecked() {
        UserDetails disabled = userDetails(ACCOUNT_UUID, ENCODED_PASSWORD, false, true, Set.of());
        when(accountUserDetailsService.loadUserByUsername(EMAIL)).thenReturn(disabled);

        assertThatThrownBy(() -> provider.authenticate(request(EMAIL, RAW_PASSWORD, null)))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(passwordEncoder, roleService, mfaService);
    }

    @Test
    void lockedAccountFailsUniformlyBeforePasswordIsChecked() {
        UserDetails locked = userDetails(ACCOUNT_UUID, ENCODED_PASSWORD, true, false, Set.of());
        when(accountUserDetailsService.loadUserByUsername(EMAIL)).thenReturn(locked);

        assertThatThrownBy(() -> provider.authenticate(request(EMAIL, RAW_PASSWORD, null)))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(passwordEncoder, roleService, mfaService);
    }

    @Test
    void wrongPasswordFailsUniformlyBeforeMfaIsChecked() {
        UserDetails userDetails = userDetails(ACCOUNT_UUID, ENCODED_PASSWORD, true, true, Set.of());
        when(accountUserDetailsService.loadUserByUsername(EMAIL)).thenReturn(userDetails);
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(request(EMAIL, RAW_PASSWORD, null)))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(roleService, mfaService);
    }

    @Test // R27: no confirmed enrollment, role does not require MFA (L10)
    void correctPasswordNoEnrollmentAndNoMandatoryRoleSucceedsPasswordOnly() {
        givenActiveAccountWithCorrectPassword();
        when(mfaService.hasConfirmedTotpEnrollment(ACCOUNT_UUID)).thenReturn(false);
        when(roleService.resolveEffectiveRoles(ACCOUNT_UUID)).thenReturn(Set.of("USER"));

        Authentication result = provider.authenticate(request(EMAIL, RAW_PASSWORD, null));

        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .doesNotContain("OTP_VERIFIED");
        verify(mfaService, never()).verifyTotpCodeForLogin(any(), any());
        verify(mfaService, never()).verifyRecoveryCode(any(), any());
    }

    @Test // R24: MERCHANT with no confirmed enrollment is blocked outright
    void merchantWithoutEnrollmentIsBlocked() {
        givenActiveAccountWithCorrectPassword();
        when(mfaService.hasConfirmedTotpEnrollment(ACCOUNT_UUID)).thenReturn(false);
        when(roleService.resolveEffectiveRoles(ACCOUNT_UUID)).thenReturn(Set.of("MERCHANT"));

        assertThatThrownBy(() -> provider.authenticate(request(EMAIL, RAW_PASSWORD, null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test // R24, L10: ADMIN with no confirmed enrollment is blocked outright too — both mandatory
          // roles named in L10, not just MERCHANT
    void adminWithoutEnrollmentIsBlocked() {
        givenActiveAccountWithCorrectPassword();
        when(mfaService.hasConfirmedTotpEnrollment(ACCOUNT_UUID)).thenReturn(false);
        when(roleService.resolveEffectiveRoles(ACCOUNT_UUID)).thenReturn(Set.of("ADMIN"));

        assertThatThrownBy(() -> provider.authenticate(request(EMAIL, RAW_PASSWORD, null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test // T20 Phase 8 finding #2's fix: R25 conditions on enrollment, not role. A USER account
          // that voluntarily enrolled must still pass MFA — the original implementation gated the
          // whole MFA step on isMfaRequired() (role only) and silently skipped this case.
    void voluntarilyEnrolledUserAccountStillRequiresMfa() {
        givenActiveAccountWithCorrectPassword();
        when(mfaService.hasConfirmedTotpEnrollment(ACCOUNT_UUID)).thenReturn(true);

        Authentication result = provider.authenticate(request(EMAIL, RAW_PASSWORD, "123456"));

        verify(mfaService).verifyTotpCodeForLogin(ACCOUNT_UUID, "123456");
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("OTP_VERIFIED");
        // R25 short-circuits on enrollment alone — the role lookup for R24's separate block must
        // never even run once a confirmed enrollment is found.
        verify(roleService, never()).resolveEffectiveRoles(any());
    }

    @Test // Same fix, recovery-code branch, for completeness
    void voluntarilyEnrolledUserAccountCanUseARecoveryCode() {
        givenActiveAccountWithCorrectPassword();
        when(mfaService.hasConfirmedTotpEnrollment(ACCOUNT_UUID)).thenReturn(true);
        String recoveryCode = "a".repeat(43);

        Authentication result = provider.authenticate(request(EMAIL, RAW_PASSWORD, recoveryCode));

        verify(mfaService).verifyRecoveryCode(ACCOUNT_UUID, recoveryCode);
        verify(mfaService, never()).verifyTotpCodeForLogin(any(), any());
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("OTP_VERIFIED");
    }

    @Test // R25: a 6-digit submission is dispatched as a TOTP code, not a recovery code
    void sixDigitCodeIsDispatchedAsTotp() {
        givenActiveAccountWithCorrectPassword();
        when(mfaService.hasConfirmedTotpEnrollment(ACCOUNT_UUID)).thenReturn(true);

        provider.authenticate(request(EMAIL, RAW_PASSWORD, "000000"));

        verify(mfaService).verifyTotpCodeForLogin(ACCOUNT_UUID, "000000");
        verify(mfaService, never()).verifyRecoveryCode(any(), any());
    }

    @Test // R29: a wrong TOTP code fails uniformly, same as a wrong password
    void wrongTotpCodeFailsUniformly() {
        givenActiveAccountWithCorrectPassword();
        when(mfaService.hasConfirmedTotpEnrollment(ACCOUNT_UUID)).thenReturn(true);
        org.mockito.Mockito.doThrow(new InvalidTotpCodeException())
                .when(mfaService).verifyTotpCodeForLogin(ACCOUNT_UUID, "999999");

        assertThatThrownBy(() -> provider.authenticate(request(EMAIL, RAW_PASSWORD, "999999")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test // Missing/blank code with a confirmed enrollment fails uniformly, without ever calling
          // into MfaService — there is nothing to verify.
    void blankMfaCodeFailsUniformlyWithoutCallingMfaService() {
        givenActiveAccountWithCorrectPassword();
        when(mfaService.hasConfirmedTotpEnrollment(ACCOUNT_UUID)).thenReturn(true);

        assertThatThrownBy(() -> provider.authenticate(request(EMAIL, RAW_PASSWORD, "   ")))
                .isInstanceOf(BadCredentialsException.class);
        verify(mfaService, never()).verifyTotpCodeForLogin(any(), any());
        verify(mfaService, never()).verifyRecoveryCode(any(), any());
    }

    @Test // T20 Phase 7/8 finding #4's fix: an unexpected failure (simulating a KMS/DB failure
          // inside MfaService) must still fail uniformly to the caller — only operator-visible
          // logging changes, not the client-facing response. Phase 11 finding #5: the log line
          // itself must actually be asserted, not just the uniform response — a regression that
          // silently dropped the log.warn(...) call would otherwise pass unnoticed.
    void unexpectedMfaServiceFailureStillFailsUniformlyAndLogsAWarning(CapturedOutput output) {
        givenActiveAccountWithCorrectPassword();
        when(mfaService.hasConfirmedTotpEnrollment(ACCOUNT_UUID)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("KMS unreachable"))
                .when(mfaService).verifyTotpCodeForLogin(ACCOUNT_UUID, "123456");

        assertThatThrownBy(() -> provider.authenticate(request(EMAIL, RAW_PASSWORD, "123456")))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(output.getOut() + output.getErr())
                .contains("WARN")
                .contains(ACCOUNT_UUID.toString())
                .contains("KMS unreachable");
    }

    @Test // T20 Phase 7/8 finding #7's fix, narrowed during resolution: the request's
          // WebAuthenticationDetails carries forward onto the authenticated result, but the raw MFA
          // code never does — that result is what JdbcOAuth2AuthorizationService persists, and a
          // bearer secret has no business living in that table even transiently.
    void resultCarriesWebDetailsForwardButNeverTheRawMfaCode() {
        givenActiveAccountWithCorrectPassword();
        when(mfaService.hasConfirmedTotpEnrollment(ACCOUNT_UUID)).thenReturn(false);
        when(roleService.resolveEffectiveRoles(ACCOUNT_UUID)).thenReturn(Set.of());
        WebAuthenticationDetails webDetails = mock(WebAuthenticationDetails.class);
        UsernamePasswordAuthenticationToken incoming = request(EMAIL, RAW_PASSWORD, null);
        incoming.setDetails(new TotpAuthenticationDetailsSource.TotpAuthenticationDetails(webDetails, null));

        Authentication result = provider.authenticate(incoming);

        assertThat(result.getDetails()).isSameAs(webDetails);
    }

    @Test
    void supportsOnlyUsernamePasswordAuthenticationToken() {
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(Authentication.class)).isFalse();
    }

    private void givenActiveAccountWithCorrectPassword() {
        UserDetails userDetails = userDetails(ACCOUNT_UUID, ENCODED_PASSWORD, true, true, Set.of());
        when(accountUserDetailsService.loadUserByUsername(EMAIL)).thenReturn(userDetails);
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
    }

    private static UserDetails userDetails(
            UUID accountUuid, String encodedPassword, boolean enabled, boolean nonLocked,
            Set<? extends GrantedAuthority> authorities) {
        return User.withUsername(accountUuid.toString())
                .password(encodedPassword)
                .disabled(!enabled)
                .accountLocked(!nonLocked)
                .authorities(List.copyOf(authorities))
                .build();
    }

    private static UsernamePasswordAuthenticationToken request(String email, String password, String mfaCode) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(email, password);
        token.setDetails(new TotpAuthenticationDetailsSource.TotpAuthenticationDetails(null, mfaCode));
        return token;
    }
}
