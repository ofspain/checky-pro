package com.themistra.crypto.lookup;

import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.common.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Crypto task 30 (R29, L16): the HTTP shape. Scope enforcement is the URL rule for
 * {@code /internal/v1/**} plus the handler's {@code @PreAuthorize}, both covered elsewhere. */
@WebMvcTest(controllers = TransactionLookupController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({TransactionLookupExceptionHandler.class, ApiExceptionHandler.class})
class TransactionLookupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionLookupService lookupService;

    @Test
    void returnsTheAgreedFactsWithTheAmountAsADecimalString() throws Exception {
        when(lookupService.lookup(Chain.ETHEREUM, "0xabc")).thenReturn(new TransactionLookupResponse(
                "ETHEREUM", "0xabc", "AGREED", 12, "0xToken", "0xFrom", "0xTo", "1000000", "AGREED"));

        mockMvc.perform(get("/internal/v1/transactions/ETHEREUM/0xabc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("AGREED"))
                .andExpect(jsonPath("$.amount").isString())
                .andExpect(jsonPath("$.amount").value("1000000"));
    }

    @Test
    void unanimousAbsenceIsADetailFree404() throws Exception {
        when(lookupService.lookup(any(), anyString())).thenThrow(new TransactionNotFoundException());

        mockMvc.perform(get("/internal/v1/transactions/TRON/abc123"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(not(containsString("providers agree"))));
    }

    @Test
    void anUnavailableProviderIsA503WithoutTheProviderName() throws Exception {
        when(lookupService.lookup(any(), anyString()))
                .thenThrow(new ProvidersUnavailableException("provider fake-ethereum-provider-b did not answer"));

        mockMvc.perform(get("/internal/v1/transactions/ETHEREUM/0xabc"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("fake-ethereum-provider-b"))));
    }

    @Test
    void anUnknownChainIsA400AndNoProviderIsAsked() throws Exception {
        mockMvc.perform(get("/internal/v1/transactions/BITCOIN/0xabc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(lookupService);
    }

    @Test
    void aMalformedHashIsA400AndNoProviderIsAsked() throws Exception {
        mockMvc.perform(get("/internal/v1/transactions/ETHEREUM/0x-abc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(lookupService);
    }
}
