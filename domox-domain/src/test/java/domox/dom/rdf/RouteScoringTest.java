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
 * Unit-level guardrail for {@link RouteScoring} — how good a candidate route is, and the fallback chooser
 * that picks the best of them.
 * <p>
 * This is the one collaborator the end-to-end {@code UmlLayoutDiagramsTest} <em>cannot</em> reach: the
 * chooser only runs when {@link OrthogonalRouter} finds no route at all for a connector, and none of the
 * bundled model's fixtures is laid out that way.  Before this class existed, 96 of its 99 mutants had
 * never been executed by any test in the module.
 */
@DisplayName("RouteScoring — the fallback chooser, and the routes it scores")
class RouteScoringTest {

    private static final Box START = new Box(0, 0, 100, 60);
    private static final Box END = new Box(400, 0, 100, 60);

    private static Pt p(final double x, final double y) {
        return new Pt(x, y);
    }

    /** The four points a fallback route may leave or enter a box by. */
    private static List<Pt> edgeMidpoints(final Box box) {
        final double midX = box.x() + box.w() / 2;
        final double midY = box.y() + box.h() / 2;
        return List.of(
                new Pt(midX, box.y()),
                new Pt(midX, box.y() + box.h()),
                new Pt(box.x(), midY),
                new Pt(box.x() + box.w(), midY));
    }

    // -- addSegments -----------------------------------------------------------

    @Test
    @DisplayName("a polyline is recorded as one segment per pair of consecutive points")
    void aPolylineIsRecordedAsSegments() {
        final List<Segment> into = new ArrayList<>();

        RouteScoring.addSegments(into, List.of(p(0, 0), p(10, 0), p(10, 20)));

        assertThat(into).containsExactly(new Segment(p(0, 0), p(10, 0)), new Segment(p(10, 0), p(10, 20)));
    }

    @Test
    @DisplayName("a single point records no segment")
    void aSinglePointRecordsNoSegment() {
        final List<Segment> into = new ArrayList<>();

        RouteScoring.addSegments(into, List.of(p(0, 0)));

        assertThat(into).isEmpty();
    }

    @Test
    @DisplayName("no points at all records no segment")
    void noPointsRecordsNoSegment() {
        final List<Segment> into = new ArrayList<>();

        RouteScoring.addSegments(into, List.of());

        assertThat(into).isEmpty();
    }

    @Test
    @DisplayName("the new segments are appended to the routes already placed")
    void theSegmentsAreAppended() {
        final List<Segment> into = new ArrayList<>(List.of(new Segment(p(0, 0), p(1, 0))));

        RouteScoring.addSegments(into, List.of(p(5, 5), p(6, 5)));

        assertThat(into).containsExactly(new Segment(p(0, 0), p(1, 0)), new Segment(p(5, 5), p(6, 5)));
    }

    // -- bestFallbackRoute -----------------------------------------------------

    @Test
    @DisplayName("the fallback leaves one edge midpoint and arrives at another")
    void theFallbackJoinsTwoEdgeMidpoints() {
        final List<Pt> route = RouteScoring.bestFallbackRoute(START, END, List.of(START, END), List.of());

        assertThat(route).hasSizeGreaterThanOrEqualTo(2);
        assertThat(route.getFirst()).as("it leaves a midpoint of the start box").isIn(edgeMidpoints(START));
        assertThat(route.getLast()).as("and arrives at one of the end box").isIn(edgeMidpoints(END));
    }

    @Test
    @DisplayName("with nothing in the way the fallback is the straight line between the top midpoints")
    void withNothingInTheWayTheFallbackIsStraight() {
        final List<Pt> route = RouteScoring.bestFallbackRoute(START, END, List.of(START, END), List.of());

        assertThat(route).containsExactly(p(50, 0), p(450, 0));
    }

    @Test
    @DisplayName("the fallback always returns a usable route, however degenerate the boxes")
    void theFallbackAlwaysReturnsARoute() {
        assertThat(RouteScoring.bestFallbackRoute(new Box(0, 0, 0, 0), new Box(0, 0, 0, 0),
                List.of(), List.of()))
                .as("a zero-sized box has four coincident midpoints, and the route still has two points")
                .hasSizeGreaterThanOrEqualTo(2);
    }

    /** Twice the signed area of the triangle p-q-r: which side of the line p-q the point r lies on. */
    private static double side(final Pt p, final Pt q, final Pt r) {
        return (q.x() - p.x()) * (r.y() - p.y()) - (q.y() - p.y()) * (r.x() - p.x());
    }

    /** The chooser's own crossing rule, restated: a shared endpoint or a collinear overlap is not one. */
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

    // -- what the chooser weighs -----------------------------------------------

    @Test
    @DisplayName("the fallback prefers the route that crosses fewer placed routes")
    void theFallbackPrefersFewerCrossings() {
        // a vertical route straddling the line between the two top midpoints
        final List<Segment> placed = List.of(new Segment(p(250, -50), p(250, 50)));

        final List<Pt> route = RouteScoring.bestFallbackRoute(START, END, List.of(START, END), placed);

        assertThat(route).isNotEmpty();
        assertCrossesNone(route, placed);
    }

    @Test
    @DisplayName("a route that merely shares a line with a placed one is not crossing it")
    void aCollinearRouteIsNotCrossed() {
        // the placed route runs along the very line the two top midpoints join
        final List<Segment> placed = List.of(new Segment(p(150, 0), p(350, 0)));

        final List<Pt> route = RouteScoring.bestFallbackRoute(START, END, List.of(START, END), placed);

        assertThat(route).as("a shared line is not a crossing, so the straight route still wins")
                .containsExactly(p(50, 0), p(450, 0));
    }

    @Test
    @DisplayName("a route that only touches a placed one at its endpoint is not crossing it either")
    void aSharedEndpointIsNotCrossed() {
        // the placed route ends exactly on the top midpoint the straight route starts at
        final List<Segment> placed = List.of(new Segment(p(50, -50), p(50, 0)));

        final List<Pt> route = RouteScoring.bestFallbackRoute(START, END, List.of(START, END), placed);

        assertThat(route).containsExactly(p(50, 0), p(450, 0));
    }

    @Test
    @DisplayName("the fallback keeps clear of a class box even when running through it would be shorter")
    void theFallbackKeepsClearOfAClassBox() {
        // a flat box straddling the line the two top midpoints join: the straight route would cut through it
        final Box obstacle = new Box(150, -10, 100, 20);

        final List<Pt> route = RouteScoring.bestFallbackRoute(START, END,
                List.of(START, END, obstacle), List.of());

        assertThat(route).isNotEmpty();
        assertRunsThroughNoBox(route, List.of(START, END, obstacle));
    }

    @Test
    @DisplayName("a box standing between the two boxes is routed clear of as well")
    void aBoxBetweenTheTwoBoxesIsRoutedClearOf() {
        // tall enough to block the bottom midpoints, but the top line passes over it
        final Box obstacle = new Box(200, 40, 100, 60);

        final List<Pt> route = RouteScoring.bestFallbackRoute(START, END,
                List.of(START, END, obstacle), List.of());

        assertThat(route).isNotEmpty();
        assertRunsThroughNoBox(route, List.of(START, END, obstacle));
    }

    @Test
    @DisplayName("a route that crosses nothing but runs through a box still loses to one that is clear")
    void aClearRouteBeatsAThroughBoxRoute() {
        // the top midpoints are joined by a line that cuts straight through this box, and nothing is
        // placed to cross — so only the intrusion penalty can rule that pair out
        final Box obstacle = new Box(150, -10, 100, 20);
        final List<Pt> straight = RouteScoring.bestFallbackRoute(START, END, List.of(START, END), List.of());

        assertThat(straight).as("without the box, the straight line between the top midpoints wins")
                .containsExactly(p(50, 0), p(450, 0));

        final List<Pt> clear = RouteScoring.bestFallbackRoute(START, END,
                List.of(START, END, obstacle), List.of());

        assertThat(clear).as("with the box there, that same line must be rejected")
                .isNotEqualTo(straight);
        assertRunsThroughNoBox(clear, List.of(START, END, obstacle));
    }
}
