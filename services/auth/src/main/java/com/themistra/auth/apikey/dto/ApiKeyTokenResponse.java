package com.themistra.auth.apikey.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code POST /api-keys/token}'s response envelope (T25). Field names are the standard OAuth2
 * token-response shape ({@code access_token}/{@code token_type}/{@code expires_in}, RFC 6749 §5.1)
 * rather than this service's usual camelCase JSON — an explicit {@link JsonProperty} on each
 * component, since no global naming strategy is configured.
 */
public record ApiKeyTokenResponse(

        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn
) {

    public static ApiKeyTokenResponse of(String accessToken, long expiresInSeconds) {
        return new ApiKeyTokenResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
