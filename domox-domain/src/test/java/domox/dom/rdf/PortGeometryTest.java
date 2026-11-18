package domox.dom.rdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import domox.dom.rdf.geometry.Box;
import domox.dom.rdf.geometry.Pt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level guardrail for {@link PortGeometry} — where a connector attaches to a class box and which way
 * it leaves or arrives.
 * <p>
 * These assertions used to live in {@code OrthogonalRouterTest}, because the arithmetic did: the router
 * owned both the port vocabulary and the A* search that consumes it.  Splitting the two leaves the router's
 * own test class to the routing alone, and pins the edge numbering, the edge arithmetic and the inward
 * normals here.
 */
@DisplayName("PortGeometry — where a connector attaches to a box")
class PortGeometryTest {

    private static Pt p(final double x, final double y) {
        return new Pt(x, y);
    }

    // -- the constants ---------------------------------------------------------

    @Test
    @DisplayName("no direction, up, and the departure stub are the values the callers hand in")
    void theSharedDirectionConstants() {
        assertThat(PortGeometry.NONE).isZero();
        assertThat(PortGeometry.UP).isEqualTo(4);
        assertThat(PortGeometry.DEPARTURE_STUB).isEqualTo(20.0);
    }

    // -- the edge arithmetic ---------------------------------------------------

    @Test
    @DisplayName("the edge a point attaches to is the one it is nearest")
    void theEdgeIsTheNearestOne() {
        final Box box = new Box(0, 0, 100, 60);

        assertThat(PortGeometry.edgeOf(box, p(50, 0))).as("top").isZero();
        assertThat(PortGeometry.edgeOf(box, p(50, 60))).as("bottom").isEqualTo(1);
        assertThat(PortGeometry.edgeOf(box, p(0, 30))).as("left").isEqualTo(2);
        assertThat(PortGeometry.edgeOf(box, p(100, 30))).as("right").isEqualTo(3);
    }

    @Test
    @DisplayName("a corner resolves to an edge, never left as a corner")
    void aCornerResolvesToAnEdge() {
        final Box box = new Box(0, 0, 100, 60);

        assertThat(PortGeometry.edgeOf(box, p(0, 0))).as("the top-left corner is the left edge")
                .isEqualTo(2);
        assertThat(PortGeometry.edgeOf(box, p(100, 0))).as("the top-right corner is the right edge")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("a point on an edge runs from one corner to the other as the fraction rises")
    void aPointOnAnEdgeRunsCornerToCorner() {
        final Box box = new Box(10, 20, 100, 60);

        assertThat(PortGeometry.pointOnEdge(box, 0, 0)).isEqualTo(p(10, 20));
        assertThat(PortGeometry.pointOnEdge(box, 0, 0.5)).isEqualTo(p(60, 20));
        assertThat(PortGeometry.pointOnEdge(box, 0, 1)).isEqualTo(p(110, 20));

        assertThat(PortGeometry.pointOnEdge(box, 1, 0.5)).as("bottom").isEqualTo(p(60, 80));
        assertThat(PortGeometry.pointOnEdge(box, 2, 0.5)).as("left").isEqualTo(p(10, 50));
        assertThat(PortGeometry.pointOnEdge(box, 3, 0.5)).as("right").isEqualTo(p(110, 50));
    }

    @Test
    @DisplayName("an unknown edge number is treated as the right edge")
    void anUnknownEdgeNumberIsTheRightEdge() {
        assertThat(PortGeometry.pointOnEdge(new Box(10, 20, 100, 60), 99, 0.5))
                .isEqualTo(p(110, 50));
    }

    // -- edgeMidpoint and nearBoundary -----------------------------------------

    @Test
    @DisplayName("a hint on an edge is snapped to that edge's middle")
    void aHintOnAnEdgeIsSnappedToItsMiddle() {
        final Box box = new Box(0, 0, 100, 60);

        assertThat(PortGeometry.edgeMidpoint(box, p(80, 2))).as("near the top").isEqualTo(p(50, 0));
        assertThat(PortGeometry.edgeMidpoint(box, p(80, 58))).as("near the bottom").isEqualTo(p(50, 60));
        assertThat(PortGeometry.edgeMidpoint(box, p(2, 40))).as("near the left").isEqualTo(p(0, 30));
        assertThat(PortGeometry.edgeMidpoint(box, p(98, 40))).as("near the right").isEqualTo(p(100, 30));
    }

    @Test
    @DisplayName("a hint at a corner is moved to an edge's middle, never left on the corner")
    void aHintAtACornerMovesToAnEdgeMiddle() {
        assertThat(PortGeometry.edgeMidpoint(new Box(0, 0, 100, 60), p(0, 0))).isEqualTo(p(0, 30));
    }

    @Test
    @DisplayName("a point counts as near the boundary within six units")
    void nearBoundaryUsesASixUnitTolerance() {
        final Box box = new Box(0, 0, 100, 60);

        assertThat(PortGeometry.nearBoundary(box, p(50, 30))).as("inside").isTrue();
        assertThat(PortGeometry.nearBoundary(box, p(50, 0))).as("on the edge").isTrue();
        assertThat(PortGeometry.nearBoundary(box, p(50, -6))).as("six above").isTrue();
        assertThat(PortGeometry.nearBoundary(box, p(50, -7))).as("seven above").isFalse();
        assertThat(PortGeometry.nearBoundary(box, p(106, 30))).as("six past the right").isTrue();
        assertThat(PortGeometry.nearBoundary(box, p(107, 30))).as("seven past the right").isFalse();
    }

    // -- inward ----------------------------------------------------------------

    @Test
    @DisplayName("the inward normal points away from the edge the port sits on")
    void theInwardNormalPointsAwayFromTheEdge() {
        final Box box = new Box(0, 0, 100, 60);

        // the numbering: 1 right, 2 left, 3 down, 4 up
        assertThat(PortGeometry.inward(box, p(0, 30))).as("left edge, so rightwards")
                .isEqualTo(PortGeometry.RIGHT);
        assertThat(PortGeometry.inward(box, p(100, 30))).as("right edge, so leftwards")
                .isEqualTo(PortGeometry.LEFT);
        assertThat(PortGeometry.inward(box, p(50, 0))).as("top edge, so downwards")
                .isEqualTo(PortGeometry.DOWN);
        assertThat(PortGeometry.inward(box, p(50, 60))).as("bottom edge, so upwards")
                .isEqualTo(PortGeometry.UP);
    }

    @Test
    @DisplayName("a port just off an edge still resolves to that edge's normal")
    void aPortJustOffAnEdgeStillResolvesToThatEdge() {
        final Box box = new Box(0, 0, 100, 60);

        assertThat(PortGeometry.inward(box, p(0.4, 30))).isEqualTo(PortGeometry.RIGHT);
        assertThat(PortGeometry.inward(box, p(99.6, 30))).isEqualTo(PortGeometry.LEFT);
        assertThat(PortGeometry.inward(box, p(50, 0.4))).isEqualTo(PortGeometry.DOWN);
        assertThat(PortGeometry.inward(box, p(50, 59.6))).isEqualTo(PortGeometry.UP);
    }

    @Test
    @DisplayName("a port nowhere near an edge falls through to upwards")
    void aPortNowhereNearAnEdgeIsUpwards() {
        assertThat(PortGeometry.inward(new Box(0, 0, 100, 60), p(50, 30)))
                .isEqualTo(PortGeometry.UP);
    }
}
