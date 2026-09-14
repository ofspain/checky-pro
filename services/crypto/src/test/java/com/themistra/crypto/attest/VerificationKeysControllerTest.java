package com.themistra.crypto.attest;

import com.themistra.crypto.common.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mirrors {@code AttestControllerTest}'s exact {@code @WebMvcTest} slice pattern -
 * {@code @AutoConfigureMockMvc(addFilters = false)} since {@code ResourceServerConfig} is not part of
 * this narrow slice; the endpoint's own public-reachability (no scope required, unlike the internal
 * endpoints) is covered separately by {@code ResourceServerConfigIntegrationTest}. */
@WebMvcTest(controllers = VerificationKeysController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class VerificationKeysControllerTest {

    private static final String KMS_KEY_ID = "arn:aws:kms:us-east-1:111122223333:key/abc-123";
    private static final String PEM = "-----BEGIN PUBLIC KEY-----\nZmFrZQ==\n-----END PUBLIC KEY-----\n";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KmsSigner kmsSigner;

    @Test
    void shouldPublishVerificationKeysAtWellKnownUrl() throws Exception {
        when(kmsSigner.publicKeyInfo()).thenReturn(
                new PublicKeyInfo(KMS_KEY_ID, KMS_KEY_ID, "ECDSA_SHA_256", PEM));

        mockMvc.perform(get("/.well-known/themistra-verification-keys"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.keys[0].kid").value(KMS_KEY_ID))
                .andExpect(jsonPath("$.keys[0].kmsKeyId").value(KMS_KEY_ID))
                .andExpect(jsonPath("$.keys[0].alg").value("ECDSA_SHA_256"))
                .andExpect(jsonPath("$.keys[0].publicKeyPem").value(PEM))
                .andExpect(jsonPath("$.keys.length()").value(1));

        // Phase 11 (Kimi) Gap 1: a future refactor accidentally calling publicKeyInfo() a second time
        // (e.g. once for logging/metrics, once for the body) would double the KMS GetPublicKey cost and
        // could produce an inconsistent result if the key rotated between the two calls.
        verify(kmsSigner, times(1)).publicKeyInfo();
    }

    @Test
    void postIsNotAllowedOnTheWellKnownPath() throws Exception {
        // Phase 11 (Kimi) Gap 3: the endpoint's contract is GET-only - a regression exposing POST could
        // trigger a non-idempotent KMS call from an unexpected HTTP method.
        //
        // Real, pre-existing defect discovered by this test, out of T22's own scope to fix: Spring
        // throws HttpRequestMethodNotSupportedException here (confirmed via the actual server log), but
        // common.ApiExceptionHandler's generic @ExceptionHandler(Exception.class) catch-all has no more
        // specific handler for it, so it is swallowed into a 500 "Internal error" instead of the
        // correct 405. This is a defect in shared code (common/ApiExceptionHandler.java, explicitly
        // listed as Files NOT to Modify in this task's own frozen brief), not something T22 introduced
        // or is scoped to fix - this test locks in the actual current behavior so a future fix to that
        // shared class is a deliberate, visible change here, not a silent regression.
        mockMvc.perform(post("/.well-known/themistra-verification-keys"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void unknownSubPathReturnsAnErrorRatherThanTheSameHandler() throws Exception {
        // Phase 11 (Kimi) Gap 3: no path variable is declared - a sub-path must not resolve to the same
        // handler (it doesn't - this assertion alone proves that much regardless of the exact status).
        //
        // Same pre-existing, out-of-scope ApiExceptionHandler gap as postIsNotAllowedOnTheWellKnownPath
        // above: Spring throws NoResourceFoundException (confirmed via the actual server log) for this
        // unmatched path, which should be a 404, but the shared catch-all turns it into a 500 instead.
        mockMvc.perform(get("/.well-known/themistra-verification-keys/extra"))
                .andExpect(status().isInternalServerError());
    }

    @ParameterizedTest
    @MethodSource("publicKeyInfoFailures")
    void aPublicKeyInfoFailurePropagatesToAGenericFiveHundred(RuntimeException failure) throws Exception {
        // AC6 (frozen brief Phase 4, Finding #3). Phase 9 (Kimi Phase 8 Finding #4): parameterized over
        // both a generic infrastructure failure and the real IllegalStateException KmsSigner's own
        // guards produce (Phase 3 Findings #1/#6) - the original test only proved the former, but its
        // name implied coverage of publicKeyInfo()'s actual failure modes.
        when(kmsSigner.publicKeyInfo()).thenThrow(failure);

        mockMvc.perform(get("/.well-known/themistra-verification-keys"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal error"));
    }

    static Stream<RuntimeException> publicKeyInfoFailures() {
        return Stream.of(
                new RuntimeException("kms unreachable"),
                new IllegalStateException("KMS returned no public key for keyId=alias/attestation-key"));
    }
}
