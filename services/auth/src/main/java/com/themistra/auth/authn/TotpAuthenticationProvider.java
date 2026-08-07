package com.themistra.auth.authn;

import com.themistra.auth.authz.RoleService;
import com.themistra.auth.mfa.MfaService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The SAS MFA step (D-014, task 20): replaces the default {@code DaoAuthenticationProvider} for
 * the login form, verifying password and, when required, TOTP/recovery-code in one pass — no
 * second page, no partially-authenticated session state (O4, single-request design; see
 * {@code SecurityChainsConfig}).
 *
 * <p>Every failure mode below throws the identical {@link BadCredentialsException} with the
 * identical message: wrong password, wrong/missing MFA code, and "MERCHANT/ADMIN without
 * confirmed enrollment" are deliberately indistinguishable to the caller (R24/R25/R29, L5 extended
 * per {@code agents.md}'s enumeration-safe posture). A distinct "please enroll" signal was
 * considered and rejected: reachable only after a correct password, it would work as a
 * password-correctness oracle for an attacker probing a known MERCHANT/ADMIN email — recorded as
 * an accepted rollout gap, not fixed by weakening this uniformity.</p>
 */
@Component
public class TotpAuthenticationProvider implements AuthenticationProvider {

    private static final Pattern TOTP_CODE_SHAPE = Pattern.compile("\\d{6}");
    private static final Set<String> MFA_MANDATORY_ROLES = Set.of("MERCHANT", "ADMIN");

    private final AccountUserDetailsService accountUserDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;
    private final MfaService mfaService;

    public TotpAuthenticationProvider(
            AccountUserDetailsService accountUserDetailsService, PasswordEncoder passwordEncoder,
            RoleService roleService, MfaService mfaService) {
        this.accountUserDetailsService = accountUserDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.roleService = roleService;
        this.mfaService = mfaService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        UsernamePasswordAuthenticationToken request = (UsernamePasswordAuthenticationToken) authentication;

        UserDetails userDetails = loadUserDetailsOrFail(request.getName());
        verifyAccountUsableOrFail(userDetails);
        verifyPasswordOrFail(request.getCredentials(), userDetails);

        UUID accountUuid = UUID.fromString(userDetails.getUsername());
        boolean otpUsed = false;
        if (isMfaRequired(accountUuid)) {
            requireConfirmedEnrollmentOrFail(accountUuid);
            verifyMfaCodeOrFail(accountUuid, extractMfaCode(request));
            otpUsed = true;
        }

        return TotpStepUpAuthenticationToken.authenticated(userDetails, userDetails.getAuthorities(), otpUsed);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private UserDetails loadUserDetailsOrFail(String email) {
        try {
            return accountUserDetailsService.loadUserByUsername(email);
        } catch (UsernameNotFoundException e) {
            throw new BadCredentialsException("Bad credentials");
        }
    }

    private void verifyAccountUsableOrFail(UserDetails userDetails) {
        if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
            throw new BadCredentialsException("Bad credentials");
        }
    }

    private void verifyPasswordOrFail(Object credentials, UserDetails userDetails) {
        String rawPassword = credentials == null ? null : credentials.toString();
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, userDetails.getPassword())) {
            throw new BadCredentialsException("Bad credentials");
        }
    }

    private boolean isMfaRequired(UUID accountUuid) {
        Set<String> roles = roleService.resolveEffectiveRoles(accountUuid);
        return roles.stream().anyMatch(MFA_MANDATORY_ROLES::contains);
    }

    private void requireConfirmedEnrollmentOrFail(UUID accountUuid) {
        if (!mfaService.hasConfirmedTotpEnrollment(accountUuid)) {
            throw new BadCredentialsException("Bad credentials");
        }
    }

    private void verifyMfaCodeOrFail(UUID accountUuid, String mfaCode) {
        if (mfaCode == null || mfaCode.isBlank()) {
            throw new BadCredentialsException("Bad credentials");
        }
        try {
            if (TOTP_CODE_SHAPE.matcher(mfaCode).matches()) {
                mfaService.verifyTotpCodeForLogin(accountUuid, mfaCode);
            } else {
                mfaService.verifyRecoveryCode(accountUuid, mfaCode);
            }
        } catch (RuntimeException e) {
            // Deliberately uniform: InvalidTotpCodeException, InvalidRecoveryCodeException, and
            // the defensive MfaNotEnrolledException all collapse to the same failure the caller sees.
            throw new BadCredentialsException("Bad credentials", e);
        }
    }

    private String extractMfaCode(UsernamePasswordAuthenticationToken request) {
        return request.getDetails() instanceof TotpAuthenticationDetailsSource.TotpAuthenticationDetails details
                ? details.mfaCode()
                : null;
    }
}
