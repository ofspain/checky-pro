package com.themistra.payment.common;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * T16 (`design.md` O4, L7). A deterministic, reusable canonical-JSON serializer — one
 * canonicalizer shared by {@code ReceiptDigest} (this task) and {@code LedgerService} (task
 * 19), rather than two independently-written ones that could silently drift apart. Keys are
 * sorted lexicographically at every nesting level; list element order is preserved, never
 * sorted; the output has no insignificant whitespace (Jackson's own default, non-pretty-printing
 * writer); every key present in the input is present in the output, including a {@code null}
 * value (Jackson's own default {@code Map} serialization never omits a null entry).
 *
 * <p><b>Supported value types only (Phase 3/4, Kimi Finding 1).</b> {@code serialize} validates,
 * recursively, that every value is one of {@code String}, {@code Boolean}, {@code null}, a safe
 * integer {@code Number} ({@code Short}/{@code Integer}/{@code Long}/{@code Byte} — never {@code
 * Float}/{@code Double}/{@code BigDecimal}/{@code BigInteger}), a nested {@code
 * Map<String,Object>}, or a {@code List<Object>}. Anything else throws {@link
 * IllegalArgumentException}. This is deliberate: a caller passing a raw {@code UUID}/{@code
 * Instant}/{@code BigDecimal} would otherwise silently depend on Jackson's own version-sensitive
 * default serialization for that type, undermining the entire point of a reproducible digest.
 * Callers (e.g. {@code ReceiptDigest}) convert such values to their canonical string/number form
 * themselves, before calling this method — this class deliberately registers no {@code
 * JavaTimeModule} or UUID-specific module, so a digest's own byte-for-byte reproducibility never
 * depends on Jackson's own evolving defaults for those types.</p>
 *
 * <p><b>ASCII scope (Phase 3/4 Finding 4, corrected at Phase 9 - Kimi Phase 8 New Finding 2).</b>
 * This class is intended for ASCII-only keys and values. Jackson's {@code ESCAPE_NON_ASCII}
 * feature is disabled by default (confirmed directly against {@code jackson-core}'s own
 * {@code JsonWriteFeature} source - {@code ESCAPE_NON_ASCII(false)}), so a default-configured
 * {@code ObjectMapper} actually emits raw UTF-8 bytes for non-ASCII characters, not an escaped
 * form - the opposite of this class's own first-drafted claim here, caught during Phase 8's
 * independent review. Every known real payload (O4's receipt fields, the ledger entry payload)
 * is ASCII-only (UUIDs, hex/addresses, chain names, RFC3339 timestamps), so this has no practical
 * effect today either way - but if a future payload needs a specific non-ASCII canonicalization
 * policy, that would need an explicit decision (not this class's own current silent default) and
 * a dedicated test.</p>
 */
public final class CanonicalJson {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CanonicalJson() {
    }

    public static String serialize(Map<String, Object> payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        try {
            return OBJECT_MAPPER.writeValueAsString(canonicalize(payload));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize canonical JSON", e);
        }
    }

    private static Object canonicalize(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Byte) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException(
                            "Unsupported canonical JSON map key type: "
                                    + (key == null ? "null" : key.getClass().getName()));
                }
                sorted.put(stringKey, canonicalize(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> canonicalized = new ArrayList<>(list.size());
            for (Object element : list) {
                canonicalized.add(canonicalize(element));
            }
            return canonicalized;
        }
        throw new IllegalArgumentException(
                "Unsupported canonical JSON value type: " + value.getClass().getName());
    }
}
