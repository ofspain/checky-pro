package com.themistra.payment.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC4 (403 half, Phase 3 finding 1): no path in this task's own scope triggers the 403 handler
 * (no authority rule exists until a later task introduces a scoped internal endpoint), so this
 * invokes it directly - immediate regression coverage rather than a TODO, mirroring
 * {@code notification-service}'s own identical, already-proven resolution to the same gap.
 */
class AccessDeniedHandlerTest {

    @Test
    void accessDeniedHandlerWritesRfc9457ProblemJson() throws Exception {
        AccessDeniedHandler handler = new ResourceServerConfig().problemJsonAccessDeniedHandler(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        String body = response.getContentAsString();
        assertThat(body).as("Phase 8 finding 2: the 401 test already asserts type - this one should too")
                .contains("\"type\":\"about:blank\"");
        assertThat(body).contains("\"title\":\"Forbidden\"");
        assertThat(body).contains("\"status\":403");
        assertThat(body).contains("\"detail\":\"The token does not carry the required access.\"");
    }
}
