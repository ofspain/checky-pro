package com.themistra.notification.inapp;

import com.themistra.notification.common.ResourceServerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice - real {@code ResourceServerConfig} filter chain (explicitly imported,
 * since {@code @WebMvcTest} does not scan arbitrary {@code @Configuration} classes on its own), a
 * mocked {@link InappStreamRegistry}, and {@code spring-security-test}'s own {@code jwt()} request
 * post-processor for an already-validated caller - no real Auth JWKS endpoint needed.
 */
@WebMvcTest(InappStreamController.class)
@Import(ResourceServerConfig.class)
class InappStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InappStreamRegistry streamRegistry;

    @Test
    void rejectsAnUnauthenticatedConnection() throws Exception {
        mockMvc.perform(get("/notifications/stream"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(streamRegistry);
    }

    /** R16 named test - proves the stream endpoint registers the *caller's own* account, scoped
     * exclusively from the validated JWT's own {@code sub} claim, never a client-supplied
     * identifier (there is none anywhere in this endpoint's own path/parameters to bypass). */
    @Test
    void shouldStreamInAppNotificationsToAuthenticatedRecipientOnly() throws Exception {
        UUID accountUuid = UUID.randomUUID();
        when(streamRegistry.register(accountUuid)).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/notifications/stream")
                        .with(jwt().jwt(builder -> builder.subject(accountUuid.toString()))))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(streamRegistry).register(accountUuid);
    }

    @Test
    void aMalformedSubjectClaimResultsInABadRequestNotAnInternalError() throws Exception {
        mockMvc.perform(get("/notifications/stream")
                        .with(jwt().jwt(builder -> builder.subject("not-a-uuid"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(streamRegistry);
    }

    /** Kimi Phase 11 Gap #10: a real, previously-uncaught bug - {@code UUID.fromString(null)}
     * throws {@code NullPointerException}, not {@code IllegalArgumentException}; unguarded, an
     * empty/missing {@code sub} fell through to the generic 500 handler instead of this endpoint's
     * own intended 400. Fixed by an explicit null/blank check in {@code accountUuidFrom} before
     * ever calling {@code UUID.fromString}. */
    @Test
    void anEmptySubjectClaimResultsInABadRequestNotAnInternalError() throws Exception {
        mockMvc.perform(get("/notifications/stream")
                        .with(jwt().jwt(builder -> builder.subject(""))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(streamRegistry);
    }

    /** Kimi Phase 11 Gap #1: locks that the broadened-to-{@code RuntimeException} fallback handler
     * (Phase 9's own fix for Kimi Phase 8 Finding #3) does not shadow Spring Boot's own default 404
     * handling for a genuinely unmapped path. */
    @Test
    void anUnmappedPathIsStillA404NotAGeneric500() throws Exception {
        mockMvc.perform(get("/notifications/this-path-does-not-exist")
                        .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());

        verifyNoInteractions(streamRegistry);
    }

    @Test
    void neverRegistersForAnyAccountOtherThanTheCallersOwn() throws Exception {
        UUID callerAccountUuid = UUID.randomUUID();
        UUID otherAccountUuid = UUID.randomUUID();
        when(streamRegistry.register(any())).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/notifications/stream")
                        .with(jwt().jwt(builder -> builder.subject(callerAccountUuid.toString()))))
                .andExpect(request().asyncStarted());

        verify(streamRegistry).register(callerAccountUuid);
        verify(streamRegistry, org.mockito.Mockito.never()).register(otherAccountUuid);
    }
}
