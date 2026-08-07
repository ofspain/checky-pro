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
        return new TotpAuthenticationDetails(new WebAuthenticationDetails(request), request.getParameter("mfaCode"));
    }

    /**
     * @param webDetails the usual remote-address/session-id pair {@link WebAuthenticationDetails}
     *                    always carries — preserved so nothing downstream loses it.
     * @param mfaCode    the raw {@code mfaCode} form field; {@code null} when the field was absent
     *                    (a password-only submission).
     */
    public record TotpAuthenticationDetails(WebAuthenticationDetails webDetails, String mfaCode) {
    }
}
