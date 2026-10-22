package com.themistra.payment.common;

import com.sun.net.httpserver.HttpServer;
import com.themistra.payment.common.config.CryptoClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC7. Mirrors {@code auth-service}'s own {@code BreachCheckClientTest} shape — a real, local
 * {@code com.sun.net.httpserver.HttpServer}, not {@code MockRestServiceServer} (same
 * {@code .requestFactory(...)}-after-binding conflict documented there applies to this class's
 * own constructor too).
 */
class CryptoServiceTokenProviderTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldFetchTokenWithClientCredentialsGrantAndBasicAuth() throws IOException {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedAuthHeader = new AtomicReference<>();
        CryptoServiceTokenProvider provider = startServer(200,
                "{\"access_token\":\"token-1\",\"expires_in\":600}", exchange -> {
                    capturedBody.set(readBody(exchange));
                    capturedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
                }, Clock.systemUTC());

        String token = provider.getAccessToken();

        assertThat(token).isEqualTo("token-1");
        // 2026-10-21: the scope crypto-service requires; without it auth issues a token with no scope.
        assertThat(capturedBody.get()).isEqualTo("grant_type=client_credentials&scope=internal.crypto%3Awrite");
        String expectedBasicAuth = "Basic " + Base64.getEncoder()
                .encodeToString("payment-service:test-secret".getBytes(StandardCharsets.UTF_8));
        assertThat(capturedAuthHeader.get()).isEqualTo(expectedBasicAuth);
    }

    @Test
    void shouldReuseCachedTokenUntilExpiry() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        Instant now = Instant.parse("2026-07-13T00:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        CryptoServiceTokenProvider provider = startServer(200,
                "{\"access_token\":\"token-1\",\"expires_in\":600}",
                exchange -> requestCount.incrementAndGet(), fixedClock);

        String first = provider.getAccessToken();
        String second = provider.getAccessToken();

        assertThat(first).isEqualTo("token-1");
        assertThat(second).isEqualTo("token-1");
        assertThat(requestCount.get()).as("second call must reuse the cached token").isEqualTo(1);
    }

    /** AC7's own named test. Many threads call {@code getAccessToken()} concurrently against an
     * empty cache; the single-flight lock must ensure only one real HTTP request is made. */
    @Test
    void shouldReuseCachedTokenUntilExpiryAndRefreshOnceUnderConcurrency() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        CryptoServiceTokenProvider provider = startServer(200,
                "{\"access_token\":\"concurrent-token\",\"expires_in\":600}",
                exchange -> requestCount.incrementAndGet(), Clock.systemUTC());

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<String> firstFailure = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    String token = provider.getAccessToken();
                    if (!"concurrent-token".equals(token)) {
                        firstFailure.compareAndSet(null, "unexpected token: " + token);
                    }
                } catch (Exception e) {
                    firstFailure.compareAndSet(null, e.toString());
                }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(firstFailure.get()).isNull();
        assertThat(requestCount.get()).as("single-flight must issue exactly one real token request").isEqualTo(1);
    }

    @Test
    void shouldRefreshAfterCachedTokenExpires() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<Instant> clockInstant = new AtomicReference<>(Instant.parse("2026-07-13T00:00:00Z"));
        Clock movableClock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return clockInstant.get();
            }
        };
        CryptoServiceTokenProvider provider = startServer(200,
                "{\"access_token\":\"token-1\",\"expires_in\":60}",
                exchange -> requestCount.incrementAndGet(), movableClock);

        provider.getAccessToken();
        clockInstant.set(clockInstant.get().plusSeconds(120));
        provider.getAccessToken();

        assertThat(requestCount.get()).as("expiry must force a real refetch, not reuse the stale token").isEqualTo(2);
    }

    @Test
    void shouldThrowWhenTokenEndpointReturnsNoAccessToken() throws IOException {
        CryptoServiceTokenProvider provider = startServer(200, "{\"expires_in\":600}", null, Clock.systemUTC());

        assertThatThrownBy(provider::getAccessToken).isInstanceOf(CryptoServiceUnavailableException.class);
    }

    @Test
    void shouldThrowOnServerError() throws IOException {
        CryptoServiceTokenProvider provider = startServer(500, "error", null, Clock.systemUTC());

        assertThatThrownBy(provider::getAccessToken).isInstanceOf(CryptoServiceUnavailableException.class);
    }

    private CryptoServiceTokenProvider startServer(int status, String responseBody,
            java.util.function.Consumer<com.sun.net.httpserver.HttpExchange> capture, Clock clock)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/token", exchange -> {
            if (capture != null) {
                capture.accept(exchange);
            }
            respond(exchange, status, responseBody);
        });
        server.start();

        CryptoClientProperties.Auth auth = new CryptoClientProperties.Auth(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth2/token",
                "payment-service", "test-secret");
        CryptoClientProperties properties = new CryptoClientProperties(
                "http://crypto-service", "/internal/v1/attest", "/internal/v1/watches", 2000, 2000, auth);
        return new CryptoServiceTokenProvider(RestClient.builder(), properties, clock);
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
