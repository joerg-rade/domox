package domox.dom.rdf;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import domox.dom.rdf.geometry.Box;
import domox.dom.rdf.geometry.Pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit-level guardrail for {@link RouteGeometry} — turning the polyline an OntoUML export stores for a
 * connector into one that can actually be drawn.
 * <p>
 * The end-to-end {@code UmlLayoutDiagramsTest} sees these rules only as the points of one connector in a
 * finished JSON payload, so the re-chaining, the clipping to the box edges, the spur removal and the
 * stray detection are pinned here against plain {@link Pt}s and {@link Box}es.
 */
@DisplayName("RouteGeometry — from the export's stored path to a drawable polyline")
class RouteGeometryTest {

    private static final Box START = new Box(0, 0, 100, 60);
    private static final Box END = new Box(300, 0, 100, 60);

    private static Pt p(final double x, final double y) {
        return new Pt(x, y);
    }

    /** The invariant every route the reconstruction emits must hold: no diagonal run survives. */
    private static void assertAxisAligned(final List<Pt> route) {
        for (int i = 1; i < route.size(); i++) {
            final Pt a = route.get(i - 1);
            final Pt b = route.get(i);
            assertThat(Math.abs(a.x() - b.x()) <= RouteGeometry.ORTHO_TOL
                    || Math.abs(a.y() - b.y()) <= RouteGeometry.ORTHO_TOL)
                    .as("segment %s -> %s must be axis-aligned", a, b)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("the alignment tolerance is one model unit")
    void theAlignmentToleranceIsOneModelUnit() {
        assertThat(RouteGeometry.ORTHO_TOL).isEqualTo(1.0);
    }

    // -- centreDistance --------------------------------------------------------

    @Test
    @DisplayName("the centre distance is the distance between the two boxes' centres")
    void centreDistanceIsBetweenTheCentres() {
        // centres (5,5) and (35,45): a 30-40-50 triangle
        assertThat(RouteGeometry.centreDistance(new Box(0, 0, 10, 10), new Box(30, 40, 10, 10)))
                .isCloseTo(50, within(1e-9));
    }

    @Test
    @DisplayName("the centre distance uses the box centres, not their top-left corners")
    void centreDistanceUsesCentresNotCorners() {
        // the same top-left corner, different sizes: centres are (50,30) and (75,30)
        assertThat(RouteGeometry.centreDistance(new Box(0, 0, 100, 60), new Box(0, 0, 150, 60)))
                .isCloseTo(25, within(1e-9));
    }

    @Test
    @DisplayName("a self-loop's two boxes coincide, so their centre distance is zero")
    void aSelfLoopHasNoCentreDistance() {
        final Box box = new Box(10, 20, 100, 60);
        assertThat(RouteGeometry.centreDistance(box, box)).isZero();
    }

    // -- orthogonalise ---------------------------------------------------------

    @Test
    @DisplayName("fewer than two points comes back untouched")
    void fewerThanTwoPointsIsUntouched() {
        assertThat(RouteGeometry.orthogonalise(List.of(), START, END)).isEmpty();
        assertThat(RouteGeometry.orthogonalise(List.of(p(5, 5)), START, END)).containsExactly(p(5, 5));
    }

    @Test
    @DisplayName("an already orthogonal route is left exactly as it is")
    void anAlreadyOrthogonalRouteIsUnchanged() {
        final List<Pt> route = List.of(p(100, 30), p(200, 30), p(200, 10), p(300, 10));

        assertThat(RouteGeometry.orthogonalise(route, START, END)).containsExactlyElementsOf(route);
    }

    @Test
    @DisplayName("a diagonal between a front edge and a side edge turns once")
    void aDiagonalBetweenAFrontAndASideEdgeTurnsOnce() {
        // leaves the top edge and arrives on the left edge: one corner serves both
        final List<Pt> route = RouteGeometry.orthogonalise(List.of(p(50, 0), p(300, 30)), START, END);

        assertThat(route).containsExactly(p(50, 0), p(50, 30), p(300, 30));
        assertAxisAligned(route);
    }

    @Test
    @DisplayName("a diagonal between two front edges becomes a staircase")
    void aDiagonalBetweenTwoFrontEdgesBecomesAStaircase() {
        // leaves the top edge and arrives on the bottom edge: no single corner can serve both
        final List<Pt> route = RouteGeometry.orthogonalise(List.of(p(50, 0), p(350, 60)), START, END);

        assertThat(route).containsExactly(p(50, 0), p(50, 30), p(350, 30), p(350, 60));
        assertAxisAligned(route);
    }

    @Test
    @DisplayName("every segment of a route built from several diagonals is axis-aligned")
    void everySegmentOfAMultiDiagonalRouteIsAxisAligned() {
        final List<Pt> route = RouteGeometry.orthogonalise(
                List.of(p(100, 30), p(200, 10), p(300, 50)), START, END);

        assertAxisAligned(route);
        assertThat(route.getFirst()).as("the route still starts where it did").isEqualTo(p(100, 30));
        assertThat(route.getLast()).as("and ends where it did").isEqualTo(p(300, 50));
    }

    @Test
    @DisplayName("a route that doubles back loses its spur")
    void aRouteThatDoublesBackLosesItsSpur() {
        final List<Pt> route = RouteGeometry.orthogonalise(
                List.of(p(0, 0), p(10, 0), p(5, 0), p(20, 0)), null, null);

        assertThat(route).containsExactly(p(0, 0), p(5, 0), p(20, 0));
        assertThat(route).as("the doubling-back point is gone").doesNotContain(p(10, 0));
    }

    @Test
    @DisplayName("a two-point route is never reduced further")
    void aTwoPointRouteIsLeftAlone() {
        assertThat(RouteGeometry.orthogonalise(List.of(p(0, 0), p(5, 0)), null, null))
                .containsExactly(p(0, 0), p(5, 0));
    }

    // -- chain -----------------------------------------------------------------

    @Test
    @DisplayName("without a start box the export's own order is kept")
    void chainWithoutAStartBoxKeepsTheOrder() {
        final List<Pt> points = List.of(p(300, 300), p(5, 5), p(200, 200));

        assertThat(RouteGeometry.chain(points, null)).containsExactlyElementsOf(points);
    }

    @Test
    @DisplayName("a two-point path is reversed when only its far end sits on the start box")
    void aTwoPointPathIsReversedWhenItStartsAtTheWrongEnd() {
        assertThat(RouteGeometry.chain(List.of(p(300, 300), p(5, 5)), START))
                .containsExactly(p(5, 5), p(300, 300));
    }

    @Test
    @DisplayName("a two-point path already starting on the box is left as stated")
    void aTwoPointPathStartingOnTheBoxIsLeftAlone() {
        final List<Pt> points = List.of(p(5, 5), p(300, 300));

        assertThat(RouteGeometry.chain(points, START)).containsExactlyElementsOf(points);
    }

    @Test
    @DisplayName("a two-point path with neither end on the box is left as stated")
    void aTwoPointPathWithNeitherEndOnTheBoxIsLeftAlone() {
        final List<Pt> points = List.of(p(300, 300), p(400, 400));

        assertThat(RouteGeometry.chain(points, START)).containsExactlyElementsOf(points);
    }

    @Test
    @DisplayName("a multi-point path is re-chained by nearest neighbour from the box")
    void aMultiPointPathIsReChainedFromTheBox() {
        // the point on the box is listed in the middle; the route must still start there
        assertThat(RouteGeometry.chain(List.of(p(300, 300), p(5, 5), p(200, 200)), START))
                .containsExactly(p(5, 5), p(200, 200), p(300, 300));
    }

    @Test
    @DisplayName("a path with no point on the start box keeps the export's order")
    void aPathWithNoPointOnTheBoxKeepsTheOrder() {
        final List<Pt> points = List.of(p(300, 300), p(400, 400), p(500, 500));

        assertThat(RouteGeometry.chain(points, START)).containsExactlyElementsOf(points);
    }

    // -- clipToBoxes -----------------------------------------------------------

    @Test
    @DisplayName("fewer than two points comes back untouched")
    void clipLeavesASinglePointAlone() {
        assertThat(RouteGeometry.clipToBoxes(List.of(p(50, 50)), START, END))
                .containsExactly(p(50, 50));
    }

    @Test
    @DisplayName("a path ending inside a box is pulled out to that box's edge")
    void aPathEndingInsideABoxIsPulledToTheEdge() {
        assertThat(RouteGeometry.clipToBoxes(List.of(p(50, 50), p(350, 50)), START, END))
                .containsExactly(p(100, 50), p(300, 50));
    }

    @Test
    @DisplayName("a path already ending on both edges is left alone")
    void aPathAlreadyOnBothEdgesIsLeftAlone() {
        final List<Pt> points = List.of(p(100, 50), p(300, 50));

        assertThat(RouteGeometry.clipToBoxes(points, START, END)).containsExactlyElementsOf(points);
    }

    // -- straysFrom ------------------------------------------------------------

    @Test
    @DisplayName("without both boxes nothing counts as a stray")
    void withoutBothBoxesNothingStrays() {
        assertThat(RouteGeometry.straysFrom(p(9999, 9999), null, END)).isFalse();
        assertThat(RouteGeometry.straysFrom(p(9999, 9999), START, null)).isFalse();
    }

    @Test
    @DisplayName("a bend between the two boxes is not a stray")
    void aBendBetweenTheBoxesIsNotAStray() {
        assertThat(RouteGeometry.straysFrom(p(200, 30), START, END)).isFalse();
    }

    @Test
    @DisplayName("the stray limit is the centre distance — one unit past it, and the point strays")
    void theStrayLimitIsTheCentreDistance() {
        // the centres are 300 apart, so the limit is 300: 700 is exactly 300 from the end box
        assertThat(RouteGeometry.straysFrom(p(700, 50), START, END)).isFalse();
        assertThat(RouteGeometry.straysFrom(p(701, 50), START, END)).isTrue();
    }

    @Test
    @DisplayName("a point far past both boxes is a stray")
    void aPointFarPastBothBoxesIsAStray() {
        assertThat(RouteGeometry.straysFrom(p(1000, 50), START, END)).isTrue();
    }

    @Test
    @DisplayName("the slack floor keeps a self-loop's bow from being mistaken for a stray")
    void theSlackFloorKeepsASelfLoopsBow() {
        final Box box = new Box(0, 0, 100, 60);
        // the two boxes coincide, so the centre distance is 0 — the 60-unit floor is what applies
        assertThat(RouteGeometry.straysFrom(p(0, -50), box, box)).isFalse();
        assertThat(RouteGeometry.straysFrom(p(0, -70), box, box)).isTrue();
    }

    // -- farFromBothBoxes ------------------------------------------------------

    @Test
    @DisplayName("without both boxes nothing is far from them")
    void withoutBothBoxesNothingIsFar() {
        assertThat(RouteGeometry.farFromBothBoxes(p(9999, 9999), null, END)).isFalse();
        assertThat(RouteGeometry.farFromBothBoxes(p(9999, 9999), START, null)).isFalse();
    }

    @Test
    @DisplayName("a point within the end slack of a box is not far from both")
    void aPointWithinTheEndSlackIsNotFarFromBoth() {
        assertThat(RouteGeometry.farFromBothBoxes(p(100, 50), START, END)).isFalse();
        assertThat(RouteGeometry.farFromBothBoxes(p(120, 50), START, END))
                .as("20 past the start box's right edge is inside the 30-unit slack").isFalse();
        assertThat(RouteGeometry.farFromBothBoxes(p(50, -20), START, END))
                .as("20 above the start box is inside the slack too").isFalse();
    }

    @Test
    @DisplayName("a point beyond the end slack of both boxes is far from both")
    void aPointBeyondBothBoxesIsFarFromBoth() {
        // 140 above the boxes, and 50 past the start's right edge
        assertThat(RouteGeometry.farFromBothBoxes(p(150, 200), START, END)).isTrue();
    }
}
