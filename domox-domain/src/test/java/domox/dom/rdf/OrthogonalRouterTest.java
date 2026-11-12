package domox.dom.rdf;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import domox.dom.rdf.geometry.Box;
import domox.dom.rdf.geometry.Pt;
import domox.dom.rdf.geometry.Segment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level guardrail for {@link OrthogonalRouter} — the axis-aligned route between two class boxes that
 * runs through none of them and crosses as few already-placed routes as it can.
 * <p>
 * The end-to-end {@code UmlLayoutDiagramsTest} sees one route's points in a finished JSON payload, so the
 * edge arithmetic, the port snapping, the forced departure and arrival, the obstacle avoidance and the
 * crossing avoidance are pinned here against plain {@link Box}es and {@link Pt}s.
 */
@DisplayName("OrthogonalRouter — the route between two class boxes")
class OrthogonalRouterTest {

    private static Pt p(final double x, final double y) {
        return new Pt(x, y);
    }

    /** The invariant every route the router emits must hold: no diagonal run survives. */
    private static void assertAxisAligned(final List<Pt> route) {
        for (int i = 1; i < route.size(); i++) {
            final Pt a = route.get(i - 1);
            final Pt b = route.get(i);
            assertThat(Math.abs(a.x() - b.x()) <= 1e-6 || Math.abs(a.y() - b.y()) <= 1e-6)
                    .as("segment %s -> %s must be axis-aligned", a, b)
                    .isTrue();
        }
    }

    /** Twice the signed area of the triangle p-q-r: which side of the line p-q the point r lies on. */
    private static double side(final Pt p, final Pt q, final Pt r) {
        return (q.x() - p.x()) * (r.y() - p.y()) - (q.y() - p.y()) * (r.x() - p.x());
    }

    /** The router's own crossing rule, restated: a shared endpoint or a collinear overlap is not one. */
    private static boolean crosses(final Pt a, final Pt b, final Pt c, final Pt d) {
        final double d1 = side(c, d, a);
        final double d2 = side(c, d, b);
        final double d3 = side(a, b, c);
        final double d4 = side(a, b, d);
        if (Math.abs(d1) < 1e-6 || Math.abs(d2) < 1e-6 || Math.abs(d3) < 1e-6 || Math.abs(d4) < 1e-6) {
            return false;
        }
        return (d1 > 0) != (d2 > 0) && (d3 > 0) != (d4 > 0);
    }

    private static void assertCrossesNone(final List<Pt> route, final List<Segment> placed) {
        for (int i = 1; i < route.size(); i++) {
            for (final Segment other : placed) {
                assertThat(crosses(route.get(i - 1), route.get(i), other.from(), other.to()))
                        .as("segment %s -> %s must not cross %s", route.get(i - 1), route.get(i), other)
                        .isFalse();
            }
        }
    }

    // -- the constants and the edge arithmetic ---------------------------------

    @Test
    @DisplayName("no direction and up are the two values the callers hand in")
    void theSharedDirectionConstants() {
        assertThat(OrthogonalRouter.NONE).isZero();
        assertThat(OrthogonalRouter.UP).isEqualTo(4);
        assertThat(OrthogonalRouter.DEPARTURE_STUB).isEqualTo(20.0);
    }

    @Test
    @DisplayName("the edge a point attaches to is the one it is nearest")
    void theEdgeIsTheNearestOne() {
        final Box box = new Box(0, 0, 100, 60);

        assertThat(OrthogonalRouter.edgeOf(box, p(50, 0))).as("top").isZero();
        assertThat(OrthogonalRouter.edgeOf(box, p(50, 60))).as("bottom").isEqualTo(1);
        assertThat(OrthogonalRouter.edgeOf(box, p(0, 30))).as("left").isEqualTo(2);
        assertThat(OrthogonalRouter.edgeOf(box, p(100, 30))).as("right").isEqualTo(3);
    }

    @Test
    @DisplayName("a corner resolves to an edge, never left as a corner")
    void aCornerResolvesToAnEdge() {
        final Box box = new Box(0, 0, 100, 60);

        assertThat(OrthogonalRouter.edgeOf(box, p(0, 0))).as("the top-left corner is the left edge")
                .isEqualTo(2);
        assertThat(OrthogonalRouter.edgeOf(box, p(100, 0))).as("the top-right corner is the right edge")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("a point on an edge runs from one corner to the other as the fraction rises")
    void aPointOnAnEdgeRunsCornerToCorner() {
        final Box box = new Box(10, 20, 100, 60);

        assertThat(OrthogonalRouter.pointOnEdge(box, 0, 0)).isEqualTo(p(10, 20));
        assertThat(OrthogonalRouter.pointOnEdge(box, 0, 0.5)).isEqualTo(p(60, 20));
        assertThat(OrthogonalRouter.pointOnEdge(box, 0, 1)).isEqualTo(p(110, 20));

        assertThat(OrthogonalRouter.pointOnEdge(box, 1, 0.5)).as("bottom").isEqualTo(p(60, 80));
        assertThat(OrthogonalRouter.pointOnEdge(box, 2, 0.5)).as("left").isEqualTo(p(10, 50));
        assertThat(OrthogonalRouter.pointOnEdge(box, 3, 0.5)).as("right").isEqualTo(p(110, 50));
    }

    @Test
    @DisplayName("an unknown edge number is treated as the right edge")
    void anUnknownEdgeNumberIsTheRightEdge() {
        assertThat(OrthogonalRouter.pointOnEdge(new Box(10, 20, 100, 60), 99, 0.5))
                .isEqualTo(p(110, 50));
    }

    // -- edgeMidpoint and nearBoundary -----------------------------------------

    @Test
    @DisplayName("a hint on an edge is snapped to that edge's middle")
    void aHintOnAnEdgeIsSnappedToItsMiddle() {
        final Box box = new Box(0, 0, 100, 60);

        assertThat(OrthogonalRouter.edgeMidpoint(box, p(80, 2))).as("near the top").isEqualTo(p(50, 0));
        assertThat(OrthogonalRouter.edgeMidpoint(box, p(80, 58))).as("near the bottom").isEqualTo(p(50, 60));
        assertThat(OrthogonalRouter.edgeMidpoint(box, p(2, 40))).as("near the left").isEqualTo(p(0, 30));
        assertThat(OrthogonalRouter.edgeMidpoint(box, p(98, 40))).as("near the right").isEqualTo(p(100, 30));
    }

    @Test
    @DisplayName("a hint at a corner is moved to an edge's middle, never left on the corner")
    void aHintAtACornerMovesToAnEdgeMiddle() {
        assertThat(OrthogonalRouter.edgeMidpoint(new Box(0, 0, 100, 60), p(0, 0))).isEqualTo(p(0, 30));
    }

    @Test
    @DisplayName("a point counts as near the boundary within six units")
    void nearBoundaryUsesASixUnitTolerance() {
        final Box box = new Box(0, 0, 100, 60);

        assertThat(OrthogonalRouter.nearBoundary(box, p(50, 30))).as("inside").isTrue();
        assertThat(OrthogonalRouter.nearBoundary(box, p(50, 0))).as("on the edge").isTrue();
        assertThat(OrthogonalRouter.nearBoundary(box, p(50, -6))).as("six above").isTrue();
        assertThat(OrthogonalRouter.nearBoundary(box, p(50, -7))).as("seven above").isFalse();
        assertThat(OrthogonalRouter.nearBoundary(box, p(106, 30))).as("six past the right").isTrue();
        assertThat(OrthogonalRouter.nearBoundary(box, p(107, 30))).as("seven past the right").isFalse();
    }

    // -- inward ----------------------------------------------------------------

    @Test
    @DisplayName("the inward normal points away from the edge the port sits on")
    void theInwardNormalPointsAwayFromTheEdge() {
        final Box box = new Box(0, 0, 100, 60);

        // the private numbering: 1 right, 2 left, 3 down, 4 up
        assertThat(OrthogonalRouter.inward(box, p(0, 30))).as("left edge, so rightwards").isEqualTo(1);
        assertThat(OrthogonalRouter.inward(box, p(100, 30))).as("right edge, so leftwards").isEqualTo(2);
        assertThat(OrthogonalRouter.inward(box, p(50, 0))).as("top edge, so downwards").isEqualTo(3);
        assertThat(OrthogonalRouter.inward(box, p(50, 60))).as("bottom edge, so upwards")
                .isEqualTo(OrthogonalRouter.UP);
    }

    @Test
    @DisplayName("a port just off an edge still resolves to that edge's normal")
    void aPortJustOffAnEdgeStillResolvesToThatEdge() {
        final Box box = new Box(0, 0, 100, 60);

        assertThat(OrthogonalRouter.inward(box, p(0.4, 30))).isEqualTo(1);
        assertThat(OrthogonalRouter.inward(box, p(99.6, 30))).isEqualTo(2);
        assertThat(OrthogonalRouter.inward(box, p(50, 0.4))).isEqualTo(3);
        assertThat(OrthogonalRouter.inward(box, p(50, 59.6))).isEqualTo(OrthogonalRouter.UP);
    }

    @Test
    @DisplayName("a port nowhere near an edge falls through to upwards")
    void aPortNowhereNearAnEdgeIsUpwards() {
        assertThat(OrthogonalRouter.inward(new Box(0, 0, 100, 60), p(50, 30)))
                .isEqualTo(OrthogonalRouter.UP);
    }

    // -- route -----------------------------------------------------------------

    /** Whether an axis-aligned segment has any part strictly inside {@code box} — inset by one unit. */
    private static boolean runsThrough(final Pt a, final Pt b, final Box box) {
        final double x0 = box.x() + 1;
        final double x1 = box.x() + box.w() - 1;
        final double y0 = box.y() + 1;
        final double y1 = box.y() + box.h() - 1;
        if (x1 <= x0 || y1 <= y0) {
            return false;
        }
        if (Math.abs(a.y() - b.y()) <= 1e-6) {
            return a.y() > y0 && a.y() < y1 && Math.max(a.x(), b.x()) > x0 && Math.min(a.x(), b.x()) < x1;
        }
        if (Math.abs(a.x() - b.x()) <= 1e-6) {
            return a.x() > x0 && a.x() < x1 && Math.max(a.y(), b.y()) > y0 && Math.min(a.y(), b.y()) < y1;
        }
        return false;
    }

    private static void assertRunsThroughNoBox(final List<Pt> route, final List<Box> boxes) {
        for (int i = 1; i < route.size(); i++) {
            for (final Box box : boxes) {
                assertThat(runsThrough(route.get(i - 1), route.get(i), box))
                        .as("segment %s -> %s must not run through %s", route.get(i - 1), route.get(i), box)
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("with nothing in the way the route is the straight line between the ports")
    void withNothingInTheWayTheRouteIsStraight() {
        final Box start = new Box(0, 0, 100, 60);
        final Box end = new Box(400, 0, 100, 60);

        final List<Pt> route = OrthogonalRouter.route(start, end, p(100, 30), p(400, 30),
                OrthogonalRouter.NONE, OrthogonalRouter.NONE, List.of(), List.of());

        assertThat(route).containsExactly(p(100, 30), p(400, 30));
    }

    @Test
    @DisplayName("a port hint off the box lets the router pick the cheapest edge midpoint itself")
    void aPortHintOffTheBoxLetsTheRouterPickTheCheapestMidpoint() {
        final Box start = new Box(0, 0, 100, 60);
        final Box end = new Box(400, 0, 100, 60);

        // the hint is nowhere near the box, so all four midpoints are candidates
        final List<Pt> route = OrthogonalRouter.route(start, end, p(5000, 5000), p(400, 30),
                OrthogonalRouter.NONE, OrthogonalRouter.NONE, List.of(), List.of());

        assertThat(route).as("the right-edge midpoint is the cheapest way out").containsExactly(
                p(100, 30), p(400, 30));
    }

    @Test
    @DisplayName("a box standing between the two ports is routed around, never through")
    void aBoxInTheWayIsRoutedAround() {
        final Box start = new Box(0, 0, 100, 60);
        final Box end = new Box(400, 0, 100, 60);
        // a tall box squarely across the straight line between the two ports
        final Box obstacle = new Box(200, -40, 100, 140);

        final List<Pt> route = OrthogonalRouter.route(start, end, p(100, 30), p(400, 30),
                OrthogonalRouter.NONE, OrthogonalRouter.NONE,
                List.of(start, end, obstacle), List.of());

        assertThat(route).isNotEmpty();
        assertThat(route.getFirst()).isEqualTo(p(100, 30));
        assertThat(route.getLast()).isEqualTo(p(400, 30));
        assertRunsThroughNoBox(route, List.of(start, end, obstacle));
        assertAxisAligned(route);
    }

    @Test
    @DisplayName("a forced departure leaves the port in that direction for the full stub")
    void aForcedDepartureLeavesThePortUpwards() {
        final Box start = new Box(0, 0, 100, 60);
        final Box end = new Box(400, 0, 100, 60);

        final List<Pt> route = OrthogonalRouter.route(start, end, p(50, 0), p(400, 30),
                OrthogonalRouter.UP, OrthogonalRouter.NONE, List.of(), List.of());

        assertThat(route.getFirst()).as("the port is put back in front of the route")
                .isEqualTo(p(50, 0));
        assertThat(route.get(1)).as("the route rises the whole departure stub before it may turn")
                .isEqualTo(p(50, -20));
    }

    @Test
    @DisplayName("a required arrival direction is honoured at the port")
    void aRequiredArrivalDirectionIsHonoured() {
        final Box start = new Box(0, 0, 100, 60);
        final Box end = new Box(400, 0, 100, 60);

        // arriving at the end box's bottom edge must be travelling upwards
        final List<Pt> route = OrthogonalRouter.route(start, end, p(100, 30), p(450, 60),
                OrthogonalRouter.NONE, OrthogonalRouter.UP, List.of(), List.of());

        assertThat(route.getLast()).isEqualTo(p(450, 60));
        final Pt before = route.get(route.size() - 2);
        assertThat(before.x()).as("the last leg is vertical").isEqualTo(450);
        assertThat(before.y()).as("and it travels upwards into the port").isGreaterThan(60);
    }

    @Test
    @DisplayName("a grid too big to search yields no route, so the caller falls back")
    void aGridTooBigToSearchYieldsNoRoute() {
        final Box start = new Box(0, 0, 10, 10);
        final Box end = new Box(1000, 1000, 10, 10);
        final List<Box> boxes = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            boxes.add(new Box(i * 5.0, i * 5.0, 3, 3));
        }

        assertThat(OrthogonalRouter.route(start, end, p(10, 5), p(1000, 1005),
                OrthogonalRouter.NONE, OrthogonalRouter.NONE, boxes, List.of()))
                .isEmpty();
    }

    @Test
    @DisplayName("a placed route across the way is avoided when the detour is cheaper than crossing it")
    void aPlacedRouteIsAvoided() {
        final Box start = new Box(0, 0, 100, 60);
        final Box end = new Box(400, 0, 100, 60);
        // a short vertical route straddling the straight line between the two ports
        final List<Segment> placed = List.of(new Segment(p(250, 20), p(250, 40)));

        final List<Pt> route = OrthogonalRouter.route(start, end, p(100, 30), p(400, 30),
                OrthogonalRouter.NONE, OrthogonalRouter.NONE, List.of(start, end), placed);

        assertThat(route).isNotEmpty();
        assertThat(route).as("it detours rather than run through the crossing").doesNotContain(p(250, 30));
        assertCrossesNone(route, placed);
        assertAxisAligned(route);
    }
}
