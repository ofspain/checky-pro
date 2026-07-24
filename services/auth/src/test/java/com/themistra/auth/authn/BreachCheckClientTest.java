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
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedUserAgent = new AtomicReference<>();
        BreachCheckClient client = startServer(200, suffix + ":0", capturedPath, capturedUserAgent);

        client.isBreached(RAW_PASSWORD);

        assertThat(capturedPath.get()).isEqualTo("/range/" + prefix);
        assertThat(capturedUserAgent.get()).isEqualTo("Themistra-Auth-Service/1.0");
    }

    @Test
    void shouldReturnTrueWhenSuffixPresentWithPositiveCount() throws IOException {
        BreachCheckClient client = startServer(200, suffix + ":42", null, null);

        assertThat(client.isBreached(RAW_PASSWORD)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenSuffixPresentWithZeroCount() throws IOException {
        BreachCheckClient client = startServer(200, suffix + ":0", null, null);

        assertThat(client.isBreached(RAW_PASSWORD)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenSuffixAbsentFromResponse() throws IOException {
        BreachCheckClient client = startServer(200, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:7", null, null);

        assertThat(client.isBreached(RAW_PASSWORD)).isFalse();
    }

    @Test
    void shouldMatchSuffixCaseInsensitively() throws IOException {
        BreachCheckClient client = startServer(200, suffix.toLowerCase(Locale.ROOT) + ":3", null, null);

        assertThat(client.isBreached(RAW_PASSWORD)).isTrue();
    }

    @Test
    void shouldIgnoreBlankLinesInResponseBody() throws IOException {
        BreachCheckClient client = startServer(200, "\r\n" + suffix + ":5\r\n\r\n", null, null);

        assertThat(client.isBreached(RAW_PASSWORD)).isTrue();
    }

    @Test
    void shouldThrowBreachCheckUnavailableExceptionOnServerError() throws IOException {
        BreachCheckClient client = startServer(500, "error", null, null);

        assertThatThrownBy(() -> client.isBreached(RAW_PASSWORD))
                .isInstanceOf(BreachCheckClient.BreachCheckUnavailableException.class);
    }

    @Test
    void shouldThrowBreachCheckUnavailableExceptionOnConnectionFailure() {
        // A fast, deterministic stand-in for "the range API is unreachable": connecting to a
        // closed local port fails immediately via the same SimpleClientHttpRequestFactory /
        // RestClientException path a real timeout or DNS failure would take, without a unit test
        // waiting out a real multi-second timeout.
        PasswordPolicyProperties properties = new PasswordPolicyProperties(
                12, 128, new BreachCheck(true, "http://127.0.0.1:1/", 200));
        BreachCheckClient unreachableClient = new BreachCheckClient(RestClient.builder(), properties);

        assertThatThrownBy(() -> unreachableClient.isBreached(RAW_PASSWORD))
                .isInstanceOf(BreachCheckClient.BreachCheckUnavailableException.class);
    }

    @Test
    void shouldRejectNullPassword() throws IOException {
        BreachCheckClient client = startServer(200, "", null, null);

        assertThatThrownBy(() -> client.isBreached(null))
                .isInstanceOf(NullPointerException.class);
    }

    private BreachCheckClient startServer(int status, String responseBody,
                                           AtomicReference<String> capturedPath,
                                           AtomicReference<String> capturedUserAgent) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (capturedPath != null) {
                capturedPath.set(exchange.getRequestURI().toString());
            }
            if (capturedUserAgent != null) {
                capturedUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            }
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        String urlPrefix = "http://127.0.0.1:" + server.getAddress().getPort() + "/range/";
        PasswordPolicyProperties properties =
                new PasswordPolicyProperties(12, 128, new BreachCheck(true, urlPrefix, 3000));
        return new BreachCheckClient(RestClient.builder(), properties);
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
