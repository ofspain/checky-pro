package com.themistra.payment.common;

import com.themistra.payment.common.config.CryptoClientProperties;
import com.themistra.payment.common.crypto.AttestRequest;
import com.themistra.payment.common.crypto.AttestResponse;
import com.themistra.payment.common.crypto.AttestationResult;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * The outbound REST client to the Crypto Service (design.md §4c, L3) — this service's first
 * inter-service authenticated HTTP call (T06). {@code unregisterWatch} added at T08 (invoice
 * expiry sweep); {@code attest} added at T15.
 *
 * <p>Deliberately never called from inside {@code InvoiceService.create}'s own
 * {@code @Transactional} method (Phase 3 findings 1/7, Phase 4 disposition) — {@code
 * InvoiceController} calls this client before opening any transaction.</p>
 *
 * <p><b>Module placement (T15, Phase 2/3):</b> {@code design.md}'s own module-layout diagram and
 * {@code package.md} §4 both describe an {@code attestation} module owning this class — but it
 * has lived in {@code common} since T06, with real callers already in {@code invoice}
 * ({@code InvoiceController}) and {@code consumer}/the expiry sweep job (T08/T11), both predating
 * any reason for an {@code attestation} module to exist. This is shared plumbing used by three
 * different feature modules, which {@code agents.md}'s own "shared plumbing lives only in
 * {@code common}" rule is the more specific, durable fit for — deliberately NOT relocated.</p>
 */
@Component
public class CryptoClient {

    private static final Logger log = LoggerFactory.getLogger(CryptoClient.class);
    private static final String REGISTERED_STATUS = "REGISTERED";
    private static final String SIGNED_OUTCOME = "SIGNED";
    private static final String BLOCKED_OUTCOME = "BLOCKED";

    private final RestClient restClient;
    private final String watchPath;
    private final String attestPath;

    public CryptoClient(RestClient.Builder restClientBuilder, CryptoServiceTokenProvider tokenProvider,
                         CryptoClientProperties properties) {
        this.watchPath = properties.watchPath();
        this.attestPath = properties.attestPath();

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

    /**
     * {@code POST /internal/v1/attest} (R19, T15). Returns {@link AttestationResult.Signed} or
     * {@link AttestationResult.Blocked} for the two real {@code 200} business outcomes — neither
     * is an error. A {@code 409} (the signing gate not satisfied — quorum/finality unmet, or a
     * screening error on the Crypto Service's own side) throws {@link
     * AttestationRefusedException}, carrying the response body's text best-effort (its content is
     * expected to be a fixed, generic string on the real Crypto Service — see that exception's
     * own Javadoc). A {@code 400} (this client sent an invalid digest/chain), any {@code 5xx}, a
     * connection failure, a timeout, or a malformed/unrecognized response body all throw the
     * existing {@link CryptoServiceUnavailableException} (Phase 3, Kimi Finding 4's accepted
     * disposition — this client trusts its own caller and performs no local request validation,
     * so a {@code 400} is treated the same as any other non-contractual failure).
     */
    public AttestationResult attest(String receiptDigestSha256, String chain, String txHash) {
        AttestRequest request = new AttestRequest(receiptDigestSha256, chain, txHash);

        AttestResponse response;
        try {
            response = restClient.post()
                    .uri(attestPath)
                    .body(request)
                    .retrieve()
                    .body(AttestResponse.class);
        } catch (HttpClientErrorException.Conflict e) {
            throw new AttestationRefusedException(e.getResponseBodyAsString(StandardCharsets.UTF_8));
        } catch (RestClientException e) {
            throw new CryptoServiceUnavailableException(
                    "Failed to obtain attestation for " + chain + ":" + txHash, e);
        }

        if (response == null || response.outcome() == null) {
            throw new CryptoServiceUnavailableException(
                    "Crypto Service returned no attest outcome for " + chain + ":" + txHash, null);
        }
        if (SIGNED_OUTCOME.equalsIgnoreCase(response.outcome())) {
            if (response.signature() == null || response.kmsKeyId() == null || response.signedAt() == null) {
                throw new CryptoServiceUnavailableException(
                        "Crypto Service returned SIGNED outcome with missing fields for "
                                + chain + ":" + txHash, null);
            }
            return new AttestationResult.Signed(response.signature(), response.kmsKeyId(), response.signedAt());
        }
        if (BLOCKED_OUTCOME.equalsIgnoreCase(response.outcome())) {
            return new AttestationResult.Blocked(response.reason());
        }
        throw new CryptoServiceUnavailableException(
                "Crypto Service returned unexpected attest outcome '" + response.outcome()
                        + "' for " + chain + ":" + txHash, null);
    }
}
