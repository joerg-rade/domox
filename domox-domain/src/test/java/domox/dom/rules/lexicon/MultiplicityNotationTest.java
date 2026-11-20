package domox.dom.rules.lexicon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MultiplicityNotation} — the translation of the raw determiner / number token the
 * multiplicity rules (TDR25, TDR26) fire on into UML multiplicity notation.
 *
 * <p>The "raw token" column of every case below is a token actually seen in the corpus evidence
 * (see the TDR25/TDR26 rule matches in {@code domox.rulematch}).</p>
 */
class MultiplicityNotationTest {

    @ParameterizedTest
    @CsvSource({
            // exact counts — digits, with and without the parser's zero padding
            "1,      1",
            "2,      2",
            "01,     1",
            "02,     2",
            "03,     3",
            "04,     4",
            "10,     10",
            "15,     15",
            "000,    0",
            // exact counts — number words
            "one,    1",
            "two,    2",
            "ten,    10",
            "twenty, 20",
            "zero,   0",
            // singular determiners
            "a,      1",
            "an,     1",
            "A,      1",
            // absence
            "no,     0",
            "none,   0",
            // plural / universal determiners -> 0..*
            "each,   *",
            "every,  *",
            "all,    *",
            "any,    *",
            "many,   *",
            "multiple, *",
            "several, *",
            "more,   *",
            "most,   *",
            // "some" asserts at least one
            "some,   1..*"
    })
    void umlNotation_translatesTheTokenVocabulary(final String token, final String expected) {
        assertThat(MultiplicityNotation.umlNotation(token)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "2..4,              2..4",
            "2...4,             2..4",
            "2-4,               2..4",
            "2 to 4,            2..4",
            "between 2 and 4,   2..4",
            "02..04,            2..4",
            "2..*,              2..*",
            "0..*,              0..*",
            "0..1,              0..1",
            "2+,                2..*",
            "at least 2,        2..*",
            "minimum of 2,      2..*",
            "at most 2,         0..2",
            "up to 2,           0..2",
            "no more than 2,    0..2"
    })
    void umlNotation_translatesRangesAndBoundedPhrases(final String token, final String expected) {
        assertThat(MultiplicityNotation.umlNotation(token)).isEqualTo(expected);
    }

    /** UML requires {@code lower <= upper}, and {@code n..n} is the single count. */
    @ParameterizedTest
    @CsvSource({
            "4..2,   2..4",
            "2..2,   2",
            "01..01, 1",
            "*..2,   *"
    })
    void umlNotation_canonicalisesDegenerateRanges(final String token, final String expected) {
        assertThat(MultiplicityNotation.umlNotation(token)).isEqualTo(expected);
    }

    /** A value already in UML notation must survive a second pass unchanged. */
    @ParameterizedTest
    @ValueSource(strings = {"*", "0..*", "1..*", "0..1", "1", "2", "2..4"})
    void umlNotation_isIdempotent(final String umlValue) {
        assertThat(MultiplicityNotation.umlNotation(umlValue)).isEqualTo(umlValue);
    }

    /**
     * A token that carries no usable count must never become a fabricated count — UML's unbounded
     * notation is the only honest answer.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "??", "n", "N", "foo bar baz"})
    void umlNotation_fallsBackToUnbounded(final String token) {
        assertThat(MultiplicityNotation.umlNotation(token)).isEqualTo(MultiplicityNotation.UNBOUNDED);
    }

    @Test
    void unbounded_isUmlZeroToMany() {
        assertThat(MultiplicityNotation.UNBOUNDED).isEqualTo("*");
        assertThat(MultiplicityNotation.RANGE_SEPARATOR).isEqualTo("..");
    }

    /** The fire vocabulary of TDR26 must map to a UML value — that is why it lives here. */
    @ParameterizedTest
    @ValueSource(strings = {"each", "every", "all", "any", "many", "multiple", "some", "Each", "SOME"})
    void isQuantifyingDeterminer_recognisesTheFireVocabulary(final String token) {
        assertThat(MultiplicityNotation.isQuantifyingDeterminer(token)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "an", "the", "this", "one", "", "somewhat"})
    void isQuantifyingDeterminer_rejectsEverythingElse(final String token) {
        assertThat(MultiplicityNotation.isQuantifyingDeterminer(token)).isFalse();
    }

    @Test
    void isQuantifyingDeterminer_isNullSafe() {
        assertThat(MultiplicityNotation.isQuantifyingDeterminer(null)).isFalse();
        assertThat(MultiplicityNotation.isSingularDeterminer(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "an", "A", "An"})
    void isSingularDeterminer_recognisesTheIndefiniteArticles(final String token) {
        assertThat(MultiplicityNotation.isSingularDeterminer(token)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"the", "one", "some", "each", ""})
    void isSingularDeterminer_rejectsEverythingElse(final String token) {
        assertThat(MultiplicityNotation.isSingularDeterminer(token)).isFalse();
    }
}
