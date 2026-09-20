package com.themistra.auth.apikey;

import com.themistra.auth.apikey.dto.ApiKeyTokenResponse;
import com.themistra.auth.apikey.dto.CreateApiKeyRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code /api-keys} (R30/R34/R35 self-service CRUD, T26; {@code POST /api-keys/token} exchange,
 * T25, R31/R32/R33, L11). Only the token-exchange endpoint is public — registered in
 * {@link com.themistra.auth.common.PublicEndpoints#METHOD_SCOPED}; the three CRUD endpoints below
 * require authentication like any other resource-server API on this service's {@code @Order(2)}
 * chain.
 *
 * <p>A controller, not an {@code ApiKeyAuthenticationFilter}, per the frozen brief: the custom
 * {@code ApiKey} scheme (D4) already keeps the exchange request away from
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
     * Creates a new API key for the caller (R30). Returns {@link ApiKeyService.CreateApiKeyResult}
     * directly — it already has no hash field by construction, so there is no separate response
     * DTO to keep in sync (Phase 4 gate D2). {@code 201}, no {@code Location} header: there is no
     * {@code GET /api-keys/{keyUuid}} endpoint for one to correctly resolve to (D8).
     * {@link ApiKeyNotAuthorizedException} (caller lacks {@code MERCHANT} or confirmed MFA) and any
     * account-state rejection propagate uncaught for the respective advice to translate.
     */
    @PostMapping
    public ResponseEntity<ApiKeyService.CreateApiKeyResult> create(
            Authentication authentication, @Valid @RequestBody CreateApiKeyRequest request) {
        UUID accountUuid = UUID.fromString(authentication.getName());
        ApiKeyService.CreateApiKeyResult result = apiKeyService.create(accountUuid, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Lists the caller's own keys, metadata only (R34). Returns
     * {@link ApiKeyService.ApiKeyMetadata} directly for the same reason {@link #create} returns
     * {@code CreateApiKeyResult} directly (D3) — it already has no hash field. An empty list for a
     * caller with no keys is a normal {@code 200}, not an error.
     */
    @GetMapping
    public List<ApiKeyService.ApiKeyMetadata> list(Authentication authentication) {
        UUID accountUuid = UUID.fromString(authentication.getName());
        return apiKeyService.list(accountUuid);
    }

    /**
     * Revokes a key the caller owns (R35). {@link ApiKeyNotFoundException} — thrown identically
     * whether {@code keyUuid} doesn't exist at all or exists but isn't owned by the caller (no
     * enumeration oracle) — propagates uncaught for {@link ApiKeyExceptionHandler} to translate to
     * a uniform 404. Revoking an already-revoked key still returns {@code 204}: {@link
     * ApiKeyService#revoke} is idempotent and never throws for that case.
     */
    @DeleteMapping("/{keyUuid}")
    public ResponseEntity<Void> revoke(Authentication authentication, @PathVariable UUID keyUuid) {
        UUID accountUuid = UUID.fromString(authentication.getName());
        apiKeyService.revoke(accountUuid, keyUuid);
        return ResponseEntity.noContent().build();
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
     *
     * <p>Leading/trailing whitespace around the credential is trimmed before the blank/length
     * checks (Phase 9 gate) — RFC 7235's {@code credentials} grammar separates scheme and
     * credential with {@code 1*SP} (one or more spaces), so a compliant client sending an extra
     * separator space is not malformed. A real {@code ck_live_<suffix>.<secret>} key never
     * contains internal whitespace (L7), so trimming cannot turn one valid-looking key into
     * another — it only affects headers that would otherwise fail on incidental formatting.</p>
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
        String credential = authorization.substring(separator + 1).trim();
        if (credential.isBlank() || credential.length() > MAX_CREDENTIAL_LENGTH) {
            return null;
        }
        return credential;
    }
}
