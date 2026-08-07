package com.themistra.auth.authn;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * The authenticated result of {@link TotpAuthenticationProvider} (D-014, task 20): password
 * verification and, when required, the TOTP/recovery-code step are both already satisfied by the
 * time this exists. {@code otpUsed} is the fact {@code TokenClaimsCustomizer} reads to emit
 * {@code amr}/{@code acr} (R26/R27) — SAS stores and later replays this same {@code Authentication}
 * at token issuance, including on refresh, so no separate propagation mechanism is needed.
 */
public final class TotpStepUpAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;
    private final boolean otpUsed;

    private TotpStepUpAuthenticationToken(
            Object principal, Collection<? extends GrantedAuthority> authorities, boolean otpUsed) {
        super(authorities);
        this.principal = principal;
        this.otpUsed = otpUsed;
        setAuthenticated(true);
    }

    /**
     * @param principal   the {@code UserDetails} loaded for the account — not just the UUID
     *                    string, so {@link #getName()} resolves via {@code UserDetails.getUsername()}
     *                    exactly as it already does for today's password-only login.
     * @param authorities the account's granted authorities, carried through unchanged.
     * @param otpUsed     whether TOTP or a recovery code was verified as part of this login (R26/R27).
     */
    public static TotpStepUpAuthenticationToken authenticated(
            Object principal, Collection<? extends GrantedAuthority> authorities, boolean otpUsed) {
        return new TotpStepUpAuthenticationToken(principal, authorities, otpUsed);
    }

    @Override
    public Object getCredentials() {
        // Never carries the password or TOTP/recovery code past authentication.
        return null;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    public boolean otpUsed() {
        return otpUsed;
    }
}
