package domox.dom.crc.diagram;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Verifies the node-sizing metrics shared by the lexical diagram renderers.
 *
 * <p>The three constants and the {@code scaleFor} formula used to be duplicated in
 * {@code CandidateD3Graph}, {@code CandidateVegaGraph} and {@code DomainModelDiagramsD3};
 * these tests pin the single-source-of-truth behaviour they must all agree on.</p>
 */
class DiagramNodeSizingTest {

    // ---------------------------------------------------------------- constants

    @Test
    void constants_are_the_documented_values() {
        assertThat(DiagramNodeSizing.NODE_BASE_RADIUS).isEqualTo(8.0);
        assertThat(DiagramNodeSizing.MIN_MATCH_COUNT).isEqualTo(1);
        assertThat(DiagramNodeSizing.MAX_SCALE).isEqualTo(4.0);
    }

    // ---------------------------------------------------------------- scaleFor

    @Test
    void scaleFor_the_minimum_match_count_is_one() {
        assertThat(DiagramNodeSizing.scaleFor(DiagramNodeSizing.MIN_MATCH_COUNT, 10)).isEqualTo(1.0);
    }

    @Test
    void scaleFor_the_largest_count_is_maxScale() {
        assertThat(DiagramNodeSizing.scaleFor(10, 10)).isEqualTo(4.0);
    }

    @Test
    void scaleFor_grows_linearly_between_the_two_ends() {
        // 1 + 3 * (5 - 1) / (9 - 1) = 1 + 1.5 = 2.5
        assertThat(DiagramNodeSizing.scaleFor(5, 9)).isCloseTo(2.5, within(1e-9));
        // 1 + 3 * (3 - 1) / (9 - 1) = 1 + 0.75 = 1.75
        assertThat(DiagramNodeSizing.scaleFor(3, 9)).isCloseTo(1.75, within(1e-9));
    }

    @Test
    void scaleFor_a_count_below_the_minimum_is_clamped_up_to_one() {
        assertThat(DiagramNodeSizing.scaleFor(0, 10)).isEqualTo(1.0);
        assertThat(DiagramNodeSizing.scaleFor(-5, 10)).isEqualTo(1.0);
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "0, 1", "0, 0", "-3, -1", "5, 1"})
    void scaleFor_a_maxCount_at_or_below_the_minimum_yields_one(final int count, final int maxCount) {
        assertThat(DiagramNodeSizing.scaleFor(count, maxCount)).isEqualTo(1.0);
    }

    // ---------------------------------------------------------------- radiusFor

    @Test
    void radiusFor_the_minimum_count_is_the_base_radius() {
        assertThat(DiagramNodeSizing.radiusFor(1, 10)).isEqualTo(8);
    }

    @Test
    void radiusFor_the_largest_count_is_base_radius_times_maxScale() {
        assertThat(DiagramNodeSizing.radiusFor(10, 10)).isEqualTo(32);
    }

    @Test
    void radiusFor_a_single_node_diagram_keeps_the_base_radius() {
        assertThat(DiagramNodeSizing.radiusFor(1, 1)).isEqualTo(8);
    }

    @Test
    void radiusFor_rounds_to_the_nearest_whole_pixel() {
        // 1 + 3 * 1 / 10 = 1.3  →  1.3 * 8  = 10.4  → 10
        assertThat(DiagramNodeSizing.radiusFor(2, 11)).isEqualTo(10);
        // 1 + 3 * 2 / 10 = 1.6  →  1.6 * 8  = 12.8  → 13
        assertThat(DiagramNodeSizing.radiusFor(3, 11)).isEqualTo(13);
    }

    @Test
    void radiusFor_never_shrinks_below_the_base_radius() {
        assertThat(DiagramNodeSizing.radiusFor(0, 100)).isGreaterThanOrEqualTo(8);
    }
}
