package com.themistra.auth.apikey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.auth.account.InvalidAccountStateException;
import com.themistra.auth.account.AccountStatus;
import com.themistra.auth.apikey.dto.ApiKeyTokenResponse;
import com.themistra.auth.apikey.dto.CreateApiKeyRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Direct unit test of {@link ApiKeyController}, mirroring {@code AccountControllerTest}'s style —
 * the controller is constructed directly with mocked collaborators and never goes through
 * Spring's dispatcher, so header-parsing and ordering are verified here at the argument level;
 * the actual HTTP status/body mapping for a rejection is verified by
 * {@link ApiKeyExceptionHandlerTest} and end-to-end by {@code ApiKeyExchangeIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyControllerTest {

    private static final UUID ACCOUNT_UUID = UUID.randomUUID();

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private ApiKeyTokenIssuer apiKeyTokenIssuer;

    private ApiKeyController controller;

    @Test // R31/R32 - happy path wires exchange's result straight into the issuer and the response
    void exchangeReturnsTokenResponseForAValidHeader() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        ApiKeyService.ExchangeResult exchangeResult =
                new ApiKeyService.ExchangeResult(ACCOUNT_UUID, List.of("merchant.api"));
        when(apiKeyService.exchange("ck_live_realcredential.secretpart")).thenReturn(exchangeResult);
        when(apiKeyTokenIssuer.issue(ACCOUNT_UUID, List.of("merchant.api")))
                .thenReturn(new ApiKeyTokenIssuer.IssuedToken("signed.jwt.value", 600L));

        ApiKeyTokenResponse response = controller.exchange("ApiKey ck_live_realcredential.secretpart");

        assertThat(response.accessToken()).isEqualTo("signed.jwt.value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(600L);
    }

    @Test // D5 - exchange (its own transaction) must fully complete before issue is ever called
    void exchangeIsCalledBeforeIssue() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        ApiKeyService.ExchangeResult exchangeResult =
                new ApiKeyService.ExchangeResult(ACCOUNT_UUID, List.of("merchant.api"));
        when(apiKeyService.exchange("ck_live_realcredential.secretpart")).thenReturn(exchangeResult);
        when(apiKeyTokenIssuer.issue(ACCOUNT_UUID, List.of("merchant.api")))
                .thenReturn(new ApiKeyTokenIssuer.IssuedToken("signed.jwt.value", 600L));

        controller.exchange("ApiKey ck_live_realcredential.secretpart");

        InOrder order = inOrder(apiKeyService, apiKeyTokenIssuer);
        order.verify(apiKeyService).exchange("ck_live_realcredential.secretpart");
        order.verify(apiKeyTokenIssuer).issue(ACCOUNT_UUID, List.of("merchant.api"));
    }

    @Test // D5/AC15 - a signing failure is never caught locally; it must propagate uncaught so
          // ApiExceptionHandler's generic catch-all yields a 500, never the uniform 401
    void signingFailurePropagatesUncaught() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        ApiKeyService.ExchangeResult exchangeResult =
                new ApiKeyService.ExchangeResult(ACCOUNT_UUID, List.of("merchant.api"));
        when(apiKeyService.exchange("ck_live_realcredential.secretpart")).thenReturn(exchangeResult);
        when(apiKeyTokenIssuer.issue(ACCOUNT_UUID, List.of("merchant.api")))
                .thenThrow(new IllegalStateException("signing key unavailable"));

        assertThatThrownBy(() -> controller.exchange("ApiKey ck_live_realcredential.secretpart"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("signing key unavailable");
    }

    @Test // R33/AC10 - no Authorization header at all -> the single audited malformed path
    void missingHeaderPassesNullToExchange() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        when(apiKeyService.exchange(isNull())).thenThrow(new ApiKeyExchangeRejectedException());

        assertThatThrownBy(() -> controller.exchange(null))
                .isInstanceOf(ApiKeyExchangeRejectedException.class);
        verify(apiKeyService).exchange(isNull());
        verifyNoInteractions(apiKeyTokenIssuer);
    }

    @Test // D4 - Bearer is deliberately never accepted as this endpoint's own scheme. In the real
          // deployed filter chain a Bearer-schemed request never even reaches this controller (it
          // is intercepted earlier by BearerTokenAuthenticationFilter - see
          // ApiKeyExchangeIntegrationTest's D4 regression test for that end-to-end behavior); this
          // unit test only proves extractCredential's own logic is correct in isolation.
    void wrongSchemePassesNullToExchange() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        when(apiKeyService.exchange(isNull())).thenThrow(new ApiKeyExchangeRejectedException());

        assertThatThrownBy(() -> controller.exchange("Bearer ck_live_x.y"))
                .isInstanceOf(ApiKeyExchangeRejectedException.class);
        verify(apiKeyService).exchange(isNull());
    }

    @Test
    void completelyUnrecognizedSchemePassesNullToExchange() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        when(apiKeyService.exchange(isNull())).thenThrow(new ApiKeyExchangeRejectedException());

        assertThatThrownBy(() -> controller.exchange("Basic dXNlcjpwYXNz"))
                .isInstanceOf(ApiKeyExchangeRejectedException.class);
        verify(apiKeyService).exchange(isNull());
    }

    @Test // no space at all between scheme and credential is malformed, not a valid ApiKey header
    void headerWithNoSeparatorPassesNullToExchange() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        when(apiKeyService.exchange(isNull())).thenThrow(new ApiKeyExchangeRejectedException());

        assertThatThrownBy(() -> controller.exchange("ApiKeyck_live_x.y"))
                .isInstanceOf(ApiKeyExchangeRejectedException.class);
        verify(apiKeyService).exchange(isNull());
    }

    @Test // blank credential (scheme with nothing, or only whitespace, after it)
    void blankCredentialPassesNullToExchange() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        when(apiKeyService.exchange(isNull())).thenThrow(new ApiKeyExchangeRejectedException());

        assertThatThrownBy(() -> controller.exchange("ApiKey "))
                .isInstanceOf(ApiKeyExchangeRejectedException.class);
        assertThatThrownBy(() -> controller.exchange("ApiKey    "))
                .isInstanceOf(ApiKeyExchangeRejectedException.class);
        verify(apiKeyService, times(2)).exchange(isNull());
    }

    @Test // Security constraint: bound at 256 characters before ever reaching ApiKeyHasher
    void overLengthCredentialPassesNullToExchange() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        when(apiKeyService.exchange(isNull())).thenThrow(new ApiKeyExchangeRejectedException());
        String tooLong = "x".repeat(257);

        assertThatThrownBy(() -> controller.exchange("ApiKey " + tooLong))
                .isInstanceOf(ApiKeyExchangeRejectedException.class);
        verify(apiKeyService).exchange(isNull());
    }

    @Test // exactly 256 characters is still accepted (the bound is inclusive)
    void exactlyMaxLengthCredentialIsPassedThrough() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        String exactly256 = "x".repeat(256);
        when(apiKeyService.exchange(eq(exactly256))).thenThrow(new ApiKeyExchangeRejectedException());

        assertThatThrownBy(() -> controller.exchange("ApiKey " + exactly256))
                .isInstanceOf(ApiKeyExchangeRejectedException.class);
        verify(apiKeyService).exchange(eq(exactly256));
        verify(apiKeyService, never()).exchange(isNull());
    }

    @Test // D4 - scheme matching is case-insensitive per RFC 7235
    void schemeMatchIsCaseInsensitive() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        when(apiKeyService.exchange(eq("ck_live_x.y"))).thenThrow(new ApiKeyExchangeRejectedException());

        assertThatThrownBy(() -> controller.exchange("apikey ck_live_x.y")).isInstanceOf(ApiKeyExchangeRejectedException.class);
        assertThatThrownBy(() -> controller.exchange("APIKEY ck_live_x.y")).isInstanceOf(ApiKeyExchangeRejectedException.class);
        assertThatThrownBy(() -> controller.exchange("ApIkEy ck_live_x.y")).isInstanceOf(ApiKeyExchangeRejectedException.class);
        verify(apiKeyService, times(3)).exchange(eq("ck_live_x.y"));
    }

    @Test // Phase 9 gate fix: the credential portion tolerates incidental extra whitespace
          // (RFC 7235 separates scheme/credential with 1*SP); a real key has no internal
          // whitespace, so trimming cannot create a false accept.
    void extraWhitespaceAroundCredentialIsTrimmed() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        when(apiKeyService.exchange(eq("ck_live_x.y"))).thenThrow(new ApiKeyExchangeRejectedException());

        assertThatThrownBy(() -> controller.exchange("ApiKey  ck_live_x.y")) // two leading spaces
                .isInstanceOf(ApiKeyExchangeRejectedException.class);
        verify(apiKeyService).exchange(eq("ck_live_x.y"));
    }

    @Test // Kimi Phase 11 Gap 3: the leading-whitespace case above only proves half the trim fix -
          // a trailing space after the credential must be trimmed too.
    void trailingWhitespaceAfterCredentialIsTrimmed() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        when(apiKeyService.exchange(eq("ck_live_x.y"))).thenThrow(new ApiKeyExchangeRejectedException());

        assertThatThrownBy(() -> controller.exchange("ApiKey ck_live_x.y ")) // trailing space
                .isInstanceOf(ApiKeyExchangeRejectedException.class);
        verify(apiKeyService).exchange(eq("ck_live_x.y"));
    }

    @Test // D4/L7 - the credential itself is case-sensitive, unlike the scheme
    void credentialCaseIsPreservedVerbatim() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        when(apiKeyService.exchange(eq("ck_live_MixedCase.Secret")))
                .thenThrow(new ApiKeyExchangeRejectedException());

        assertThatThrownBy(() -> controller.exchange("ApiKey ck_live_MixedCase.Secret"))
                .isInstanceOf(ApiKeyExchangeRejectedException.class);
        verify(apiKeyService).exchange(eq("ck_live_MixedCase.Secret"));
    }

    @Test // Kimi Phase 11 Gap 5: proves the @JsonProperty annotations actually produce snake_case
          // field names, independent of Docker - ApiKeyExchangeIntegrationTest also checks this,
          // but that test cannot run without Testcontainers; this one always can.
    void responseSerializesWithSnakeCaseFieldNames() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        String json = objectMapper.writeValueAsString(ApiKeyTokenResponse.of("x", 600L));

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> asMap = objectMapper.readValue(json, java.util.Map.class);
        assertThat(asMap.keySet()).containsExactlyInAnyOrder("access_token", "token_type", "expires_in");
        assertThat(asMap.get("access_token")).isEqualTo("x");
        assertThat(asMap.get("token_type")).isEqualTo("Bearer");
        assertThat(asMap.get("expires_in")).isEqualTo(600);
    }

    // -----------------------------------------------------------------
    // T26: create / list / revoke
    // -----------------------------------------------------------------

    @Test // R30, D2, D4 - happy path returns 201 with the service's own result, no wrapping DTO
    void createReturns201WithTheServiceResult() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        ApiKeyService.CreateApiKeyResult serviceResult = new ApiKeyService.CreateApiKeyResult(
                UUID.randomUUID(), "ck_live_abcdefghijklmnopqrstuvwx.abcdefghijklmnopqrstuvwxyz123456",
                "CI key", Instant.parse("2026-01-01T00:00:00Z"));
        when(apiKeyService.create(ACCOUNT_UUID, "CI key")).thenReturn(serviceResult);

        ResponseEntity<ApiKeyService.CreateApiKeyResult> response =
                controller.create(authentication, new CreateApiKeyRequest("CI key"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(serviceResult);
    }

    @Test // D2/AC2 - caller identity comes from Authentication, never the request body
    void createDerivesCallerFromAuthenticationNotRequestBody() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        when(apiKeyService.create(ACCOUNT_UUID, "a name")).thenReturn(
                new ApiKeyService.CreateApiKeyResult(UUID.randomUUID(), "ck_live_x.y", "a name", Instant.now()));

        controller.create(authentication, new CreateApiKeyRequest("a name"));

        verify(apiKeyService).create(ACCOUNT_UUID, "a name");
    }

    @Test // D8 - no Location header on the 201: there is no GET /api-keys/{keyUuid} to point to
    void createResponseHasNoLocationHeader() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        when(apiKeyService.create(eq(ACCOUNT_UUID), eq("name"))).thenReturn(
                new ApiKeyService.CreateApiKeyResult(UUID.randomUUID(), "ck_live_x.y", "name", Instant.now()));

        ResponseEntity<ApiKeyService.CreateApiKeyResult> response =
                controller.create(authentication, new CreateApiKeyRequest("name"));

        assertThat(response.getHeaders().getLocation()).isNull();
    }

    @Test // R30/AC9 - a caller lacking MERCHANT/confirmed MFA propagates uncaught for
          // ApiKeyExceptionHandler.onNotAuthorized to translate
    void createPropagatesApiKeyNotAuthorizedUncaught() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        when(apiKeyService.create(eq(ACCOUNT_UUID), eq("name")))
                .thenThrow(new ApiKeyNotAuthorizedException());

        assertThatThrownBy(() -> controller.create(authentication, new CreateApiKeyRequest("name")))
                .isInstanceOf(ApiKeyNotAuthorizedException.class);
    }

    @Test // D6/AC10 - a non-ACTIVE account propagates uncaught for the existing, unmodified
          // AccountExceptionHandler to translate (409, accepted as-is)
    void createPropagatesInvalidAccountStateUncaught() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        when(apiKeyService.create(eq(ACCOUNT_UUID), eq("name")))
                .thenThrow(new InvalidAccountStateException(ACCOUNT_UUID, AccountStatus.PENDING_VERIFICATION, "create an API key"));

        assertThatThrownBy(() -> controller.create(authentication, new CreateApiKeyRequest("name")))
                .isInstanceOf(InvalidAccountStateException.class);
    }

    @Test // R34 - returns exactly what the service returns, no wrapping/filtering in the controller
    void listReturnsTheCallersOwnKeys() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        List<ApiKeyService.ApiKeyMetadata> keys = List.of(
                new ApiKeyService.ApiKeyMetadata(UUID.randomUUID(), "key one", List.of("merchant.api"),
                        Instant.now(), null, null, null));
        when(apiKeyService.list(ACCOUNT_UUID)).thenReturn(keys);

        List<ApiKeyService.ApiKeyMetadata> result = controller.list(authentication);

        assertThat(result).isSameAs(keys);
    }

    @Test // R34 - zero keys is a normal empty list, not an error
    void listReturnsEmptyListWhenCallerHasNoKeys() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        when(apiKeyService.list(ACCOUNT_UUID)).thenReturn(List.of());

        List<ApiKeyService.ApiKeyMetadata> result = controller.list(authentication);

        assertThat(result).isEmpty();
    }

    @Test // R34/AC2 - list is scoped to the authenticated caller's own resolved UUID
    void listDerivesCallerFromAuthentication() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        when(apiKeyService.list(ACCOUNT_UUID)).thenReturn(List.of());

        controller.list(authentication);

        verify(apiKeyService).list(ACCOUNT_UUID);
    }

    @Test // R35 - revoke wires the caller's UUID and the path-variable keyUuid straight through
    void revokeCallsServiceWithCallerAndKeyUuid() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        UUID keyUuid = UUID.randomUUID();

        ResponseEntity<Void> response = controller.revoke(authentication, keyUuid);

        verify(apiKeyService).revoke(ACCOUNT_UUID, keyUuid);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test // R35/AC7 - an unowned or nonexistent key propagates uncaught for
          // ApiKeyExceptionHandler.onNotFound to translate to a uniform 404
    void revokePropagatesApiKeyNotFoundUncaught() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        UUID keyUuid = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ApiKeyNotFoundException())
                .when(apiKeyService).revoke(ACCOUNT_UUID, keyUuid);

        assertThatThrownBy(() -> controller.revoke(authentication, keyUuid))
                .isInstanceOf(ApiKeyNotFoundException.class);
    }

    @Test // R35 - revoking an already-revoked key is idempotent: ApiKeyService.revoke doesn't
          // throw for that case, so the controller must still return 204, not surface an error
    void revokeOfAlreadyRevokedKeyStillReturns204() {
        controller = new ApiKeyController(apiKeyService, apiKeyTokenIssuer);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        UUID keyUuid = UUID.randomUUID();
        // ApiKeyService.revoke is a void method that simply no-ops on an already-revoked key -
        // the mock's default no-op behavior for an unstubbed void call already models this exactly.

        ResponseEntity<Void> response = controller.revoke(authentication, keyUuid);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private static Authentication authenticationFor(UUID accountUuid) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(accountUuid.toString());
        return authentication;
    }
}
