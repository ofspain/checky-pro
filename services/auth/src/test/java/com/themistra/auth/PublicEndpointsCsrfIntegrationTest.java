package com.themistra.auth;

import com.themistra.auth.common.PublicEndpoints;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Frontend start (2026-10-21): the SPA calls auth's anonymous JSON endpoints with no session and
 * no CSRF token. Each must reach its controller, not be refused by the CSRF filter (which then
 * surfaced as a 401 from the {@code /error} forward). The session-backed {@code /login} form
 * keeps its CSRF protection. A plain JDK client, so no cookie or token is ever carried.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PublicEndpointsCsrfIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> postJson(String path, String json) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void registrationWithoutACsrfTokenIsAccepted() throws Exception {
        HttpResponse<String> response = postJson("/accounts",
                "{\"email\":\"csrf-" + UUID.randomUUID() + "@example.com\",\"password\":\"" + PASSWORD + "\"}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(202);
    }

    /** Every anonymous JSON endpoint reaches its controller: a well-formed body gets the endpoint's
     * own answer, never the 401/403 of a CSRF refusal. */
    @Test
    void everyAnonymousJsonEndpointIsReachableWithoutACsrfToken() throws Exception {
        Map<String, String> bodies = Map.of(
                "/accounts/verify-email", "{\"token\":\"not-a-real-token\"}",
                "/accounts/resend-verification", "{\"email\":\"nobody@example.com\"}",
                "/accounts/password-reset-request", "{\"email\":\"nobody@example.com\"}",
                "/accounts/password-reset", "{\"token\":\"not-a-real-token\",\"newPassword\":\"" + PASSWORD + "\"}");
        // /api-keys/token is left out: it answers a bad key with its own uniform 401 (D5), and was
        // already CSRF-exempt before 2026-10-21 (ApiKeyLifecycleIntegrationTest covers it).
        assertThat(bodies.keySet()).as("covers every POST in PublicEndpoints except /accounts and /api-keys/token")
                .containsExactlyInAnyOrderElementsOf(PublicEndpoints.METHOD_SCOPED.stream()
                        .map(PublicEndpoints.MethodScoped::pattern)
                        .filter(path -> !path.equals("/accounts") && !path.equals("/api-keys/token")).toList());

        for (Map.Entry<String, String> entry : bodies.entrySet()) {
            HttpResponse<String> response = postJson(entry.getKey(), entry.getValue());
            assertThat(response.statusCode()).as("%s answered %s", entry.getKey(), response.body())
                    .isNotIn(401, 403);
        }
    }

    @Test
    void theLoginFormStillRequiresACsrfToken() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("username=a%40example.com&password=x")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).as("refused before any credential check").isIn(401, 403);
    }
}
