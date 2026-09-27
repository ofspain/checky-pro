package com.themistra.notification.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC3 (Kimi Phase 8 Finding #3) - exercised as a {@code @WebMvcTest} slice against
 * {@link ResourceServerTestController} with {@link ResourceServerConfig} imported, mirroring
 * {@code services/crypto}'s own {@code ResourceServerConfigIntegrationTest}. No real JWT decoding
 * occurs - {@code SecurityMockMvcRequestPostProcessors.jwt()} injects a pre-built authentication
 * directly, which is exactly right for proving the {@code .anyRequest().authenticated()} rule and
 * the RFC 9457 handler wiring in isolation. Real signature/issuer validation against a live JWKS
 * remains a documented limitation, same as crypto's own equivalent test.
 */
@WebMvcTest(controllers = ResourceServerTestController.class)
@Import(ResourceServerConfig.class)
class ResourceServerConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestToNonPublicPathIsRejected() throws Exception {
        mockMvc.perform(get("/v1/notifications/unread"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("A valid access token is required."));
    }

    @Test
    void authenticatedJwtRequestToNonPublicPathIsAccepted() throws Exception {
        mockMvc.perform(get("/v1/notifications/unread").with(jwt()))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void nonBearerAuthorizationSchemeIsRejected() throws Exception {
        // A Basic-scheme header isn't a bearer token at all - BearerTokenResolver never attempts to
        // extract/decode it, so the request falls through as anonymous and is still rejected.
        mockMvc.perform(get("/v1/notifications/unread")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNzd29yZA=="))
                .andExpect(status().isUnauthorized());
    }
}
