package domox.dom.rdf;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import domox.dom.rdf.geometry.Box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit-level guardrail for {@link LabelLayout} — growing a class box until its label fits, so the whole
 * diagram keeps one font size.
 * <p>
 * The end-to-end {@code UmlLayoutDiagramsTest} only sees a widened box as a JSON width, and its fixtures
 * never make a box too narrow for its name — so the widening itself, and every decision it takes about
 * which way to grow, is pinned here against plain {@link Box}es.
 */
@DisplayName("LabelLayout — widening a class box until its label fits")
class LabelLayoutTest {

    /** {@code LabelMetrics.estimatedLabelWidth("A")}, stated as data so the tests pin the widening. */
    private static final double WIDTH_A = 9.57;

    /** The padding kept inside a box around its label — the test's own copy of the documented value. */
    private static final double PADDING = 14;

    /** The width "A" needs beyond a 10-wide box: its own width plus the padding. */
    private static final double SHORTFALL_A_AT_10 = WIDTH_A + PADDING - 10;

    /** The id-to-name lookup the widening fits to: the class name each view draws. */
    private static Function<String, String> nameOf(final Map<String, String> byId) {
        return id -> byId.getOrDefault(id, id);
    }

    /** A mutable box map in a fixed order, so which box is widened first is not left to the hash. */
    private static Map<String, Box> boxes(final String id1, final Box box1) {
        final Map<String, Box> map = new LinkedHashMap<>();
        map.put(id1, box1);
        return map;
    }

    private static Map<String, Box> boxes(final String id1, final Box box1,
                                          final String id2, final Box box2) {
        final Map<String, Box> map = boxes(id1, box1);
        map.put(id2, box2);
        return map;
    }

    private static Map<String, Box> boxes(final String id1, final Box box1,
                                          final String id2, final Box box2,
                                          final String id3, final Box box3) {
        final Map<String, Box> map = boxes(id1, box1, id2, box2);
        map.put(id3, box3);
        return map;
    }

    private static Function<String, String> named(final String id, final String name) {
        return actual -> {
            assertThat(actual).as("the name is looked up by the box's id").isEqualTo(id);
            return name;
        };
    }

    @Test
    @DisplayName("a box wide enough for its label is left exactly as it is")
    void aBoxWideEnoughForItsLabelIsLeftAlone() {
        final Map<String, Box> boxes = boxes("A", new Box(100, 100, 200, 60));

        LabelLayout.widenToFitLabels(boxes, named("A", "A"));

        assertThat(boxes.get("A")).isEqualTo(new Box(100, 100, 200, 60));
    }

    @Test
    @DisplayName("a narrow box grows to the right when nothing is in the way")
    void aNarrowBoxGrowsToTheRight() {
        final Map<String, Box> boxes = boxes("A", new Box(100, 100, 10, 60));

        LabelLayout.widenToFitLabels(boxes, named("A", "A"));

        final Box widened = boxes.get("A");
        assertThat(widened.x()).as("the left edge stays put").isEqualTo(100);
        assertThat(widened.y()).isEqualTo(100);
        assertThat(widened.h()).isEqualTo(60);
        assertThat(widened.w()).isCloseTo(10 + SHORTFALL_A_AT_10, within(1e-6));
    }

    @Test
    @DisplayName("a box grows by exactly the shortfall its label leaves")
    void aBoxGrowsByExactlyTheShortfall() {
        // "Item" needs 3.3 + 4.0 + 6.7 + 10.7 = 24.7 units, times the safety factor, plus the padding
        final Map<String, Box> boxes = boxes("A", new Box(100, 100, 20, 60));

        LabelLayout.widenToFitLabels(boxes, named("A", "Item"));

        final double needed = 24.7 * 1.10 + PADDING;
        assertThat(boxes.get("A").w()).isCloseTo(needed, within(1e-6));
        assertThat(boxes.get("A").w() - 20).as("the growth is exactly the shortfall")
                .isCloseTo(needed - 20, within(1e-6));
    }

    @Test
    @DisplayName("a narrow box grows to the left when the right is blocked")
    void aNarrowBoxGrowsToTheLeftWhenTheRightIsBlocked() {
        final Map<String, Box> boxes = boxes("A", new Box(100, 100, 10, 60),
                "N", new Box(130, 100, 50, 60));

        LabelLayout.widenToFitLabels(boxes, nameOf(Map.of("A", "A", "N", "N")));

        final Box widened = boxes.get("A");
        assertThat(widened.x()).as("the left edge moves out").isCloseTo(100 - SHORTFALL_A_AT_10, within(1e-6));
        assertThat(widened.y()).isEqualTo(100);
        assertThat(widened.h()).isEqualTo(60);
        assertThat(widened.w()).isCloseTo(10 + SHORTFALL_A_AT_10, within(1e-6));
    }

    @Test
    @DisplayName("a box grown to the left keeps its right edge where it was")
    void theRightEdgeStaysPutWhenABoxGrowsToTheLeft() {
        final Map<String, Box> boxes = boxes("A", new Box(100, 100, 10, 60),
                "N", new Box(130, 100, 50, 60));

        LabelLayout.widenToFitLabels(boxes, nameOf(Map.of("A", "A", "N", "N")));

        final Box widened = boxes.get("A");
        assertThat(widened.x() + widened.w()).isCloseTo(110, within(1e-6));
    }

    @Test
    @DisplayName("a box with no room in either direction keeps its width")
    void aBoxWithNoRoomEitherWayKeepsItsWidth() {
        final Map<String, Box> boxes = boxes("A", new Box(100, 100, 10, 60),
                "L", new Box(60, 100, 30, 60),
                "R", new Box(130, 100, 50, 60));

        LabelLayout.widenToFitLabels(boxes, nameOf(Map.of("A", "A", "L", "L", "R", "R")));

        assertThat(boxes.get("A")).as("the client shrinks this one label instead")
                .isEqualTo(new Box(100, 100, 10, 60));
        assertThat(boxes.get("L")).isEqualTo(new Box(60, 100, 30, 60));
        assertThat(boxes.get("R")).isEqualTo(new Box(130, 100, 50, 60));
    }

    @Test
    @DisplayName("a neighbour that does not overlap vertically is not in the way")
    void aNeighbourWithNoVerticalOverlapIsNotInTheWay() {
        final Map<String, Box> boxes = boxes("A", new Box(100, 100, 10, 60),
                "N", new Box(130, 300, 50, 60));

        LabelLayout.widenToFitLabels(boxes, nameOf(Map.of("A", "A", "N", "N")));

        assertThat(boxes.get("A").x()).as("it still grows right").isEqualTo(100);
        assertThat(boxes.get("A").w()).isCloseTo(10 + SHORTFALL_A_AT_10, within(1e-6));
    }

    @Test
    @DisplayName("a neighbour touching the box's bottom edge exactly is not in the way either")
    void aNeighbourTouchingTheBottomEdgeIsNotInTheWay() {
        final Map<String, Box> boxes = boxes("A", new Box(100, 100, 10, 60),
                "N", new Box(130, 160, 50, 60));

        LabelLayout.widenToFitLabels(boxes, nameOf(Map.of("A", "A", "N", "N")));

        assertThat(boxes.get("A").x()).as("it still grows right").isEqualTo(100);
        assertThat(boxes.get("A").w()).isCloseTo(10 + SHORTFALL_A_AT_10, within(1e-6));
    }

    @Test
    @DisplayName("a neighbour overlapping only part of the vertical span still blocks")
    void aPartlyOverlappingNeighbourStillBlocks() {
        final Map<String, Box> boxes = boxes("A", new Box(100, 100, 10, 60),
                "N", new Box(130, 150, 50, 60));

        LabelLayout.widenToFitLabels(boxes, nameOf(Map.of("A", "A", "N", "N")));

        assertThat(boxes.get("A").x()).as("the right is blocked, so it grows left")
                .isCloseTo(100 - SHORTFALL_A_AT_10, within(1e-6));
    }

    @Test
    @DisplayName("a neighbour leaving exactly enough room is grown into, not pushed past")
    void aNeighbourLeavingEnoughRoomIsGrownInto() {
        // 132 - 8 (the gap) - 110 (this box's right edge) = 14 >= the 13.57 shortfall
        final Map<String, Box> boxes = boxes("A", new Box(100, 100, 10, 60),
                "N", new Box(132, 100, 50, 60));

        LabelLayout.widenToFitLabels(boxes, nameOf(Map.of("A", "A", "N", "N")));

        assertThat(boxes.get("A").x()).as("it grows right, into the gap").isEqualTo(100);
        assertThat(boxes.get("A").w()).isCloseTo(10 + SHORTFALL_A_AT_10, within(1e-6));
    }

    @Test
    @DisplayName("a neighbour leaving a unit too little room pushes the box left")
    void aNeighbourLeavingTooLittleRoomPushesTheBoxLeft() {
        // 131 - 8 - 110 = 13 < the 13.57 shortfall — so the right is not enough
        final Map<String, Box> boxes = boxes("A", new Box(100, 100, 10, 60),
                "N", new Box(131, 100, 50, 60));

        LabelLayout.widenToFitLabels(boxes, nameOf(Map.of("A", "A", "N", "N")));

        assertThat(boxes.get("A").x()).as("it grows left instead")
                .isCloseTo(100 - SHORTFALL_A_AT_10, within(1e-6));
    }

    @Test
    @DisplayName("only the box being widened changes; its neighbour does not")
    void onlyTheBoxBeingWidenedChanges() {
        final Map<String, Box> boxes = boxes("A", new Box(100, 100, 10, 60),
                "N", new Box(300, 100, 50, 60));

        LabelLayout.widenToFitLabels(boxes, nameOf(Map.of("A", "A", "N", "N")));

        assertThat(boxes.get("A").w()).isCloseTo(10 + SHORTFALL_A_AT_10, within(1e-6));
        assertThat(boxes.get("N")).isEqualTo(new Box(300, 100, 50, 60));
    }

    @Test
    @DisplayName("the map the caller passed is the one widened, and its keys are kept")
    void theWideningIsAppliedInPlaceToTheMap() {
        final Map<String, Box> boxes = boxes("A", new Box(100, 100, 10, 60));

        LabelLayout.widenToFitLabels(boxes, named("A", "A"));

        assertThat(boxes).containsOnlyKeys("A");
        assertThat(boxes.get("A").w()).isGreaterThan(10);
    }

    @Test
    @DisplayName("each box is widened independently of the others")
    void eachBoxIsWidenedIndependently() {
        final Map<String, Box> boxes = boxes("A", new Box(0, 0, 10, 60),
                "B", new Box(1000, 0, 10, 60));

        LabelLayout.widenToFitLabels(boxes, nameOf(Map.of("A", "A", "B", "B")));

        assertThat(boxes.get("A").x()).isEqualTo(0);
        assertThat(boxes.get("A").w()).isCloseTo(10 + SHORTFALL_A_AT_10, within(1e-6));
        assertThat(boxes.get("B").x()).isEqualTo(1000);
        assertThat(boxes.get("B").w()).isCloseTo(10 + SHORTFALL_A_AT_10, within(1e-6));
    }

    @Test
    @DisplayName("a label that needs a wider box grows it further")
    void aWiderLabelGrowsTheBoxFurther() {
        final Map<String, Box> boxes = boxes("A", new Box(100, 100, 10, 60));

        LabelLayout.widenToFitLabels(boxes, named("A", "Long"));

        // L + o + n + g = 4 x 7.3 = 29.2, plus the padding, times the safety factor
        assertThat(boxes.get("A").w()).isCloseTo(29.2 * 1.10 + PADDING, within(1e-6));
    }
}
