package com.themistra.payment.common;

import com.themistra.payment.common.config.CryptoClientProperties;
import com.themistra.payment.common.crypto.RegisterWatchRequest;
import com.themistra.payment.common.crypto.RegisterWatchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The outbound REST client to the Crypto Service (design.md §4c, L3) — this service's first
 * inter-service authenticated HTTP call (T06). {@code unregisterWatch} added at T08 (invoice
 * expiry sweep); {@code attest} lands in a later task.
 *
 * <p>Deliberately never called from inside {@code InvoiceService.create}'s own
 * {@code @Transactional} method (Phase 3 findings 1/7, Phase 4 disposition) — {@code
 * InvoiceController} calls this client before opening any transaction.</p>
 */
@Component
public class CryptoClient {

    private static final Logger log = LoggerFactory.getLogger(CryptoClient.class);
    private static final String REGISTERED_STATUS = "REGISTERED";

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
            if (!REGISTERED_STATUS.equalsIgnoreCase(response.status())) {
                throw new CryptoServiceUnavailableException(
                        "Crypto Service returned unexpected watch status '" + response.status()
                                + "' for invoice " + invoiceUuid, null);
            }
            return response;
        } catch (RestClientException e) {
            throw new CryptoServiceUnavailableException(
                    "Failed to register a watch with Crypto Service for invoice " + invoiceUuid, e);
        }
    }

    /**
     * {@code DELETE /internal/v1/watches/{watchId}} (R5, T08). The contract's own text says this
     * is idempotent — "also returned for an already-unregistered watch" — but a 204 is not the
     * only shape a real Crypto Service deployment might use to say "already gone"; {@code 404}/
     * {@code 410} are treated as success too (Phase 3 finding 3), so a retried sweep after a
     * prior successful unregister (but a failed DB commit before {@code expireInvoice} ran)
     * never gets permanently stuck.
     */
    public void unregisterWatch(UUID watchId) {
        try {
            restClient.delete()
                    .uri(watchPath + "/{watchId}", watchId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Gone e) {
            log.debug("Watch {} was already unregistered; treating as success", watchId);
        } catch (RestClientException e) {
            throw new CryptoServiceUnavailableException("Failed to unregister watch " + watchId, e);
        }
    }
}
