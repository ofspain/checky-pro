package com.themistra.crypto.watch;

import com.themistra.crypto.watch.dto.RegisterWatchRequest;
import com.themistra.crypto.watch.dto.WatchResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Watch registration for the payment service (ARCHITECTURE §4: registration is synchronous intent,
 * observations come back asynchronously over Kafka).
 *
 * <p><strong>This endpoint is currently unauthenticated.</strong> §8 requires JWT validation in
 * every service — "zero trust, required regardless" — and `services/auth` already provisions a
 * {@code crypto-service} client. Wiring the OAuth2 resource server is a separate change that must
 * land before this service is deployed anywhere reachable. Until then, {@code /internal/**} must be
 * refused at the ingress for traffic originating outside the cluster.
 */
@RestController
@RequestMapping("/internal/watches")
public class WatchController {

    private final WatchService watchService;

    public WatchController(WatchService watchService) {
        this.watchService = watchService;
    }

    /**
     * Registers a watch, or returns the existing one for this caller reference.
     *
     * <p>Returns 200 rather than 201 in both cases. A caller retrying after a timeout cannot tell
     * whether its first attempt was received, and should not have to care.
     */
    @PostMapping
    public ResponseEntity<WatchResponse> register(@Valid @RequestBody RegisterWatchRequest request) {
        Watch watch = watchService.register(
                request.callerReference(),
                request.chainId(),
                request.recipientAddress(),
                request.tokenAddress(),
                request.expectedAmount(),
                request.expiresAt());
        return ResponseEntity.ok(WatchResponse.from(watch));
    }

    @GetMapping("/{watchUuid}")
    public WatchResponse get(@PathVariable UUID watchUuid) {
        return WatchResponse.from(watchService.findByUuid(watchUuid));
    }

    @DeleteMapping("/{watchUuid}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID watchUuid) {
        watchService.cancel(watchUuid);
    }
}
