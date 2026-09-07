package com.themistra.crypto.watch;

import com.themistra.crypto.token.AddressValidator;
import com.themistra.crypto.watch.dto.RegisterWatchRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/** Register/unregister a watch (R18/R19) - the only writer of {@link Watch}/{@link ChainCursor}. */
@Service
public class WatchService {

    /** Positive, scale-0 integer only (Phase 3 Finding 2) - matches {@code expected_amount NUMERIC(78,
     * 0)}. No leading zero (so "0" itself, and any zero-value amount, is rejected by requiring the
     * first digit to be 1-9), no decimal point, no exponent - "1.5"/"1e18"/"-0" are all rejected. */
    private static final Pattern EXPECTED_AMOUNT_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private static final String ETHEREUM = "ETHEREUM";

    private final WatchRepository watchRepository;
    private final ChainCursorRepository chainCursorRepository;
    private final AddressValidator addressValidator;
    private final Clock clock;

    public WatchService(WatchRepository watchRepository, ChainCursorRepository chainCursorRepository,
            AddressValidator addressValidator, Clock clock) {
        this.watchRepository = watchRepository;
        this.chainCursorRepository = chainCursorRepository;
        this.addressValidator = addressValidator;
        this.clock = clock;
    }

    /** R18: validates, then persists a {@link Watch} and its placeholder {@link ChainCursor}
     * atomically. {@code watchId} is generated here, never left to the database. */
    @Transactional
    public Watch register(RegisterWatchRequest request) {
        BigDecimal expectedAmount = parseExpectedAmount(request.expectedAmount());
        validateExpiresAt(request.expiresAt());
        validateAddress(request.chain(), request.address());
        validateAddress(request.chain(), request.tokenContractAddress());

        Instant now = clock.instant();
        UUID watchId = UUID.randomUUID();
        Watch watch = Watch.register(watchId, request.invoiceUuid(), request.chain(), request.address(),
                request.tokenContractAddress(), expectedAmount, request.expiresAt(), now);
        watchRepository.save(watch);
        chainCursorRepository.save(ChainCursor.placeholder(request.chain(), watchId, now));
        return watch;
    }

    /** R19: {@code 404} (via {@link WatchNotFoundException}) if {@code watchId} matches no row at all;
     * otherwise an atomic conditional {@code UPDATE} (race-safe, Phase 3 Finding 4) transitions a
     * {@code REGISTERED} watch to {@code UNREGISTERED} and is a no-op for any other status. */
    @Transactional
    public void unregister(UUID watchId) {
        if (!watchRepository.existsByWatchId(watchId)) {
            throw new WatchNotFoundException(watchId);
        }
        watchRepository.markUnregisteredIfRegistered(watchId, clock.instant());
    }

    private BigDecimal parseExpectedAmount(String raw) {
        if (!EXPECTED_AMOUNT_PATTERN.matcher(raw).matches()) {
            throw new InvalidWatchRequestException(
                    "expectedAmount must be a positive integer decimal string in token base units");
        }
        return new BigDecimal(raw);
    }

    private void validateExpiresAt(Instant expiresAt) {
        if (!expiresAt.isAfter(clock.instant())) {
            throw new InvalidWatchRequestException("expiresAt must be in the future");
        }
    }

    /** {@code chain} is already bean-validated to exactly {@code ETHEREUM}/{@code TRON}
     * (`RegisterWatchRequest`'s own {@code @Pattern}) before this ever runs - no further chain-value
     * guard is added here (this codebase's own established discipline against unprecedented
     * defense-in-depth for an invariant already enforced upstream, e.g. T14 Phase 3/8/11). */
    private void validateAddress(String chain, String address) {
        boolean valid = ETHEREUM.equals(chain)
                ? addressValidator.isValidEvmAddress(address)
                : addressValidator.isValidTronAddress(address);
        if (!valid) {
            throw new InvalidWatchRequestException("address is not a valid " + chain + " address");
        }
    }
}
