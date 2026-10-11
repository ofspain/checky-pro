package com.themistra.payment.common;

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
 * AC3, AC4 (401 half). Exercised as a {@code @WebMvcTest} slice against
 * {@link ResourceServerTestController} with {@link ResourceServerConfig} imported, mirroring
 * {@code notification-service}'s/{@code crypto-service}'s own identical test shape. No real JWT
 * decoding occurs - {@code SecurityMockMvcRequestPostProcessors.jwt()} injects a pre-built
 * authentication directly, which is exactly right for proving the
 * {@code .anyRequest().authenticated()} rule and the RFC 9457 handler wiring in isolation. Real
 * signature/issuer validation against a live JWKS remains a documented limitation, same as both
 * siblings' own equivalent test. The 403 half of AC4 is NOT testable here (Phase 3 finding 1 - no
 * authority-restricted path exists in this task's own scope) - see
 * {@link AccessDeniedHandlerTest} for that half, proven by direct bean invocation instead.
 */
@WebMvcTest(controllers = ResourceServerTestController.class)
@Import(ResourceServerConfig.class)
class ResourceServerConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicActuatorHealthPathIsReachableWithoutAToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("public actuator health path must never be blocked, got " + status);
                    }
                });
    }

    @Test
    void unauthenticatedRequestToNonPublicPathIsRejected() throws Exception {
        mockMvc.perform(get("/v1/invoices/test-only"))
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
        mockMvc.perform(get("/v1/invoices/test-only").with(jwt()))
                .andExpect(status().is2xxSuccessful());
    }
}
