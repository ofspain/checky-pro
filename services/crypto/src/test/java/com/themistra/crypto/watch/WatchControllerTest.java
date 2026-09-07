package com.themistra.crypto.watch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.crypto.common.ApiExceptionHandler;
import com.themistra.crypto.watch.dto.RegisterWatchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code @AutoConfigureMockMvc(addFilters = false)} - {@code ResourceServerConfig} is not part of this
 * narrow {@code @WebMvcTest} slice (T15 Phase 8/9's own real-HTTP verification found the default
 * Spring Boot test security auto-configuration otherwise rejects every request with a generic 403
 * before it ever reaches the controller); R27 scope enforcement is already covered separately by
 * {@code ResourceServerConfigIntegrationTest} (T03). */
@WebMvcTest(controllers = WatchController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({WatchExceptionHandler.class, ApiExceptionHandler.class})
class WatchControllerTest {

    private static final String VALID_EVM_ADDRESS = "0x5AEDA56215b167893e80B4fE645BA6d5Bab767DE";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WatchService watchService;

    @Test
    void shouldRegisterWatchAndReturnWatchId() throws Exception {
        UUID watchId = UUID.randomUUID();
        Watch watch = Watch.register(watchId, UUID.randomUUID(), "ETHEREUM", VALID_EVM_ADDRESS,
                VALID_EVM_ADDRESS, BigDecimal.valueOf(1_000_000L), Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now());
        when(watchService.register(any())).thenReturn(watch);

        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                VALID_EVM_ADDRESS, VALID_EVM_ADDRESS, "1000000", Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(post("/internal/v1/watches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watchId").value(watchId.toString()))
                .andExpect(jsonPath("$.status").value("REGISTERED"));
    }

    @Test
    void postWithAMissingRequiredFieldReturnsProblemJsonValidationFailure() throws Exception {
        mockMvc.perform(post("/internal/v1/watches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void postWithAnUnrecognizedChainReturnsBadRequest() throws Exception {
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "BITCOIN",
                VALID_EVM_ADDRESS, VALID_EVM_ADDRESS, "1000000", Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(post("/internal/v1/watches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void postWithAnOversizeAddressReturnsBadRequest() throws Exception {
        String oversizeAddress = "0x" + "a".repeat(200);
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                oversizeAddress, VALID_EVM_ADDRESS, "1000000", Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(post("/internal/v1/watches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void postWithMalformedJsonReturnsProblemJsonMalformedBody() throws Exception {
        mockMvc.perform(post("/internal/v1/watches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Malformed request body"));
    }

    @Test
    void postMappedToAnInvalidWatchRequestExceptionReturnsProblemJsonBadRequest() throws Exception {
        when(watchService.register(any()))
                .thenThrow(new InvalidWatchRequestException("address is not a valid ETHEREUM address"));

        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                VALID_EVM_ADDRESS, VALID_EVM_ADDRESS, "1000000", Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(post("/internal/v1/watches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid watch registration request"))
                .andExpect(jsonPath("$.detail").value("address is not a valid ETHEREUM address"));
    }

    @Test
    void postWithAnInvalidTokenContractAddressReturnsProblemJsonBadRequest() throws Exception {
        // Phase 11 (Kimi Issue 5): the tokenContractAddress-specific counterpart of the test above -
        // confirms the controller-to-handler wiring surfaces this field's own distinct message too, not
        // just address's.
        when(watchService.register(any())).thenThrow(
                new InvalidWatchRequestException("tokenContractAddress is not a valid ETHEREUM address"));

        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                VALID_EVM_ADDRESS, VALID_EVM_ADDRESS, "1000000", Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(post("/internal/v1/watches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid watch registration request"))
                .andExpect(jsonPath("$.detail").value("tokenContractAddress is not a valid ETHEREUM address"));
    }

    @Test
    void postWithAnUnparseableExpiresAtReturnsProblemJsonMalformedBody() throws Exception {
        // Phase 11 (Kimi Issue 4): a well-formed JSON body whose expiresAt Jackson cannot parse as an
        // Instant reaches ApiExceptionHandler.onUnreadableBody via a different path
        // (HttpMessageNotReadableException wrapping a JsonMappingException) than syntactically invalid
        // JSON - both must produce the same shape.
        String body = "{\"invoiceUuid\":\"" + UUID.randomUUID() + "\",\"chain\":\"ETHEREUM\","
                + "\"address\":\"" + VALID_EVM_ADDRESS + "\",\"tokenContractAddress\":\"" + VALID_EVM_ADDRESS
                + "\",\"expectedAmount\":\"1000000\",\"expiresAt\":\"not-an-instant\"}";

        mockMvc.perform(post("/internal/v1/watches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Malformed request body"));
    }

    @Test
    void shouldUnregisterWatchOnDelete() throws Exception {
        mockMvc.perform(delete("/internal/v1/watches/" + UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteWithAnInvalidPathUuidReturnsProblemJsonBadRequest() throws Exception {
        mockMvc.perform(delete("/internal/v1/watches/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Malformed request parameter"));
    }

    @Test
    void deleteForAnUnknownWatchIdReturnsProblemJsonNotFound() throws Exception {
        UUID watchId = UUID.randomUUID();
        doThrow(new WatchNotFoundException(watchId)).when(watchService).unregister(watchId);

        mockMvc.perform(delete("/internal/v1/watches/" + watchId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Watch not found"));
    }
}
