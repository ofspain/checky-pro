package com.themistra.auth.apikey;

import com.themistra.auth.apikey.dto.ApiKeyTokenResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api-keys/token} (T25, R31/R32/R33, L11) — public; the presented API key is the
 * credential. Registered in {@link com.themistra.auth.common.PublicEndpoints#METHOD_SCOPED}.
 *
 * <p>A controller, not an {@code ApiKeyAuthenticationFilter}, per the frozen brief: the custom
 * {@code ApiKey} scheme (D4) already keeps this request away from
 * {@code BearerTokenAuthenticationFilter} on the application chain's {@code @Order(2)} security
 * filter chain, so no filter is needed to protect the request from being mis-decoded as a JWT; a
 * controller also keeps the rejection path inside this module's own
 * {@link ApiKeyExceptionHandler} rather than a filter-level entry point.</p>
 */
@RestController
@RequestMapping("/api-keys")
public class ApiKeyController {

    /** {@code Authorization: ApiKey <credential>} — matched case-insensitively (RFC 7235);
     * {@code Bearer} is deliberately never accepted (D4). */
    private static final String SCHEME = "ApiKey";
    /** Bounds the credential before it is ever hashed (frozen brief, Inputs / Security constraint). */
    private static final int MAX_CREDENTIAL_LENGTH = 256;

    private final ApiKeyService apiKeyService;
    private final ApiKeyTokenIssuer apiKeyTokenIssuer;

    public ApiKeyController(ApiKeyService apiKeyService, ApiKeyTokenIssuer apiKeyTokenIssuer) {
        this.apiKeyService = apiKeyService;
        this.apiKeyTokenIssuer = apiKeyTokenIssuer;
    }

    /**
     * Validates the presented key, updates {@code last_used_at}, and mints a JWT (R31/R32). Per
     * D5: {@link ApiKeyService#exchange} runs to completion (its own transaction commits) before
     * {@link ApiKeyTokenIssuer#issue} is called — no transactional wrapper spans both. A signing
     * failure from {@code issue} is deliberately not caught here: it propagates to
     * {@code ApiExceptionHandler.onUnexpected}, the framework-level catch-all, yielding an opaque
     * 500 rather than the uniform 401 (D5/AC15) — a signing failure means broken key material, not
     * an invalid caller-presented key, and must never look like one.
     */
    @PostMapping("/token")
    public ApiKeyTokenResponse exchange(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String credential = extractCredential(authorization);
        ApiKeyService.ExchangeResult result = apiKeyService.exchange(credential);
        ApiKeyTokenIssuer.IssuedToken issued =
                apiKeyTokenIssuer.issue(result.accountUuid(), result.scopes());
        return ApiKeyTokenResponse.of(issued.accessToken(), issued.expiresInSeconds());
    }

    /**
     * Normalizes a missing header, wrong scheme, blank credential, or over-length credential all
     * to {@code null} rather than rejecting locally. {@link ApiKeyService#exchange(String)}
     * already takes an audited path for a {@code null}/malformed candidate (mirrors its existing
     * no-separator handling) — routing every header-level rejection cause through that single call
     * keeps exactly one code path responsible for the uniform 401 (R33/AC10) and the per-attempt
     * audit row (R43/AC12); nothing here can bypass either. Truncating an over-length credential
     * would risk silently accepting a corrupted-but-valid-looking value, so it is rejected outright
     * instead, before ever reaching {@link ApiKeyHasher}.
     */
    private String extractCredential(String authorization) {
        if (authorization == null) {
            return null;
        }
        int separator = authorization.indexOf(' ');
        if (separator <= 0 || separator == authorization.length() - 1) {
            return null;
        }
        String scheme = authorization.substring(0, separator);
        if (!SCHEME.equalsIgnoreCase(scheme)) {
            return null;
        }
        String credential = authorization.substring(separator + 1);
        if (credential.isBlank() || credential.length() > MAX_CREDENTIAL_LENGTH) {
            return null;
        }
        return credential;
    }
}
