package com.themistra.notification.inapp;

import com.themistra.notification.common.ApiExceptionHandler;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Unread notifications, recipient-scoped (R17, L8, `design.md` §6). Same authentication/scoping
 * construction as {@link InappStreamController} - requires a valid JWT and returns only unread
 * ({@code read_at IS NULL}) notifications for the token's own {@code sub} claim, with no
 * client-supplied account identifier anywhere in its own path or parameters.
 */
@RestController
public class InappReadController {

    private final InappNotificationRepository repository;

    public InappReadController(InappNotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/notifications/unread")
    public List<InappNotification.View> unread(@AuthenticationPrincipal Jwt jwt) {
        UUID accountUuid = accountUuidFrom(jwt);
        return repository.findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(accountUuid).stream()
                .map(InappNotification::toView)
                .toList();
    }

    private static UUID accountUuidFrom(Jwt jwt) {
        // Kimi Phase 11 Gap #10: a null/blank sub claim previously reached UUID.fromString(null),
        // which throws NullPointerException, not IllegalArgumentException - uncaught here, it fell
        // through to ApiExceptionHandler's own generic 500 handler instead of this endpoint's own
        // intended 400. Checked explicitly first, so both a missing and a malformed claim map to
        // the exact same, correct response.
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new ApiExceptionHandler.InvalidSubjectClaimException("sub claim is missing");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new ApiExceptionHandler.InvalidSubjectClaimException(
                    "sub claim is not a valid UUID: " + subject);
        }
    }
}
