package com.themistra.auth.authn;

import com.sun.net.httpserver.HttpServer;
import com.themistra.auth.account.PasswordPolicyProperties;
import com.themistra.auth.account.PasswordPolicyProperties.BreachCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BreachCheckClient} — R9's HIBP k-anonymity range check.
 *
 * <p>Uses a real, local {@code com.sun.net.httpserver.HttpServer} (JDK built-in, no new test
 * dependency, {@code 127.0.0.1} only — never the real internet) rather than
 * {@code MockRestServiceServer}. Binding {@code MockRestServiceServer} to a
 * {@code RestClient.Builder} works by replacing the builder's {@code ClientHttpRequestFactory} —
 * which {@link BreachCheckClient#isBreached}'s own timeout-configuring {@code requestFactory(...)}
 * call silently overwrites, since it runs after the mock binding and before the builder's
 * {@code build()}. That combination was caught empirically while writing this test: it made the
 * production code fall through to a real network call in a sandbox with internet access. A local
 * HTTP server exercises the exact production constructor and code path end-to-end with no such
 * conflict, and needed no production-code change.</p>
 *
 * <p>The server context is bound to the exact expected path ({@code /range/<prefix>}), not a
 * catch-all root handler — any test where the client constructs the wrong URI gets a 404 from the
 * server itself (Phase 11 test review, Gap 1), so URI correctness is proven by every test here,
 * not only the one that inspects the captured path directly.</p>
 */
class BreachCheckClientTest {

    private static final String RAW_PASSWORD = "correct-horse-battery-staple";

    private HttpServer server;
    private String prefix;
    private String suffix;

    @BeforeEach
    void computeHashParts() {
        String sha1 = sha1UppercaseHex(RAW_PASSWORD);
        prefix = sha1.substring(0, 5);
        suffix = sha1.substring(5);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldQueryFiveCharacterUppercasePrefixWithUserAgentHeader() throws IOException {
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedUserAgent = new AtomicReference<>();
        BreachCheckClient client = startServer("/range/" + prefix, 200, suffix + ":0", capturedMethod, capturedUserAgent);

        client.isBreached(RAW_PASSWORD);

        assertThat(capturedMethod.get()).isEqualTo("GET");
        assertThat(capturedUserAgent.get()).isEqualTo("Themistra-Auth-Service/1.0");
    }

    @Test
    void shouldReturnTrueWhenSuffixPresentWithPositiveCount() throws IOException {
        BreachCheckClient client = startServer("/range/" + prefix, 200, suffix + ":42", null, null);

        assertThat(client.isBreached(RAW_PASSWORD)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenSuffixPresentWithZeroCount() throws IOException {
        BreachCheckClient client = startServer("/range/" + prefix, 200, suffix + ":0", null, null);

        assertThat(client.isBreached(RAW_PASSWORD)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenSuffixAbsentFromResponse() throws IOException {
        BreachCheckClient client = startServer(
                "/range/" + prefix, 200, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:7", null, null);

        assertThat(client.isBreached(RAW_PASSWORD)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenMatchingSuffixHasZeroCountAlongsideAnotherPositiveCount() throws IOException {
        // A matching suffix with count 0 must not be treated as breached even when a *different*
        // suffix in the same response has a positive count (Phase 11 Gap 6).
        BreachCheckClient client = startServer(
                "/range/" + prefix, 200,
                suffix + ":0\r\nAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:99", null, null);

        assertThat(client.isBreached(RAW_PASSWORD)).isFalse();
    }

    @Test
    void shouldMatchSuffixCaseInsensitively() throws IOException {
        BreachCheckClient client =
                startServer("/range/" + prefix, 200, suffix.toLowerCase(Locale.ROOT) + ":3", null, null);

        assertThat(client.isBreached(RAW_PASSWORD)).isTrue();
    }

    @Test
    void shouldIgnoreBlankLinesInResponseBody() throws IOException {
        BreachCheckClient client = startServer("/range/" + prefix, 200, "\r\n" + suffix + ":5\r\n\r\n", null, null);

        assertThat(client.isBreached(RAW_PASSWORD)).isTrue();
    }

    @Test
    void shouldTolerateMalformedLinesAndWhitespaceAroundColon() throws IOException {
        // Garbage lines without a colon, and whitespace around the colon, must not break parsing
        // of the real matching line (Phase 11 Gap 6).
        String body = "garbage-no-colon\r\n" + suffix + " : 7 \r\nnot-a-number:xyz\r\n";
        BreachCheckClient client = startServer("/range/" + prefix, 200, body, null, null);

        assertThat(client.isBreached(RAW_PASSWORD)).isTrue();
    }

    @Test
    void shouldResolveCorrectPathWhenUrlPrefixIsMissingTrailingSlash() throws IOException {
        // BreachCheckClient normalizes a urlPrefix missing a trailing slash (Phase 9 fix); prove
        // the request still lands on /range/<prefix>, not /rangeABCDE (Phase 11 Gap 3).
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/range/" + prefix, exchange -> respond(exchange, 200, suffix + ":0"));
        server.start();

        String urlPrefixNoTrailingSlash = "http://127.0.0.1:" + server.getAddress().getPort() + "/range";
        PasswordPolicyProperties properties = new PasswordPolicyProperties(
                12, 128, new BreachCheck(true, urlPrefixNoTrailingSlash, 3000));
        BreachCheckClient client = new BreachCheckClient(RestClient.builder(), properties);

        assertThat(client.isBreached(RAW_PASSWORD)).isFalse();
    }

    @Test
    void shouldThrowBreachCheckUnavailableExceptionOnServerError() throws IOException {
        BreachCheckClient client = startServer("/range/" + prefix, 500, "error", null, null);

        assertThatThrownBy(() -> client.isBreached(RAW_PASSWORD))
                .isInstanceOf(BreachCheckClient.BreachCheckUnavailableException.class);
    }

    @Test
    void shouldThrowBreachCheckUnavailableExceptionOnConnectionFailure() throws IOException {
        // A deterministically-closed port: bind an ephemeral server, stop it immediately, then
        // connect to that now-closed address. Unlike a hardcoded low port number (e.g. 1), this
        // is guaranteed refused rather than filtered/open on some systems (Phase 11 Gap 4).
        HttpServer throwawayServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int closedPort = throwawayServer.getAddress().getPort();
        throwawayServer.stop(0);

        PasswordPolicyProperties properties = new PasswordPolicyProperties(
                12, 128, new BreachCheck(true, "http://127.0.0.1:" + closedPort + "/", 3000));
        BreachCheckClient unreachableClient = new BreachCheckClient(RestClient.builder(), properties);

        assertThatThrownBy(() -> unreachableClient.isBreached(RAW_PASSWORD))
                .isInstanceOf(BreachCheckClient.BreachCheckUnavailableException.class);
    }

    @Test
    void shouldThrowBreachCheckUnavailableExceptionWhenServerExceedsConfiguredTimeout() throws IOException {
        // Proves the bounded timeout itself, not just a connection failure (Phase 11 Gap 5): the
        // server deliberately sleeps well past a short configured timeout-ms before responding.
        long timeoutMs = 100;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/range/" + prefix, exchange -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, suffix + ":0");
        });
        server.start();

        String urlPrefix = "http://127.0.0.1:" + server.getAddress().getPort() + "/range/";
        PasswordPolicyProperties properties =
                new PasswordPolicyProperties(12, 128, new BreachCheck(true, urlPrefix, timeoutMs));
        BreachCheckClient client = new BreachCheckClient(RestClient.builder(), properties);

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.isBreached(RAW_PASSWORD))
                .isInstanceOf(BreachCheckClient.BreachCheckUnavailableException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(1000);
    }

    @Test
    void shouldRejectNullPassword() throws IOException {
        BreachCheckClient client = startServer("/range/" + prefix, 200, "", null, null);

        assertThatThrownBy(() -> client.isBreached(null))
                .isInstanceOf(NullPointerException.class);
    }

    private BreachCheckClient startServer(String contextPath, int status, String responseBody,
                                           AtomicReference<String> capturedMethod,
                                           AtomicReference<String> capturedUserAgent) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(contextPath, exchange -> {
            if (capturedMethod != null) {
                capturedMethod.set(exchange.getRequestMethod());
            }
            if (capturedUserAgent != null) {
                capturedUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            }
            respond(exchange, status, responseBody);
        });
        server.start();

        String urlPrefix = "http://127.0.0.1:" + server.getAddress().getPort() + "/range/";
        PasswordPolicyProperties properties =
                new PasswordPolicyProperties(12, 128, new BreachCheck(true, urlPrefix, 3000));
        return new BreachCheckClient(RestClient.builder(), properties);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String responseBody)
            throws IOException {
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static String sha1UppercaseHex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
