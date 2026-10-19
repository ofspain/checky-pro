package com.themistra.notification.consumer;

import java.math.BigDecimal;

/**
 * Turns a payment event's {@code amount} (whole token base units, a decimal string) into the
 * human amount a merchant expects: {@code "1500000"} with 6 decimals reads {@code "1.5"}. Exact
 * {@link BigDecimal} arithmetic only, never a floating-point type (L6). When the token's decimals
 * aren't known, the raw value is shown and labelled as base units rather than guessed.
 */
final class AmountFormatter {

    static final String BASE_UNITS_SUFFIX = " base units";

    private AmountFormatter() {
    }

    /** @return the formatted amount, or {@code null} when {@code baseUnits} is {@code null}. */
    static String format(String baseUnits, Integer tokenDecimals) {
        if (baseUnits == null) {
            return null;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(baseUnits);
        } catch (NumberFormatException e) {
            return baseUnits;
        }
        if (tokenDecimals == null || tokenDecimals < 0) {
            return value.toPlainString() + BASE_UNITS_SUFFIX;
        }
        BigDecimal human = value.movePointLeft(tokenDecimals).stripTrailingZeros();
        return human.toPlainString();
    }
}
