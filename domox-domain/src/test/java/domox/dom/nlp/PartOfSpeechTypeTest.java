package domox.dom.nlp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class PartOfSpeechTypeTest {

    @ParameterizedTest
    @MethodSource("knownPennTreebankTags")
    void fromCode_resolvesAllKnownPennTreebankTags(final String code, final PartOfSpeechType expected) {
        assertThat(PartOfSpeechType.fromCode(code)).isEqualTo(expected);
    }

    /** The full set of Penn Treebank tags the enum maps, paired with the code that resolves to it. */
    static Stream<Arguments> knownPennTreebankTags() {
        return Stream.of(
                arguments("FW", PartOfSpeechType.FW),
                arguments("``", PartOfSpeechType.LQUOTE),
                arguments("''", PartOfSpeechType.RQUOTE),
                arguments("SYM", PartOfSpeechType.SYM),
                arguments("-LRB-", PartOfSpeechType.LRB),
                arguments("-RRB-", PartOfSpeechType.RRB),
                arguments("RP", PartOfSpeechType.RP),
                arguments("RBS", PartOfSpeechType.RBS),
                arguments("POS", PartOfSpeechType.POS));
    }

    @Test
    void fromCode_trimsWhitespace() {
        assertThat(PartOfSpeechType.fromCode("  NN  ")).isEqualTo(PartOfSpeechType.NN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void fromCode_returnsNullForNullAndBlank(final String code) {
        assertThat(PartOfSpeechType.fromCode(code)).isNull();
    }

    @Test
    void fromCode_returnsNullForUnknown() {
        assertThat(PartOfSpeechType.fromCode("NOT_A_TAG")).isNull();
    }
}