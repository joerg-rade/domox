package domox.dom.nlp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TdTypeTest {

    @Test
    void everyConstantRoundTripsThroughFromCode() {
        for (final TdType type : TdType.values()) {
            assertThat(TdType.fromCode(type.getCode())).as("failed for code '" + type.getCode() + "'").isEqualTo(type);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "clf,        CLF",
            "discourse,  DISCOURSE",
            "dislocated, DISLOCATED",
            "expl,       EXPL",
            "flat,       FLAT",
            "flat:name,  FLAT_NAME",
            "goeswith,   GOESWITH",
            "list,       LIST",
            "orphan,     ORPHAN",
            "reparandum, REPARANDUM",
            "vocative,   VOCATIVE"
    })
    void fromCode_resolvesRecentlyAddedUniversalRelations(final String code, final TdType expected) {
        assertThat(TdType.fromCode(code)).isEqualTo(expected);
    }
}