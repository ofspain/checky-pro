package com.themistra.crypto.token;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AC1 (identity fields), the append-only entity shape (no mutator beyond construction). */
class TokenAllowlistTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-05T12:00:00Z");

    @Test
    void createPopulatesEveryFieldExactlyAsGiven() {
        TokenAllowlist entry = TokenAllowlist.create("ETHEREUM", "0xabc", "USDT", 6, 1,
                "local-only-unsigned-placeholder", CREATED_AT);

        assertThat(entry.chain()).isEqualTo("ETHEREUM");
        assertThat(entry.contractAddress()).isEqualTo("0xabc");
        assertThat(entry.symbol()).isEqualTo("USDT");
        assertThat(entry.decimals()).isEqualTo((short) 6);
        assertThat(entry.version()).isEqualTo(1);
        assertThat(entry.signature()).isEqualTo("local-only-unsigned-placeholder");
        assertThat(entry.createdAt()).isEqualTo(CREATED_AT);
        assertThat(entry.id()).isNull();
    }

    @Test
    void createRejectsNullArguments() {
        assertThatThrownBy(() -> TokenAllowlist.create(null, "0xabc", "USDT", 6, 1, "sig", CREATED_AT))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("chain");
        assertThatThrownBy(() -> TokenAllowlist.create("ETHEREUM", null, "USDT", 6, 1, "sig", CREATED_AT))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("contractAddress");
        assertThatThrownBy(() -> TokenAllowlist.create("ETHEREUM", "0xabc", null, 6, 1, "sig", CREATED_AT))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("symbol");
        assertThatThrownBy(() -> TokenAllowlist.create("ETHEREUM", "0xabc", "USDT", 6, 1, null, CREATED_AT))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("signature");
        assertThatThrownBy(() -> TokenAllowlist.create("ETHEREUM", "0xabc", "USDT", 6, 1, "sig", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("createdAt");
    }

    @Test
    void createRejectsANegativeDecimals() {
        assertThatThrownBy(() -> TokenAllowlist.create("ETHEREUM", "0xabc", "USDT", -1, 1, "sig", CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decimals");
    }

    @Test
    void createRejectsADecimalsValueExceedingThirty() {
        // Phase 9 (Kimi Phase 8 Issue 10): bounded to 30, not Short.MAX_VALUE.
        assertThatThrownBy(() -> TokenAllowlist.create("ETHEREUM", "0xabc", "USDT", 31, 1, "sig", CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decimals");
    }

    @Test
    void createAcceptsTheBoundaryDecimalsValueOfThirty() {
        TokenAllowlist entry = TokenAllowlist.create("ETHEREUM", "0xabc", "USDT", 30, 1, "sig", CREATED_AT);

        assertThat(entry.decimals()).isEqualTo((short) 30);
    }

    @Test
    void hasNoPublicMutatorBeyondConstruction() {
        // AC1/entity-shape, grant-enforced: crypto_app has INSERT/SELECT only on token_allowlist.
        List<Method> nonGetterPublicMethods = java.util.Arrays.stream(TokenAllowlist.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> m.getParameterCount() > 0 || m.getReturnType() == void.class)
                .toList();

        assertThat(nonGetterPublicMethods).isEmpty();
    }
}
