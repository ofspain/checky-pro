package com.themistra.auth.authn;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    private final LockoutService lockoutService;
    private final Clock clock;

    public LoginSuccessHandler(LockoutService lockoutService, Clock clock) {
        this.lockoutService = lockoutService;
        this.clock = clock;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        UUID accountUuid = UUID.fromString(authentication.getName());
        lockoutService.recordSuccessfulAttempt(accountUuid, clock.instant());
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
