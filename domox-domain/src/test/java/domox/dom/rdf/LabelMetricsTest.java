package domox.dom.rdf;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Unit-level guardrail for {@link LabelMetrics} — the per-character advance table the box widening fits
 * a class name to.
 * <p>
 * The domain has no font to measure with, so the estimate is a table plus a safety factor.  These tests
 * pin the table entry for every character class, the index arithmetic that selects it, and the fact that
 * the safety factor is applied once to the whole label.
 */
@DisplayName("LabelMetrics — the estimated width of a class label")
class LabelMetricsTest {

    /** The safety factor {@link LabelMetrics} applies to a whole label; stated here as data. */
    private static final double SAFETY = 1.10;

    @Test
    @DisplayName("a null label has no width")
    void nullLabelIsZeroWidth() {
        assertThat(LabelMetrics.estimatedLabelWidth(null)).isZero();
    }

    @Test
    @DisplayName("an empty label has no width")
    void emptyLabelIsZeroWidth() {
        assertThat(LabelMetrics.estimatedLabelWidth("")).isZero();
    }

    static Stream<Arguments> singleCharacterAdvances() {
        return Stream.of(
                // lower-case: the first entry, the widest, and the last — pins the `c - 'a'` index
                arguments("a", 6.7), arguments("m", 10.7), arguments("z", 6.0),
                // upper-case: likewise — pins the `c - 'A'` index
                arguments("A", 8.7), arguments("M", 10.0), arguments("Z", 7.3),
                // a digit, and one of each measured punctuation class
                arguments("5", 6.7), arguments("_", 6.7), arguments(" ", 3.8),
                arguments("(", 4.0), arguments(")", 4.0), arguments("-", 4.0),
                arguments(".", 3.3), arguments(",", 3.3),
                // a character the table does not measure: assumed wide
                arguments("?", 8.0));
    }

    @ParameterizedTest(name = "\"{0}\" advances {1}")
    @MethodSource("singleCharacterAdvances")
    @DisplayName("a single character advances by its own table entry, times the safety factor")
    void singleCharacterUsesItsOwnAdvance(final String label, final double advance) {
        assertThat(LabelMetrics.estimatedLabelWidth(label))
                .isCloseTo(advance * SAFETY, within(1e-6));
    }

    static Stream<Arguments> multiCharacterAdvances() {
        return Stream.of(
                arguments("ab", 6.7 + 7.3),
                arguments("AB", 8.7 + 8.7),
                arguments("Item", 3.3 + 4.0 + 6.7 + 10.7),
                arguments("Cart Item", 8.7 + 6.7 + 4.7 + 4.0 + 3.8 + 3.3 + 4.0 + 6.7 + 10.7));
    }

    @ParameterizedTest(name = "\"{0}\" advances {1}")
    @MethodSource("multiCharacterAdvances")
    @DisplayName("a multi-character label is the sum of its characters' advances")
    void multiCharacterLabelsAddUp(final String label, final double advance) {
        assertThat(LabelMetrics.estimatedLabelWidth(label))
                .isCloseTo(advance * SAFETY, within(1e-6));
    }

    @Test
    @DisplayName("the safety factor is applied once, not compounded per character")
    void safetyFactorIsAppliedOnce() {
        assertThat(LabelMetrics.estimatedLabelWidth("ab"))
                .isCloseTo((6.7 + 7.3) * SAFETY, within(1e-6));
    }

    @Test
    @DisplayName("the box padding and the neighbour gap are the documented sizes")
    void paddingAndGapAreTheDocumentedSizes() {
        assertThat(LabelMetrics.LABEL_PADDING).isEqualTo(14);
        assertThat(LabelMetrics.LABEL_GAP).isEqualTo(8);
    }
}
