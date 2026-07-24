package com.themistra.auth.authn;

import com.themistra.auth.account.PasswordPolicyProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Have I Been Pwned k-anonymity range check (R9/L2): only a 5-character uppercase SHA-1 hash
 * prefix ever leaves this process — never the password, never the full hash. Any failure to
 * complete the call (timeout, error status, connection failure) surfaces as
 * {@link BreachCheckUnavailableException} so {@code PasswordPolicy} can apply the R10 fail-open
 * rule instead of receiving a false "not breached" result.
 */
@Component
public class BreachCheckClient {

    private static final int PREFIX_LENGTH = 5;
    private static final String USER_AGENT = "Themistra-Auth-Service/1.0";

    private final RestClient restClient;

    public BreachCheckClient(RestClient.Builder restClientBuilder, PasswordPolicyProperties properties) {
        this.restClient = buildRestClient(restClientBuilder, properties);
    }

    /**
     * @return {@code true} if the password's SHA-1 hash suffix appears in the range response
     * with a count greater than zero.
     * @throws BreachCheckUnavailableException if the range API could not be reached or returned
     * an error — this is the only checked failure mode; a {@code null} argument is a caller bug
     * (see the guard below), not a fail-open condition.
     */
    public boolean isBreached(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        String sha1 = sha1UppercaseHex(rawPassword);
        String prefix = sha1.substring(0, PREFIX_LENGTH);
        String suffix = sha1.substring(PREFIX_LENGTH);

        try {
            // RestClient#retrieve() throws RestClientResponseException (a RestClientException)
            // on any non-2xx status by default, so an HTTP-level failure lands in the catch
            // below along with timeouts/connection errors — it never reaches this line with a
            // response body from an error page.
            String responseBody = restClient.get()
                    .uri("{prefix}", prefix)
                    .retrieve()
                    .body(String.class);
            return responseContainsSuffix(responseBody, suffix);
        } catch (RestClientException e) {
            throw new BreachCheckUnavailableException("Have I Been Pwned range check failed", e);
        }
    }

    private RestClient buildRestClient(RestClient.Builder builder, PasswordPolicyProperties properties) {
        int timeoutMs = Math.toIntExact(properties.breachCheck().timeoutMs());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        String urlPrefix = properties.breachCheck().urlPrefix();
        String baseUrl = urlPrefix.endsWith("/") ? urlPrefix : urlPrefix + "/";

        return builder
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", USER_AGENT)
                .requestFactory(requestFactory)
                .build();
    }

    private String sha1UppercaseHex(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    private boolean responseContainsSuffix(String responseBody, String suffix) {
        if (responseBody == null) {
            return false;
        }
        for (String line : responseBody.split("\r?\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            String lineSuffix = parts[0].trim().toUpperCase(Locale.ROOT);
            long count;
            try {
                count = Long.parseLong(parts[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (count > 0 && lineSuffix.equals(suffix)) {
                return true;
            }
        }
        return false;
    }

    public static class BreachCheckUnavailableException extends RuntimeException {

        public BreachCheckUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
