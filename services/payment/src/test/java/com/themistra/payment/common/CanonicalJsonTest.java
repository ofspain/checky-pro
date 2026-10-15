package com.themistra.payment.common;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T16 (`design.md` O4, L7). Unit tests for the shared canonical-JSON serializer — no Spring
 * context, pure functions throughout.
 */
class CanonicalJsonTest {

    @Test
    void shouldProduceIdenticalOutputRegardlessOfKeyInsertionOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", "two");
        first.put("a", "one");
        first.put("c", "three");

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("c", "three");
        second.put("a", "one");
        second.put("b", "two");

        assertThat(CanonicalJson.serialize(first)).isEqualTo(CanonicalJson.serialize(second));
    }

    @Test
    void shouldProduceNoPrettyPrintingWhitespace() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("a", "one");
        payload.put("b", "two");

        String json = CanonicalJson.serialize(payload);

        assertThat(json).isEqualTo("{\"a\":\"one\",\"b\":\"two\"}");
    }

    @Test
    void shouldIncludeNullValuedKeyAsLiteralJsonNull() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("a", "one");
        payload.put("b", null);

        String json = CanonicalJson.serialize(payload);

        assertThat(json).isEqualTo("{\"a\":\"one\",\"b\":null}");
    }

    @Test
    void shouldSortNestedMapKeys() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("z", "last");
        nested.put("a", "first");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("outer", nested);

        String json = CanonicalJson.serialize(payload);

        assertThat(json).isEqualTo("{\"outer\":{\"a\":\"first\",\"z\":\"last\"}}");
    }

    @Test
    void shouldPreserveListElementOrderWithoutSorting() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", List.of("zebra", "apple", "mango"));

        String json = CanonicalJson.serialize(payload);

        assertThat(json).isEqualTo("{\"items\":[\"zebra\",\"apple\",\"mango\"]}");
    }

    /** Phase 11 Gap 4 (Kimi, real pass): map-inside-a-list is the one remaining combination in
     * the supported type grammar - {@code shouldSortNestedMapKeys} covers maps-inside-maps,
     * {@code shouldPreserveListElementOrderWithoutSorting} covers scalars-inside-lists, but
     * neither covers this. */
    @Test
    void shouldSortMapKeysInsideAList() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("z", 1);
        nested.put("a", 2);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", List.of(nested));

        String json = CanonicalJson.serialize(payload);

        assertThat(json).isEqualTo("{\"items\":[{\"a\":2,\"z\":1}]}");
    }

    /** Phase 11 Gap 5 (Kimi, real pass, empty-collection half - the Boolean half was already
     * closed by {@code shouldAcceptBooleanValue} below, added during an earlier, since-reverted
     * self-produced pass and kept per the task's own real test content). */
    @Test
    void shouldSerializeEmptyMapAndEmptyListValues() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("emptyMap", Map.of());
        payload.put("emptyList", List.of());

        String json = CanonicalJson.serialize(payload);

        assertThat(json).isEqualTo("{\"emptyList\":[],\"emptyMap\":{}}");
    }

    @Test
    void shouldRejectUuidValue() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", UUID.randomUUID());

        assertThatThrownBy(() -> CanonicalJson.serialize(payload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInstantValue() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("at", java.time.Instant.now());

        assertThatThrownBy(() -> CanonicalJson.serialize(payload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBigDecimalValue() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", new java.math.BigDecimal("1.0"));

        assertThatThrownBy(() -> CanonicalJson.serialize(payload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectFloatValue() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("value", 1.0f);

        assertThatThrownBy(() -> CanonicalJson.serialize(payload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDoubleValueNestedInsideAList() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("values", List.of(1.0d));

        assertThatThrownBy(() -> CanonicalJson.serialize(payload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Phase 11 Gap 1 (self-produced): {@code Boolean} is an explicitly supported type with no
     * prior positive test. */
    @Test
    void shouldAcceptBooleanValue() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("flag", Boolean.TRUE);

        String json = CanonicalJson.serialize(payload);

        assertThat(json).isEqualTo("{\"flag\":true}");
    }

    /** Phase 11 Gap 2 (self-produced): the class's own Javadoc explicitly names
     * {@code BigInteger} as rejected, but no test previously verified it. */
    @Test
    void shouldRejectBigIntegerValue() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("value", java.math.BigInteger.TEN);

        assertThatThrownBy(() -> CanonicalJson.serialize(payload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Phase 11 Gap 3 (self-produced): an earlier Phase 8 review claimed arrays are rejected
     * without ever being tested - verified empirically here. */
    @Test
    void shouldRejectArrayValue() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("values", new String[] {"a", "b"});

        assertThatThrownBy(() -> CanonicalJson.serialize(payload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptIntegerLongShortByteNumberValues() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("anInt", 1);
        payload.put("aLong", 2L);
        payload.put("aShort", (short) 3);
        payload.put("aByte", (byte) 4);

        String json = CanonicalJson.serialize(payload);

        assertThat(json).isEqualTo("{\"aByte\":4,\"aLong\":2,\"aShort\":3,\"anInt\":1}");
    }

    @Test
    void shouldRejectNullPayload() {
        assertThatThrownBy(() -> CanonicalJson.serialize(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNonStringNestedMapKey() {
        Map<Object, Object> nested = new LinkedHashMap<>();
        nested.put(1, "one");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("outer", nested);

        assertThatThrownBy(() -> CanonicalJson.serialize(payload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullNestedMapKey() {
        Map<Object, Object> nested = new LinkedHashMap<>();
        nested.put(null, "one");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("outer", nested);

        assertThatThrownBy(() -> CanonicalJson.serialize(payload))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
