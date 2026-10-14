package com.themistra.payment.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.themistra.payment.common.config.CryptoClientProperties;
import com.themistra.payment.common.crypto.RegisterWatchResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * AC5, AC6. Mirrors {@code auth-service}'s own {@code BreachCheckClientTest} shape — a real,
 * local {@code com.sun.net.httpserver.HttpServer} (JDK built-in, {@code 127.0.0.1} only),
 * <b>not</b> {@code MockRestServiceServer}: that test's own documented gotcha applies here too
 * — {@code CryptoClient}'s own constructor calls {@code .requestFactory(...)} on the builder
 * after binding, which would silently defeat a {@code MockRestServiceServer}-bound factory and
 * fall through to a real network call.
 */
@ExtendWith(MockitoExtension.class)
class CryptoClientTest {

    private static final UUID INVOICE_UUID = UUID.randomUUID();
    private static final Instant EXPIRES_AT = Instant.parse("2026-07-13T01:00:00Z");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private CryptoServiceTokenProvider tokenProvider;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendExpectedAmountAsDecimalStringNotJsonNumber() throws IOException {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        CryptoClient client = startServer(200, "{\"watchId\":\"" + UUID.randomUUID() + "\",\"status\":\"REGISTERED\"}",
                exchange -> capturedBody.set(readBody(exchange)));

        client.registerWatch(INVOICE_UUID, "ETHEREUM", "0xdef", "0xabc", new BigDecimal("1000"), EXPIRES_AT);

        JsonNode sentBody = OBJECT_MAPPER.readTree(capturedBody.get());
        assertThat(sentBody.get("expectedAmount").isTextual())
                .as("expectedAmount must be a JSON string, never a number")
                .isTrue();
        assertThat(sentBody.get("expectedAmount").asText()).isEqualTo("1000");
        assertThat(sentBody.get("invoiceUuid").asText()).isEqualTo(INVOICE_UUID.toString());
        assertThat(sentBody.get("chain").asText()).isEqualTo("ETHEREUM");
        assertThat(sentBody.get("address").asText()).isEqualTo("0xdef");
        assertThat(sentBody.get("tokenContractAddress").asText()).isEqualTo("0xabc");
    }

    @Test
    void shouldSendBearerTokenFromProvider() throws IOException {
        AtomicReference<String> capturedAuthHeader = new AtomicReference<>();
        when(tokenProvider.getAccessToken()).thenReturn("a-real-looking-token");
        CryptoClient client = startServer(200, "{\"watchId\":\"" + UUID.randomUUID() + "\",\"status\":\"REGISTERED\"}",
                exchange -> capturedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization")));

        client.registerWatch(INVOICE_UUID, "ETHEREUM", "0xdef", "0xabc", new BigDecimal("1000"), EXPIRES_AT);

        assertThat(capturedAuthHeader.get()).isEqualTo("Bearer a-real-looking-token");
    }

    @Test
    void shouldReturnWatchIdAndStatusOnSuccess() throws IOException {
        UUID watchId = UUID.randomUUID();
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        CryptoClient client = startServer(200, "{\"watchId\":\"" + watchId + "\",\"status\":\"REGISTERED\"}", null);

        RegisterWatchResponse response =
                client.registerWatch(INVOICE_UUID, "ETHEREUM", "0xdef", "0xabc", new BigDecimal("1000"), EXPIRES_AT);

        assertThat(response.watchId()).isEqualTo(watchId);
        assertThat(response.status()).isEqualTo("REGISTERED");
    }

    /** Phase 9 fix (Kimi Phase 8 finding 8): a non-REGISTERED status must not be treated as
     * success, even with a real, non-null watchId. */
    @Test
    void shouldThrowWhenResponseStatusIsNotRegistered() throws IOException {
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        CryptoClient client = startServer(200,
                "{\"watchId\":\"" + UUID.randomUUID() + "\",\"status\":\"PENDING\"}", null);

        assertThatThrownBy(() -> client.registerWatch(
                INVOICE_UUID, "ETHEREUM", "0xdef", "0xabc", new BigDecimal("1000"), EXPIRES_AT))
                .isInstanceOf(CryptoServiceUnavailableException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void shouldThrowWhenWatchIdIsNull() throws IOException {
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        CryptoClient client = startServer(200, "{\"watchId\":null,\"status\":\"REGISTERED\"}", null);

        assertThatThrownBy(() -> client.registerWatch(
                INVOICE_UUID, "ETHEREUM", "0xdef", "0xabc", new BigDecimal("1000"), EXPIRES_AT))
                .isInstanceOf(CryptoServiceUnavailableException.class);
    }

    @Test
    void shouldThrowOnServerError() throws IOException {
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        CryptoClient client = startServer(500, "error", null);

        assertThatThrownBy(() -> client.registerWatch(
                INVOICE_UUID, "ETHEREUM", "0xdef", "0xabc", new BigDecimal("1000"), EXPIRES_AT))
                .isInstanceOf(CryptoServiceUnavailableException.class);
    }

    @Test
    void shouldThrowOnConnectionFailure() {
        // A deterministically-closed port, same technique as BreachCheckClientTest's own
        // identical precedent: bind, then stop immediately, so the port is guaranteed refused.
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        CryptoClientProperties properties = cryptoClientProperties("http://127.0.0.1:1");
        CryptoClient client = new CryptoClient(RestClient.builder(), tokenProvider, properties);

        assertThatThrownBy(() -> client.registerWatch(
                INVOICE_UUID, "ETHEREUM", "0xdef", "0xabc", new BigDecimal("1000"), EXPIRES_AT))
                .isInstanceOf(CryptoServiceUnavailableException.class);
    }

    @Test
    void shouldThrowWhenServerExceedsConfiguredReadTimeout() throws IOException {
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/watches", exchange -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{\"watchId\":\"" + UUID.randomUUID() + "\",\"status\":\"REGISTERED\"}");
        });
        server.start();

        CryptoClientProperties properties = cryptoClientProperties(
                "http://127.0.0.1:" + server.getAddress().getPort());
        CryptoClient client = new CryptoClient(RestClient.builder(), tokenProvider, properties);

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.registerWatch(
                INVOICE_UUID, "ETHEREUM", "0xdef", "0xabc", new BigDecimal("1000"), EXPIRES_AT))
                .isInstanceOf(CryptoServiceUnavailableException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Phase 11 Gap 11: configured read timeout is 200ms; 500ms leaves generous scheduling
        // headroom while still being well short of the server's own 1000ms sleep, so this
        // actually proves the bounded timeout, not just "eventually returned."
        assertThat(elapsedMs).isLessThan(500);
    }

    // --- T08 (R5): unregisterWatch ---

    @Test
    void shouldSendDeleteRequestOnUnregisterWatch() throws IOException {
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        CryptoClient client = startServer(204, "", exchange -> capturedMethod.set(exchange.getRequestMethod()));

        client.unregisterWatch(UUID.randomUUID());

        assertThat(capturedMethod.get()).isEqualTo("DELETE");
    }

    /** Phase 11 Gap 5 (Kimi): the method-only check above would still pass if the URI were wrong
     * (e.g. missing the watchId path segment entirely). */
    @Test
    void shouldSendDeleteRequestToTheWatchIdSpecificUriOnUnregisterWatch() throws IOException {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        CryptoClient client = startServer(204, "", exchange -> capturedPath.set(exchange.getRequestURI().getPath()));

        UUID watchId = UUID.randomUUID();
        client.unregisterWatch(watchId);

        assertThat(capturedPath.get()).isEqualTo("/internal/v1/watches/" + watchId);
    }

    /** Phase 11 Gap 6 (Kimi): {@code registerWatch}'s own bearer-token injection is covered by
     * {@code shouldSendBearerTokenFromProvider}, but the shared interceptor was never verified
     * for the DELETE path specifically. */
    @Test
    void shouldSendBearerTokenOnUnregisterWatch() throws IOException {
        AtomicReference<String> capturedAuthHeader = new AtomicReference<>();
        when(tokenProvider.getAccessToken()).thenReturn("a-real-looking-token");
        CryptoClient client = startServer(204, "",
                exchange -> capturedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization")));

        client.unregisterWatch(UUID.randomUUID());

        assertThat(capturedAuthHeader.get()).isEqualTo("Bearer a-real-looking-token");
    }

    /** Phase 3 finding 3: a prior successful unregister that a retried sweep repeats (e.g. after
     * a failed DB commit before {@code expireInvoice} ran) must never throw - {@code 404} is one
     * real shape a deployment might use for "already gone." */
    @Test
    void shouldTreatNotFoundAsSuccessOnUnregisterWatch() throws IOException {
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        CryptoClient client = startServer(404, "not found", null);

        assertThatCode(() -> client.unregisterWatch(UUID.randomUUID())).doesNotThrowAnyException();
    }

    /** Phase 3 finding 3: {@code 410 Gone} is the other real shape a deployment might use for
     * "already gone" - also treated as success. */
    @Test
    void shouldTreatGoneAsSuccessOnUnregisterWatch() throws IOException {
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        CryptoClient client = startServer(410, "gone", null);

        assertThatCode(() -> client.unregisterWatch(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void shouldThrowCryptoServiceUnavailableOnUnregisterWatchServerError() throws IOException {
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        CryptoClient client = startServer(500, "error", null);

        assertThatThrownBy(() -> client.unregisterWatch(UUID.randomUUID()))
                .isInstanceOf(CryptoServiceUnavailableException.class);
    }

    @Test
    void shouldThrowCryptoServiceUnavailableOnUnregisterWatchConnectionFailure() {
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        CryptoClientProperties properties = cryptoClientProperties("http://127.0.0.1:1");
        CryptoClient client = new CryptoClient(RestClient.builder(), tokenProvider, properties);

        assertThatThrownBy(() -> client.unregisterWatch(UUID.randomUUID()))
                .isInstanceOf(CryptoServiceUnavailableException.class);
    }

    private CryptoClient startServer(int status, String responseBody,
                                      java.util.function.Consumer<com.sun.net.httpserver.HttpExchange> capture)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/watches", exchange -> {
            if (capture != null) {
                capture.accept(exchange);
            }
            respond(exchange, status, responseBody);
        });
        server.start();

        CryptoClientProperties properties =
                cryptoClientProperties("http://127.0.0.1:" + server.getAddress().getPort());
        return new CryptoClient(RestClient.builder(), tokenProvider, properties);
    }

    private static CryptoClientProperties cryptoClientProperties(String baseUrl) {
        return new CryptoClientProperties(baseUrl, "/internal/v1/attest", "/internal/v1/watches", 200, 200,
                new CryptoClientProperties.Auth("http://127.0.0.1:1/oauth2/token", "payment-service", "secret"));
    }

    private static String readBody(com.sun.net.httpserver.HttpExchange exchange) {
        try {
            return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String responseBody)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
