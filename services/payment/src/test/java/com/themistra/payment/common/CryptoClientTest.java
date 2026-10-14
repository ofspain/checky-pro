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

        assertThat(elapsedMs).isLessThan(1000);
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
