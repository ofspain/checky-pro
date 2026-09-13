package com.themistra.crypto.attest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.crypto.common.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mirrors {@code watch.WatchControllerTest}'s exact {@code @WebMvcTest} slice pattern -
 * {@code @AutoConfigureMockMvc(addFilters = false)} since {@code ResourceServerConfig} is not part of
 * this narrow slice (R27/scope enforcement is covered separately, {@code ResourceServerConfigIntegrationTest}). */
@WebMvcTest(controllers = AttestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AttestExceptionHandler.class, ApiExceptionHandler.class})
class AttestControllerTest {

    private static final String DIGEST_HEX = "d".repeat(64);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AttestationService attestationService;

    private AttestRequest validRequest() {
        return new AttestRequest(DIGEST_HEX, "ETHEREUM", "0xtx");
    }

    @Test
    void shouldReturnKmsSignatureFromAttestForValidDigest() throws Exception {
        when(attestationService.attest(any())).thenReturn(
                AttestResponse.signed("c2ln", "arn:aws:kms:key/abc", Instant.parse("2026-09-14T00:00:00Z")));

        mockMvc.perform(post("/internal/v1/attest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SIGNED"))
                .andExpect(jsonPath("$.signature").value("c2ln"))
                .andExpect(jsonPath("$.kmsKeyId").value("arn:aws:kms:key/abc"))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void blockedResponseOmitsSigningFieldsEntirely() throws Exception {
        // Phase 4 Finding #7: @JsonInclude(NON_NULL) - a BLOCKED body must never carry a stray
        // "signature": null / "kmsKeyId": null / "signedAt": null.
        when(attestationService.attest(any())).thenReturn(AttestResponse.blocked("sanctioned"));

        mockMvc.perform(post("/internal/v1/attest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("BLOCKED"))
                .andExpect(jsonPath("$.reason").value("sanctioned"))
                .andExpect(jsonPath("$.signature").doesNotExist())
                .andExpect(jsonPath("$.kmsKeyId").doesNotExist())
                .andExpect(jsonPath("$.signedAt").doesNotExist());
    }

    @Test
    void refusedMapsToProblemJson409() throws Exception {
        when(attestationService.attest(any())).thenThrow(new AttestationRefusedException("ETHEREUM", "0xtx"));

        mockMvc.perform(post("/internal/v1/attest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Attestation refused"))
                .andExpect(jsonPath("$.detail").value("Attestation preconditions not met for ETHEREUM:0xtx"));
    }

    @Test
    void malformedDigestReturnsBadRequest() throws Exception {
        AttestRequest badDigest = new AttestRequest("not-hex", "ETHEREUM", "0xtx");

        mockMvc.perform(post("/internal/v1/attest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badDigest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void unsupportedChainReturnsBadRequest() throws Exception {
        AttestRequest badChain = new AttestRequest(DIGEST_HEX, "BITCOIN", "0xtx");

        mockMvc.perform(post("/internal/v1/attest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badChain)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void blankTxHashReturnsBadRequest() throws Exception {
        AttestRequest blankTxHash = new AttestRequest(DIGEST_HEX, "ETHEREUM", "");

        mockMvc.perform(post("/internal/v1/attest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankTxHash)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }
}
