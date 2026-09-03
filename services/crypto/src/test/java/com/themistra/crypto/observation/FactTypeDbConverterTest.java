package com.themistra.crypto.observation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 11 Gap 9: direct coverage of {@link FactType.DbConverter}, isolated from
 * {@code ObservationLogTest}'s only-indirect exercise of it. AC6. */
class FactTypeDbConverterTest {

    private final FactType.DbConverter converter = new FactType.DbConverter();

    @ParameterizedTest
    @EnumSource(FactType.class)
    void convertsEachFactTypeToItsLowercaseNameAndBack(FactType factType) {
        String dbValue = converter.convertToDatabaseColumn(factType);

        assertThat(dbValue).isEqualTo(factType.name().toLowerCase(java.util.Locale.ROOT));
        assertThat(converter.convertToEntityAttribute(dbValue)).isEqualTo(factType);
    }

    @Test
    void nullMapsToNullInBothDirections() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
