package com.themistra.crypto.common;

import com.themistra.crypto.watch.WatchController;
import com.themistra.crypto.watch.WatchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FU-2 port (open-issues sweep, 2026-10-20): {@code @EnableMethodSecurity} is on, the handlers'
 * {@code @PreAuthorize} rules are live, and a denial is a 403 problem, not the catch-all 500.
 *
 * <p>The {@code /internal/v1} URL rule asks for the same authority as the method rule, so over
 * HTTP the URL rule always refuses first. The real handler is therefore called directly on its
 * secured bean, and the 403 mapping is shown through a {@code denyAll()} probe endpoint.</p>
 */
@WebMvcTest(controllers = {WatchController.class, MethodSecurityProbeController.class})
@ActiveProfiles(MethodSecurityProbeController.PROFILE)
@Import({ResourceServerConfig.class, ApiExceptionHandler.class})
class MethodSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WatchController watchController;

    @MockitoBean
    private WatchService watchService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aPreAuthorizeDenialIsA403ProblemNotA500() throws Exception {
        mockMvc.perform(get("/method-security-probe").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void theRealHandlerRefusesACallerWithoutTheInternalScope() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("caller", "n/a", "SCOPE_internal.crypto:read"));

        assertThatThrownBy(() -> watchController.unregister(UUID.randomUUID()))
                .isInstanceOf(AuthorizationDeniedException.class);
        verifyNoInteractions(watchService);
    }

    @Test
    void theRealHandlerAcceptsTheInternalScope() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("caller", "n/a", ResourceServerConfig.INTERNAL_SCOPE_AUTHORITY));
        UUID watchId = UUID.randomUUID();

        watchController.unregister(watchId);

        verify(watchService).unregister(watchId);
    }
}
