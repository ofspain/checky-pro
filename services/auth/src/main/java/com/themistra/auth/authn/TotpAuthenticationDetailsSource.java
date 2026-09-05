package com.themistra.auth.authn;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * Replaces the login form filter's default authentication-details capture so the optional
 * {@code mfaCode} field submitted alongside {@code username}/{@code password} (task 20's
 * single-request MFA design, O4) reaches {@link TotpAuthenticationProvider} — an
 * {@code AuthenticationProvider} only ever sees the {@code Authentication} object, never the raw
 * request, so this is the one place that request parameter can be captured.
 */
@Component
public class TotpAuthenticationDetailsSource implements
        AuthenticationDetailsSource<HttpServletRequest, TotpAuthenticationDetailsSource.TotpAuthenticationDetails> {

    @Override
    public TotpAuthenticationDetails buildDetails(HttpServletRequest request) {
        String mfaCode = request.getParameter("mfaCode");
        // Strips incidental copy-paste whitespace before it ever reaches the shape-based
        // TOTP-vs-recovery-code dispatch in TotpAuthenticationProvider — untrimmed input there
        // could misclassify an otherwise-valid code and fail it for the wrong reason (Phase 8
        // independent-review finding #5).
        return new TotpAuthenticationDetails(new WebAuthenticationDetails(request), mfaCode == null ? null : mfaCode.strip());
    }

    /**
     * @param webDetails the usual remote-address/session-id pair {@link WebAuthenticationDetails}
     *                    always carries — preserved so nothing downstream loses it.
     * @param mfaCode    the raw {@code mfaCode} form field; {@code null} when the field was absent
     *                    (a password-only submission).
     */
    public record TotpAuthenticationDetails(WebAuthenticationDetails webDetails, String mfaCode) {

        /** Overridden so the raw code can never leak via a default record {@code toString()} —
         * {@link org.springframework.security.authentication.AbstractAuthenticationToken}'s own
         * {@code toString()} includes {@code details} (Phase 8 independent-review finding #5). */
        @Override
        public String toString() {
            return "TotpAuthenticationDetails[webDetails=" + webDetails + ", mfaCode=REDACTED]";
        }
    }
}
