package com.themistra.auth.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.auth.account.dto.PasswordResetConfirmRequest;
import com.themistra.auth.common.Hashing;
import com.themistra.auth.common.ProblemTypes;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Enforces R41's per-account request-rate backstop on three paths: {@code /login} (which also
 * covers MFA verification — it happens inside the same request via
 * {@code TotpAuthenticationProvider}, Phase 4 D1), {@code POST /accounts/password-reset}, and the
 * {@code /oauth2/token} refresh_token grant. Registered on both {@code SecurityFilterChain}s
 * before any credential validation (Phase 4 D4 — this is what gives the limiter real DoS-backstop
 * value; checking after validation would let an attacker force unlimited password-hashing work
 * before ever being throttled).
 *
 * <p>Keying is deliberately never account-lookup-based (Phase 4 D2): the login key is the raw,
 * normalized submitted username with no database access at all (true pre-check enumeration
 * safety — a fabricated email is bucketed identically to a real one). The password-reset and
 * {@code /oauth2/token} keys are the SHA-256 hash of the submitted token (D3's token-hash-keying
 * pattern, applied consistently to both token-possession-based paths) — this is a per-session,
 * not strictly per-account, granularity for {@code /oauth2/token}, an accepted narrowing.</p>
 *
 * <p>Fail-open (Phase 4 D5): if the limiter or key-derivation logic itself throws, the request is
 * allowed through and the error is logged — mirroring this codebase's existing R13/HIBP
 * breach-check precedent that a security-adjacent dependency failing must not become an outage of
 * the primary functionality it backstops.</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest effectiveRequest = request;
        ConsumptionProbe probe = null;
        try {
            if (isLoginRequest(request)) {
                probe = rateLimiter.tryConsumeLogin(normalizedUsername(request));
            } else if (isPasswordResetRequest(request)) {
                CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
                effectiveRequest = cached;
                String tokenHash = passwordResetTokenHash(cached);
                if (tokenHash != null) {
                    probe = rateLimiter.tryConsumePasswordReset(tokenHash);
                }
            } else if (isOAuthTokenRefreshRequest(request)) {
                String tokenHash = refreshTokenHash(request);
                if (tokenHash != null) {
                    probe = rateLimiter.tryConsumeOauthToken(tokenHash);
                }
            }
        } catch (Exception e) {
            // D5: a bug in this backstop must never become an outage of what it backstops.
            log.error("Rate limiter check failed; failing open", e);
            probe = null;
        }

        if (probe != null && !probe.isConsumed()) {
            writeTooManyRequests(response, probe);
            return;
        }
        filterChain.doFilter(effectiveRequest, response);
    }

    private static boolean isLoginRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && "/login".equals(request.getRequestURI());
    }

    private static boolean isPasswordResetRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && "/accounts/password-reset".equals(request.getRequestURI());
    }

    private static boolean isOAuthTokenRefreshRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && "/oauth2/token".equals(request.getRequestURI())
                && "refresh_token".equals(request.getParameter("grant_type"));
    }

    /** Coupled to {@code .formLogin(Customizer.withDefaults())}'s default username parameter
     * name, same coupling {@code LoginFailureHandler} already documents. No database access
     * (D2) — a missing/blank submission still gets a (shared) bucket key, so it can't bypass
     * the limiter by omitting the field. */
    private static String normalizedUsername(HttpServletRequest request) {
        String username = request.getParameter("username");
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private String passwordResetTokenHash(CachedBodyHttpServletRequest request) throws IOException {
        PasswordResetConfirmRequest body =
                objectMapper.readValue(request.getInputStream(), PasswordResetConfirmRequest.class);
        return body.token() == null ? null : Hashing.sha256(body.token());
    }

    private static String refreshTokenHash(HttpServletRequest request) {
        String refreshToken = request.getParameter("refresh_token");
        return refreshToken == null ? null : Hashing.sha256(refreshToken);
    }

    private void writeTooManyRequests(HttpServletResponse response, ConsumptionProbe probe) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        problem.setType(ProblemTypes.RATE_LIMIT_EXCEEDED);
        problem.setTitle("Too many requests");

        long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
