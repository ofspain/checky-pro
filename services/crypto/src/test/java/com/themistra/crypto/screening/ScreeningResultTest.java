package com.themistra.crypto.screening;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** AC4 (schema fidelity): {@code create(...)} rejects {@code null} for every non-nullable parameter and
 * accepts {@code null} for the DDL's own nullable columns ({@code tx_hash}, {@code raw_response}). */
class ScreeningResultTest {

    private static final Instant SCREENED_AT = Instant.parse("2026-09-12T00:00:00Z");

    @Test
    void createRejectsNullChain() {
        assertThatNullPointerException()
                .isThrownBy(() -> ScreeningResult.create(null, "0xaddr", "0xtx",
                        ScreeningOutcome.ERROR, "provider", null, SCREENED_AT));
    }

    @Test
    void createRejectsNullAddress() {
        assertThatNullPointerException()
                .isThrownBy(() -> ScreeningResult.create("ETHEREUM", null, "0xtx",
                        ScreeningOutcome.ERROR, "provider", null, SCREENED_AT));
    }

    @Test
    void createRejectsNullOutcome() {
        assertThatNullPointerException()
                .isThrownBy(() -> ScreeningResult.create("ETHEREUM", "0xaddr", "0xtx",
                        null, "provider", null, SCREENED_AT));
    }

    @Test
    void createRejectsNullProvider() {
        assertThatNullPointerException()
                .isThrownBy(() -> ScreeningResult.create("ETHEREUM", "0xaddr", "0xtx",
                        ScreeningOutcome.ERROR, null, null, SCREENED_AT));
    }

    @Test
    void createRejectsNullScreenedAt() {
        assertThatNullPointerException()
                .isThrownBy(() -> ScreeningResult.create("ETHEREUM", "0xaddr", "0xtx",
                        ScreeningOutcome.ERROR, "provider", null, null));
    }

    @Test
    void createAcceptsNullTxHashAndNullRawResponse() {
        ScreeningResult result = ScreeningResult.create("ETHEREUM", "0xaddr", null,
                ScreeningOutcome.ERROR, "provider", null, SCREENED_AT);

        assertThat(result.txHash()).isNull();
        assertThat(result.rawResponse()).isNull();
    }

    @Test
    void accessorsReturnExactlyWhatWasPassedIn() {
        ScreeningResult result = ScreeningResult.create("TRON", "Taddr", "0xtx",
                ScreeningOutcome.BLOCKED, "chainalysis", "{\"hit\":true}", SCREENED_AT);

        assertThat(result.chain()).isEqualTo("TRON");
        assertThat(result.address()).isEqualTo("Taddr");
        assertThat(result.txHash()).isEqualTo("0xtx");
        assertThat(result.outcome()).isEqualTo(ScreeningOutcome.BLOCKED);
        assertThat(result.provider()).isEqualTo("chainalysis");
        assertThat(result.rawResponse()).isEqualTo("{\"hit\":true}");
        assertThat(result.screenedAt()).isEqualTo(SCREENED_AT);
    }

    // Phase 11 (Kimi) Gap 8: no existing test asserted the entity's structural immutability directly -
    // a future refactor adding a setter would not fail any test that only exercises create(...) and
    // the accessors it already produces correctly.
    @Test
    void exposesNoPublicSetterMethods() {
        boolean hasSetter = Arrays.stream(ScreeningResult.class.getDeclaredMethods())
                .anyMatch(method -> Modifier.isPublic(method.getModifiers())
                        && method.getName().startsWith("set"));

        assertThat(hasSetter).as("ScreeningResult must expose no public setter").isFalse();
    }

    @Test
    void everyFieldIsPrivate() {
        boolean hasNonPrivateField = Arrays.stream(ScreeningResult.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(Field::getModifiers)
                .anyMatch(modifiers -> !Modifier.isPrivate(modifiers));

        assertThat(hasNonPrivateField).as("every ScreeningResult field must be private").isFalse();
    }
}
