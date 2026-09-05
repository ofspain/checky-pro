package com.themistra.crypto.token;

import org.springframework.stereotype.Component;
import org.tron.trident.utils.Base58Check;
import org.web3j.crypto.Keys;

import java.util.regex.Pattern;

/**
 * Address validation at the boundary (L8): EIP-55 for EVM (Ethereum), Base58Check for Tron. A
 * stateless `@Component` - no persistence, no injected dependency, but still a proper Spring bean for
 * eventual injection at a future call site (mirrors {@link TokenValidator}'s own precedent). Both
 * methods are `boolean` predicates, never throwing - a future caller decides how to react to `false`
 * (an HTTP 4xx, a different domain exception, a log line); this class only supplies the predicate
 * (L8's own "rejected at the boundary" is not fully enforced until some future task wires this in and
 * actually rejects - no such caller exists yet in this codebase, the same "no real caller yet" pattern
 * every prior task in this session has had).
 *
 * <p><b>Casing mismatch with T11's allowlist (Phase 3 Kimi Issue 2) - a forward-looking note, not a
 * bug here.</b> {@link #isValidEvmAddress} requires a correctly-checksummed (mixed-case) address;
 * T11's {@code TokenAllowlist}/{@code TokenValidator} store EVM addresses lowercase and match
 * exact-string with no case-folding. A future caller chaining "validate, then look up in the
 * allowlist" must lowercase the address after successful validation here and before that lookup, or
 * every legitimately-valid checksummed address will incorrectly report {@code UNKNOWN_TOKEN}. This
 * class performs no such normalization itself.</p>
 */
@Component
public class AddressValidator {

    /** Literal lowercase `0x` prefix only (Phase 3 Kimi Issue 4) - `0X...` is rejected, not treated as
     * an alternate valid prefix; deliberately NOT compiled with {@code Pattern.CASE_INSENSITIVE},
     * which would also case-fold the prefix itself. Fully anchored ({@code ^...$}), so leading/trailing
     * whitespace (Phase 3 Kimi Issue 7) always causes rejection - never trimmed. */
    private static final Pattern EVM_ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    /** Tron mainnet address prefix byte - mirrors {@code TronAdapter.ADDRESS_PREFIX}'s identical,
     * {@code private} value (T07, adapter/tron/) exactly; kept as a separate local constant per L15
     * module boundaries rather than reaching into that frozen file. If Tron's launch-scoped mainnet
     * prefix ever changes, both constants must be updated together. */
    private static final byte TRON_MAINNET_PREFIX = 0x41;
    private static final int TRON_DECODED_LENGTH = 21;

    /** Defensive bound (Phase 3 Kimi Issue 5) before invoking `Base58Check` - real Tron addresses are
     * ~34 characters; 64 is a generous margin against wasted decode work on a pathologically long
     * string, not a tight spec-derived value. */
    private static final int MAX_TRON_ADDRESS_LENGTH = 64;

    /** Phase 9 (self-review Finding 1 / Kimi Phase 8 Issues 1 and 3): real Tron addresses are 34
     * characters; anything under 25 can never decode to a valid 21-byte payload plus 4-byte checksum
     * and is rejected before ever reaching {@code Base58Check} - this also sidesteps a confirmed bug
     * in that library, where an input decoding to fewer than 4 raw bytes (empirically, any string
     * under 5 characters) throws {@code NegativeArraySizeException} rather than the
     * {@code IllegalArgumentException} every other invalid input produces. */
    private static final int MIN_TRON_ADDRESS_LENGTH = 25;

    public boolean isValidEvmAddress(String address) {
        if (address == null || !EVM_ADDRESS_PATTERN.matcher(address).matches()) {
            return false;
        }
        // Amendment #1: strict EIP-55 - an all-lowercase or all-uppercase address fails this equality
        // check and is rejected, not accepted as an unchecksummed fallback.
        return address.equals(Keys.toChecksumAddress(address));
    }

    public boolean isValidTronAddress(String address) {
        if (address == null || address.length() < MIN_TRON_ADDRESS_LENGTH
                || address.length() > MAX_TRON_ADDRESS_LENGTH) {
            return false;
        }
        try {
            byte[] decoded = Base58Check.base58ToBytes(address);
            return decoded.length == TRON_DECODED_LENGTH && decoded[0] == TRON_MAINNET_PREFIX;
        } catch (RuntimeException e) {
            // Phase 9 (self-review Finding 1 / Kimi Phase 8 Issue 1): broadened from
            // IllegalArgumentException - Base58Check itself throws that for an illegal character or a
            // checksum mismatch, but the MIN_TRON_ADDRESS_LENGTH guard above is what actually prevents
            // its own confirmed NegativeArraySizeException bug for very short input from ever being
            // reached; this catch stays broad as defense-in-depth against any other unenumerated
            // exception shape this third-party library might throw for input this review didn't
            // specifically test.
            return false;
        }
    }
}
