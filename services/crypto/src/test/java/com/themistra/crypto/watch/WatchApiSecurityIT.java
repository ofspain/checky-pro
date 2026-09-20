package com.themistra.crypto.watch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who reaches the watch API.
 *
 * <p>The distinction that matters here is between 401 and 403: collapsing them would hide the most
 * common real misconfiguration — a client whose scope was never provisioned on the issuer — behind
 * the symptom of a bad token.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class WatchApiSecurityIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String SCOPE = "internal.chain:write";

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // No issuer: the decoder is not built, and jwt() post-processors supply the authentication
        // directly. Token validation itself is Spring Security's, not ours to re-test.
        registry.add("themistra.crypto.security.require-issuer", () -> false);
        registry.add("themistra.crypto.chains[0].id", () -> "eip155:1");
        registry.add("themistra.crypto.chains[0].providers[0].label", () -> "evm-provider-a");
        registry.add("themistra.crypto.chains[0].providers[0].endpoint", () -> "http://localhost:1/unused");
        registry.add("themistra.crypto.chains[0].providers[1].label", () -> "evm-provider-b");
        registry.add("themistra.crypto.chains[0].providers[1].endpoint", () -> "http://localhost:2/unused");
        registry.add("themistra.crypto.chains[0].providers[2].label", () -> "evm-provider-c");
        registry.add("themistra.crypto.chains[0].providers[2].endpoint", () -> "http://localhost:3/unused");
    }

    @Test
    @DisplayName("registering without credentials is unauthorised")
    void unauthenticatedRegistrationRejected() throws Exception {
        mockMvc.perform(post("/internal/watches")
                        .contentType("application/json")
                        .content(registrationBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a valid token without the scope is forbidden, not unauthorised")
    void wrongScopeIsForbidden() throws Exception {
        mockMvc.perform(post("/internal/watches")
                        .with(jwt().authorities(new org.springframework.security.core.authority
                                .SimpleGrantedAuthority("SCOPE_internal.accounts:read")))
                        .contentType("application/json")
                        .content(registrationBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a token carrying the scope is allowed through to the handler")
    void correctScopeIsAllowed() throws Exception {
        mockMvc.perform(post("/internal/watches")
                        .with(jwt().authorities(new org.springframework.security.core.authority
                                .SimpleGrantedAuthority("SCOPE_" + SCOPE)))
                        .contentType("application/json")
                        .content(registrationBody()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an unauthenticated probe for a watch cannot tell whether it exists")
    void unauthenticatedLookupRevealsNothing() throws Exception {
        // Same status for a random identifier as for any real one: the endpoint cannot be used to
        // discover live invoices.
        mockMvc.perform(get("/internal/watches/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("liveness and readiness stay open so orchestration works")
    void probesAreOpen() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("other management endpoints require credentials")
    void otherManagementEndpointsClosed() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    private static String registrationBody() {
        return """
                {
                  "callerReference": "invoice-%s",
                  "chainId": "eip155:1",
                  "recipientAddress": "0x9fd4AaA15C9B74F4c4B248566E01A729e3aCe193",
                  "tokenAddress": "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                  "expectedAmount": 1500000,
                  "expiresAt": "%s"
                }
                """.formatted(UUID.randomUUID(), java.time.Instant.now().plusSeconds(3600));
    }
}
