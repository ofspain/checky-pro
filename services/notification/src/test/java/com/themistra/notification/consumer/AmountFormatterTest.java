package com.themistra.notification.consumer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Notification T07 (frozen brief, engineering addition): base units become human units exactly. */
class AmountFormatterTest {

    @Test
    void shouldShiftBaseUnitsByTheTokensDecimals() {
        assertThat(AmountFormatter.format("1500000", 6)).isEqualTo("1.5");
        assertThat(AmountFormatter.format("1000000", 6)).isEqualTo("1");
        assertThat(AmountFormatter.format("1", 18)).isEqualTo("0.000000000000000001");
        assertThat(AmountFormatter.format("1000", 0)).isEqualTo("1000");
    }

    /** stripTrailingZeros alone would give "1E+3"; toPlainString keeps it readable. */
    @Test
    void shouldNeverUseScientificNotation() {
        assertThat(AmountFormatter.format("1000000000", 6)).isEqualTo("1000");
    }

    /** No precision is lost on values a double could not hold exactly. */
    @Test
    void shouldStayExactForLargeValues() {
        assertThat(AmountFormatter.format("123456789012345678901234567890", 18))
                .isEqualTo("123456789012.34567890123456789");
    }

    @Test
    void shouldLabelTheRawValueWhenDecimalsAreUnknown() {
        assertThat(AmountFormatter.format("1500000", null)).isEqualTo("1500000 base units");
        assertThat(AmountFormatter.format("1500000", -1)).isEqualTo("1500000 base units");
    }

    @Test
    void shouldPassThroughAnUnparseableValueAndNull() {
        assertThat(AmountFormatter.format("not-a-number", 6)).isEqualTo("not-a-number");
        assertThat(AmountFormatter.format(null, 6)).isNull();
    }
}
