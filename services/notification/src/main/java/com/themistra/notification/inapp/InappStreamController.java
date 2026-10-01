package com.themistra.notification.inapp;

import com.themistra.notification.common.ApiExceptionHandler;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * SSE stream, recipient-scoped (R16, L8, `design.md` §6). Requires a valid JWT (rejected by
 * {@code ResourceServerConfig}'s own already-existing filter chain, T03, for an unauthenticated
 * connection - not reimplemented here) and streams only the notifications belonging to the token's
 * own {@code sub} claim. The endpoint accepts no client-supplied account identifier anywhere in its
 * own path or parameters (Kimi Phase 3 Finding #5/frozen brief AC10) - the caller's identity comes
 * exclusively from the validated token, eliminating the cross-account attack surface by
 * construction rather than by a runtime comparison alone.
 *
 * <p>Sends only notifications pushed <em>after</em> the connection is established (Kimi Phase 3
 * Finding #8) - no {@code Last-Event-ID} backfill; {@link InappReadController}'s own unread endpoint
 * is the sole backfill path (Phase 1's own resolved reading of {@code package.md}).</p>
 */
@RestController
public class InappStreamController {

    private final InappStreamRegistry streamRegistry;

    public InappStreamController(InappStreamRegistry streamRegistry) {
        this.streamRegistry = streamRegistry;
    }

    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        UUID accountUuid = accountUuidFrom(jwt);
        return streamRegistry.register(accountUuid);
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
