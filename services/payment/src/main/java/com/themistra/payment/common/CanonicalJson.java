package com.themistra.payment.common;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * <p><b>ASCII scope (Phase 3/4, Kimi Finding 4).</b> This class relies on Jackson's default
 * {@code ObjectMapper} behavior, which escapes non-ASCII characters as backslash-u-prefixed
 * hex sequences. Every
 * known real payload (O4's receipt fields, the ledger entry payload) is ASCII-only (UUIDs,
 * hex/addresses, chain names, RFC3339 timestamps), so this is harmless today. If a future
 * payload needs raw UTF-8 non-ASCII bytes in the canonical output, this would need an explicit
 * {@code JsonGenerator.Feature.ESCAPE_NON_ASCII} override and a dedicated test — not done here,
 * since no real payload needs it yet.</p>
 */
public final class CanonicalJson {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CanonicalJson() {
    }

    public static String serialize(Map<String, Object> payload) {
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
                sorted.put((String) entry.getKey(), canonicalize(entry.getValue()));
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
