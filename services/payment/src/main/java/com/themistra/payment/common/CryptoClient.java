package com.themistra.payment.common;

import com.themistra.payment.common.config.CryptoClientProperties;
import com.themistra.payment.common.crypto.RegisterWatchRequest;
import com.themistra.payment.common.crypto.RegisterWatchResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The outbound REST client to the Crypto Service (design.md §4c, L3) — this service's first
 * inter-service authenticated HTTP call (T06). Only {@code registerWatch} is this task's own
 * scope; {@code attest}/{@code unregisterWatch} land in later tasks.
 *
 * <p>Deliberately never called from inside {@code InvoiceService.create}'s own
 * {@code @Transactional} method (Phase 3 findings 1/7, Phase 4 disposition) — {@code
 * InvoiceController} calls this client before opening any transaction.</p>
 */
@Component
public class CryptoClient {

    private final RestClient restClient;
    private final String watchPath;

    public CryptoClient(RestClient.Builder restClientBuilder, CryptoServiceTokenProvider tokenProvider,
                         CryptoClientProperties properties) {
        this.watchPath = properties.watchPath();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeoutMs());
        requestFactory.setReadTimeout(properties.readTimeoutMs());

        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenProvider.getAccessToken());
                    return execution.execute(request, body);
                })
                .build();
    }

    public RegisterWatchResponse registerWatch(UUID invoiceUuid, String chain, String address,
                                                String tokenContractAddress, BigDecimal expectedAmount,
                                                Instant expiresAt) {
        RegisterWatchRequest request = new RegisterWatchRequest(invoiceUuid, chain, address,
                tokenContractAddress, expectedAmount.toPlainString(), expiresAt);

        try {
            RegisterWatchResponse response = restClient.post()
                    .uri(watchPath)
                    .body(request)
                    .retrieve()
                    .body(RegisterWatchResponse.class);

            if (response == null || response.watchId() == null) {
                throw new CryptoServiceUnavailableException(
                        "Crypto Service returned no watchId for invoice " + invoiceUuid, null);
            }
            return response;
        } catch (RestClientException e) {
            throw new CryptoServiceUnavailableException(
                    "Failed to register a watch with Crypto Service for invoice " + invoiceUuid, e);
        }
    }
}
