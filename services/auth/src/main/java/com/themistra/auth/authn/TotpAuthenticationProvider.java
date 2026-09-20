package com.themistra.auth.authn;

import com.themistra.auth.authz.RoleService;
import com.themistra.auth.mfa.InvalidRecoveryCodeException;
import com.themistra.auth.mfa.InvalidTotpCodeException;
import com.themistra.auth.mfa.MfaNotEnrolledException;
import com.themistra.auth.mfa.MfaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The SAS MFA step (D-014, task 20): replaces the default {@code DaoAuthenticationProvider} for
 * the login form, verifying password and, when the account has a confirmed TOTP enrollment, a
 * TOTP/recovery code — in one pass, no second page, no partially-authenticated session state (O4,
 * single-request design; see {@code SecurityChainsConfig}).
 *
 * <p>Whether MFA runs is gated on {@link MfaService#hasConfirmedTotpEnrollment} alone (R25) —
 * MERCHANT/ADMIN without a confirmed enrollment is a separate, role-gated block (R24, L10), not a
 * precondition for R25's check. A voluntarily-enrolled {@code USER}/{@code COMPLIANCE} account
 * still must pass MFA (Phase 8 independent-review finding #2: an earlier version of this class
 * incorrectly gated the whole MFA step on role, silently skipping R25 for that case).</p>
 *
 * <p>The MFA outcome is carried forward as a synthetic {@link #OTP_VERIFIED_AUTHORITY} granted
 * authority on a plain {@link UsernamePasswordAuthenticationToken}, not a custom
 * {@code Authentication} subclass (Phase 8 finding #1): {@code JdbcOAuth2AuthorizationService}
 * persists the SAS authorization's principal via Jackson, and a hand-rolled {@code Authentication}
 * type with no registered Jackson mixin is not reliably deserializable by it — a synthetic
 * authority on a standard token type rides on Spring Security's own, already-covered Jackson
 * support for {@link SimpleGrantedAuthority} instead.</p>
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

    /**
     * Marks that TOTP or a recovery code was verified as part of this login (R26/R27) — read by
     * {@code TokenClaimsCustomizer} off the authenticated principal's authorities. Never a real
     * authorization grant; only ever consulted by that one claims-customization check.
     */
    public static final GrantedAuthority OTP_VERIFIED_AUTHORITY = new SimpleGrantedAuthority("OTP_VERIFIED");

    private static final Logger log = LoggerFactory.getLogger(TotpAuthenticationProvider.class);
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
        if (mfaService.hasConfirmedTotpEnrollment(accountUuid)) {
            // R25: a confirmed enrollment requires the MFA step regardless of role.
            verifyMfaCodeOrFail(accountUuid, extractMfaCode(request));
            otpUsed = true;
        } else if (isMfaRequired(accountUuid)) {
            // R24: MERCHANT/ADMIN with no confirmed enrollment is blocked outright.
            throw new BadCredentialsException("Bad credentials");
        }

        UsernamePasswordAuthenticationToken result = new UsernamePasswordAuthenticationToken(
                userDetails, null, authoritiesFor(userDetails, otpUsed));
        result.setDetails(webDetailsOnly(request.getDetails()));
        return result;
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
        } catch (InvalidTotpCodeException | InvalidRecoveryCodeException | MfaNotEnrolledException e) {
            // Expected verification failures — uniform response, nothing operationally unusual.
            throw new BadCredentialsException("Bad credentials", e);
        } catch (RuntimeException e) {
            // Anything else (e.g. MfaEncryptionException from a KMS outage, a transient DB
            // failure) must stay visible to operators even though the client-facing response
            // stays uniform — otherwise an infrastructure failure is indistinguishable from a
            // credential-stuffing spike in the mfa.failed audit trail (Phase 7/8 finding).
            log.warn("Unexpected failure verifying MFA code for account {}", accountUuid, e);
            throw new BadCredentialsException("Bad credentials", e);
        }
    }

    private String extractMfaCode(UsernamePasswordAuthenticationToken request) {
        return request.getDetails() instanceof TotpAuthenticationDetailsSource.TotpAuthenticationDetails details
                ? details.mfaCode()
                : null;
    }

    private Collection<GrantedAuthority> authoritiesFor(UserDetails userDetails, boolean otpUsed) {
        List<GrantedAuthority> authorities = new ArrayList<>(userDetails.getAuthorities());
        if (otpUsed) {
            authorities.add(OTP_VERIFIED_AUTHORITY);
        }
        return authorities;
    }

    /**
     * Carries the request's {@code WebAuthenticationDetails} (remote address, session id) forward
     * onto the authenticated result, but never the raw MFA code {@code TotpAuthenticationDetails}
     * also holds — this result is what {@code JdbcOAuth2AuthorizationService} persists, and a
     * bearer secret has no business living in that table even transiently.
     */
    private Object webDetailsOnly(Object incomingDetails) {
        return incomingDetails instanceof TotpAuthenticationDetailsSource.TotpAuthenticationDetails details
                ? details.webDetails()
                : incomingDetails;
    }
}
