package com.themistra.notification.inapp;

import com.themistra.notification.common.ApiExceptionHandler;
import com.themistra.notification.common.ResourceServerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice - real {@code ResourceServerConfig} filter chain, a mocked
 * {@link InappNotificationRepository}, and {@code spring-security-test}'s own {@code jwt()} request
 * post-processor.
 */
@WebMvcTest(InappReadController.class)
@Import(ResourceServerConfig.class)
class InappReadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InappNotificationRepository repository;

    @Test
    void rejectsAnUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/notifications/unread"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(repository);
    }

    /** R17 named test - proves the unread endpoint queries only the *caller's own* account, scoped
     * exclusively from the validated JWT's own {@code sub} claim. */
    @Test
    void shouldReturnUnreadInAppNotificationsForCaller() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        UUID notificationUuid = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-03-01T00:00:00Z");
        InappNotification notification = new InappNotification(
                notificationUuid, accountUuid, "SECURITY", "Welcome", "Welcome aboard!", null, createdAt);
        when(repository.findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(accountUuid))
                .thenReturn(List.of(notification));

        mockMvc.perform(get("/notifications/unread")
                        .with(jwt().jwt(builder -> builder.subject(accountUuid.toString()))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].notificationUuid").value(notificationUuid.toString()))
                .andExpect(jsonPath("$[0].category").value("SECURITY"))
                .andExpect(jsonPath("$[0].title").value("Welcome"))
                .andExpect(jsonPath("$[0].body").value("Welcome aboard!"));

        verify(repository).findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(accountUuid);
    }

    @Test
    void neverQueriesForAnyAccountOtherThanTheCallersOwn() throws Exception {
        UUID callerAccountUuid = UUID.randomUUID();
        UUID otherAccountUuid = UUID.randomUUID();
        when(repository.findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(any())).thenReturn(List.of());

        mockMvc.perform(get("/notifications/unread")
                        .with(jwt().jwt(builder -> builder.subject(callerAccountUuid.toString()))))
                .andExpect(status().isOk());

        verify(repository).findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(callerAccountUuid);
        verify(repository, org.mockito.Mockito.never())
                .findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(otherAccountUuid);
    }

    @Test
    void aMalformedSubjectClaimResultsInABadRequestNotAnInternalError() throws Exception {
        mockMvc.perform(get("/notifications/unread")
                        .with(jwt().jwt(builder -> builder.subject("not-a-uuid"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(repository);
    }

    /** Kimi Phase 11 Gap #10: a real, previously-uncaught bug - {@code UUID.fromString(null)}
     * throws {@code NullPointerException}, not {@code IllegalArgumentException}; unguarded, an
     * empty/missing {@code sub} fell through to the generic 500 handler instead of this endpoint's
     * own intended 400. Fixed by an explicit null/blank check in {@code accountUuidFrom} before
     * ever calling {@code UUID.fromString}. */
    @Test
    void anEmptySubjectClaimResultsInABadRequestNotAnInternalError() throws Exception {
        mockMvc.perform(get("/notifications/unread")
                        .with(jwt().jwt(builder -> builder.subject(""))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(repository);
    }

    /** Kimi Phase 11 Gap #1: locks that the broadened-to-{@code RuntimeException} fallback handler
     * (Phase 9's own fix for Kimi Phase 8 Finding #3) does not shadow Spring Boot's own default 404
     * handling for a genuinely unmapped path - verified directly by running this exact test, not
     * merely by the structural {@code javap} check Phase 9 already performed. */
    @Test
    void anUnmappedPathIsStillA404NotAGeneric500() throws Exception {
        mockMvc.perform(get("/notifications/this-path-does-not-exist")
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());

        verifyNoInteractions(repository);
    }

    /** Kimi Phase 11 Gap #3: the read endpoint's own declared ordering (newest-first) is only
     * meaningful with more than one row - locks it explicitly rather than relying on the
     * single-notification happy-path test above to imply it. */
    @Test
    void returnsNotificationsOrderedNewestFirst() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        InappNotification older = new InappNotification(UUID.randomUUID(), accountUuid, "SECURITY",
                "Older", "older body", null, Instant.parse("2026-01-01T00:00:00Z"));
        InappNotification newer = new InappNotification(UUID.randomUUID(), accountUuid, "SECURITY",
                "Newer", "newer body", null, Instant.parse("2026-02-01T00:00:00Z"));
        // The repository method's own name (...OrderByCreatedAtDesc) owns the real ordering
        // guarantee (proven against a real DB at InAppChannelIntegrationTest's own Gap #4 test);
        // this test locks that the controller preserves whatever order it receives, unchanged.
        when(repository.findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(accountUuid))
                .thenReturn(List.of(newer, older));

        mockMvc.perform(get("/notifications/unread")
                        .with(jwt().jwt(builder -> builder.subject(accountUuid.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Newer"))
                .andExpect(jsonPath("$[1].title").value("Older"));
    }

    /** Kimi Phase 8 Finding #2's own suggested {@code ApiExceptionHandlerTest} scenario ("unexpected
     * exception -> 500 problem detail") - folded in here rather than a separate file, since a
     * {@code @RestControllerAdvice} needs some real controller/dispatch to exercise it through. */
    @Test
    void anUnexpectedRepositoryFailureResultsInAGenericInternalServerErrorNotARawStackTrace() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        when(repository.findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(accountUuid))
                .thenThrow(new RuntimeException("db is down, secret=abc123"));

        mockMvc.perform(get("/notifications/unread")
                        .with(jwt().jwt(builder -> builder.subject(accountUuid.toString()))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("abc123"))));
    }

    /** Confirms {@link ApiExceptionHandler.InvalidSubjectClaimException} is a real, resolvable type
     * reachable from this package (a static compile-time guard, not a runtime behavior proof - the
     * runtime proof is {@code aMalformedSubjectClaimResultsInABadRequestNotAnInternalError} above). */
    @Test
    void invalidSubjectClaimExceptionIsPubliclyConstructibleFromThisPackage() {
        var exception = new ApiExceptionHandler.InvalidSubjectClaimException("test");
        org.assertj.core.api.Assertions.assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
