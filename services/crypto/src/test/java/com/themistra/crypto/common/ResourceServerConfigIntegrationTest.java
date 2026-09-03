package com.themistra.crypto.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R27 — named test {@code shouldRequireInternalScopeForWatchAndAttestEndpoints} (package.md §8),
 * plus the boundary tests the frozen brief's Required Tests section calls for. Exercised as a
 * {@code @WebMvcTest} slice against {@link InternalTestController} (mirrors the real internal API
 * shape) with {@link ResourceServerConfig} imported — no real JWT decoding occurs:
 * {@code SecurityMockMvcRequestPostProcessors.jwt()} injects a pre-built authentication directly,
 * which is exactly right for proving the {@code authorizeHttpRequests}/{@code hasAuthority} rules
 * in isolation. It does not exercise real signature/issuer validation or the default
 * scope-claim-to-authority conversion from an actual token — those are Spring
 * Security/Boot-owned behaviors, reasoned through in the implementation notes rather than
 * re-proven here.
 */
@WebMvcTest(controllers = InternalTestController.class)
@Import(ResourceServerConfig.class)
class ResourceServerConfigIntegrationTest {

    private static final String INTERNAL_SCOPE = "SCOPE_internal.crypto:write";
    private static final String WATCH_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    private static Stream<InternalRequest> internalRequests() {
        return Stream.of(
                new InternalRequest(HttpMethod.POST, "/internal/v1/watches"),
                new InternalRequest(HttpMethod.DELETE, "/internal/v1/watches/" + WATCH_ID),
                new InternalRequest(HttpMethod.POST, "/internal/v1/attest")
        );
    }

    @ParameterizedTest
    @MethodSource("internalRequests")
    void shouldRequireInternalScopeForWatchAndAttestEndpoints_rejectsUnauthenticated(InternalRequest req) throws Exception {
        mockMvc.perform(request(req.method(), req.path()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().string(containsString("\"status\":401")));
    }

    @ParameterizedTest
    @MethodSource("internalRequests")
    void shouldRequireInternalScopeForWatchAndAttestEndpoints_rejectsUnderScopedToken(InternalRequest req) throws Exception {
        mockMvc.perform(request(req.method(), req.path())
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_something.else"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().string(containsString("\"status\":403")));
    }

    @ParameterizedTest
    @MethodSource("internalRequests")
    void shouldRequireInternalScopeForWatchAndAttestEndpoints_acceptsCorrectScope(InternalRequest req) throws Exception {
        mockMvc.perform(request(req.method(), req.path())
                        .with(jwt().authorities(new SimpleGrantedAuthority(INTERNAL_SCOPE))))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void shouldRequireInternalScopeForWatchAndAttestEndpoints_missingTokenEntirely() throws Exception {
        // No .with(jwt(...)) at all - a bare unauthenticated request, distinct from "authenticated
        // but under-scoped" above. Confirms both absence-of-token and wrong-scope are rejected.
        mockMvc.perform(request(HttpMethod.POST, "/internal/v1/attest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowInternalScopeAlongsideExtraScopes() throws Exception {
        // R27 is at-least, not exact-match (frozen brief amendment #12).
        mockMvc.perform(request(HttpMethod.POST, "/internal/v1/attest")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority(INTERNAL_SCOPE),
                                new SimpleGrantedAuthority("SCOPE_something.else"))))
                .andExpect(status().isOk());
    }

    private record InternalRequest(HttpMethod method, String path) {
    }
}
