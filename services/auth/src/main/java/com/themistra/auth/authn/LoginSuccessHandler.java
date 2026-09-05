package com.themistra.auth.authn;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.util.UUID;

/**
 * SAS password-login success hook (R18). Extends {@link SavedRequestAwareAuthenticationSuccessHandler}
 * — the same class {@code .formLogin(Customizer.withDefaults())} uses internally by default — so
 * the redirect behavior is preserved exactly; only the {@link LockoutService} call is added.
 *
 * <p>The authenticated principal's name is already the account UUID
 * ({@code AccountUserDetailsService}'s established behavior), so no additional lookup is needed
 * here.</p>
 */
@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginSuccessHandler.class);

    private final LockoutService lockoutService;
    private final Clock clock;

    public LoginSuccessHandler(LockoutService lockoutService, Clock clock) {
        this.lockoutService = lockoutService;
        this.clock = clock;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        // Lockout-reset bookkeeping is best-effort: a failure here (including an unexpectedly
        // non-UUID principal name) must never prevent an already-authenticated user from
        // completing login.
        try {
            UUID accountUuid = UUID.fromString(authentication.getName());
            lockoutService.recordSuccessfulAttempt(accountUuid, clock.instant());
        } catch (RuntimeException e) {
            log.warn("Failed to record successful login for lockout tracking", e);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
