package domox.dom.rdf;

import java.util.List;

import domox.dom.rdf.geometry.Box;
import domox.dom.rdf.geometry.Pt;
import domox.dom.rdf.geometry.Segment;

/**
 * <b>Route scoring</b> for the UML reconstruction: how good a candidate route is, and the fallback
 * chooser that picks the best one.
 *
 * <p>Where {@link RouteGeometry} decides what <em>shape</em> a route has, this class decides how
 * <em>good</em> it is.  The export's own route is used wherever it is usable; only when the router finds
 * nothing for a connector — a box it cannot get around — are the two boxes joined by a plain orthogonal
 * route, and there are four edge midpoints on each box to leave and enter by.  The chooser tries every
 * pair and keeps the one that crosses the fewest routes already placed and runs through no class box,
 * with an intrusion into a box weighted far above a crossing ({@link #THROUGH_BOX_PENALTY}) so that
 * staying clear of the boxes wins first and crossings only break the tie between the routes that do.</p>
 *
 * <p>Extracted from {@code UmlLayoutDiagrams}; the routes it scores are shaped by {@link RouteGeometry}
 * and produced by {@link OrthogonalRouter}.</p>
 */
final class RouteScoring {

    /** Below this a point counts as lying *on* a segment's line — not a transversal crossing. */
    private static final double CROSS_TOL = 1e-6;

    /**
     * How much worse a route that runs through a class box is than one that merely crosses another route.
     * <p>
     * A fallback route may leave and enter by any of four edge midpoints, and several pairs score the
     * same on crossings; without this the chooser could pick one whose last leg enters through the far
     * edge and so draws straight across the box.  Weighting an intrusion far above a crossing makes the
     * edge pair that stays clear of every box win first, crossings only break the tie between those.
     */
    private static final long THROUGH_BOX_PENALTY = 1000;

    /** A box is shrunk by this when testing whether a route runs through it, so an endpoint on its edge is clear. */
    private static final double BOX_INSET = 1.0;

    /** The orthogonal route between two boxes that crosses the fewest already-placed routes. */
    static List<Pt> bestFallbackRoute(final Box start, final Box end,
                                      final List<Box> boxes, final List<Segment> placed) {
        List<Pt> best = List.of();
        long fewest = Long.MAX_VALUE;
        for (final Pt from : edgeMidpoints(start)) {
            for (final Pt to : edgeMidpoints(end)) {
                final List<Pt> route = RouteGeometry.orthogonalise(List.of(from, to), start, end);
                if (route.size() < 2) {
                    continue;
                }
                final long score = countCrossings(route, placed)
                        + THROUGH_BOX_PENALTY * boxesTraversed(route, boxes);
                if (score < fewest) {
                    fewest = score;
                    best = route;
                }
            }
        }
        return best;
    }

    /** The midpoints of a box's four edges — where a route may leave or enter. */
    private static List<Pt> edgeMidpoints(final Box box) {
        return List.of(
                new Pt(box.x() + box.w() / 2, box.y()),
                new Pt(box.x() + box.w() / 2, box.y() + box.h()),
                new Pt(box.x(), box.y() + box.h() / 2),
                new Pt(box.x() + box.w(), box.y() + box.h() / 2));
    }

    /** Records a route as placed, so the routes chosen after it can be scored against it. */
    static void addSegments(final List<Segment> into, final List<Pt> points) {
        for (int i = 1; i < points.size(); i++) {
            into.add(new Segment(points.get(i - 1), points.get(i)));
        }
    }

    /** How many times {@code route} crosses the routes already placed. */
    private static int countCrossings(final List<Pt> route, final List<Segment> placed) {
        int crossings = 0;
        for (int i = 1; i < route.size(); i++) {
            for (final Segment other : placed) {
                if (crosses(route.get(i - 1), route.get(i), other.from(), other.to())) {
                    crossings++;
                }
            }
        }
        return crossings;
    }

    /**
     * Whether two segments cross transversally.
     * <p>
     * A shared endpoint or a collinear overlap is <em>not</em> a crossing, and counting those would make
     * the chooser chase noise rather than visible crossings.
     */
    private static boolean crosses(final Pt a, final Pt b, final Pt c, final Pt d) {
        final double d1 = side(c, d, a);
        final double d2 = side(c, d, b);
        final double d3 = side(a, b, c);
        final double d4 = side(a, b, d);
        if (Math.abs(d1) < CROSS_TOL || Math.abs(d2) < CROSS_TOL
                || Math.abs(d3) < CROSS_TOL || Math.abs(d4) < CROSS_TOL) {
            return false;
        }
        return (d1 > 0) != (d2 > 0) && (d3 > 0) != (d4 > 0);
    }

    /** Twice the signed area of the triangle p-q-r: which side of the line p-q the point r lies on. */
    private static double side(final Pt p, final Pt q, final Pt r) {
        return (q.x() - p.x()) * (r.y() - p.y()) - (q.y() - p.y()) * (r.x() - p.x());
    }

    /**
     * How many times a route runs through the interior of a class box.
     * <p>
     * A route may legitimately touch a box at the one point where it leaves or enters (its endpoint sits
     * on the edge); running along the inside, or crossing clean through, is what must never happen.  The
     * test insets the box by {@link #BOX_INSET} so an endpoint lying exactly on the edge does not count.
     */
    private static int boxesTraversed(final List<Pt> route, final List<Box> boxes) {
        int count = 0;
        for (int i = 1; i < route.size(); i++) {
            for (final Box box : boxes) {
                if (segmentEntersBox(route.get(i - 1), route.get(i), box)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Whether an axis-aligned segment has any part strictly inside {@code box}. */
    private static boolean segmentEntersBox(final Pt a, final Pt b, final Box box) {
        final double x0 = box.x() + BOX_INSET;
        final double x1 = box.x() + box.w() - BOX_INSET;
        final double y0 = box.y() + BOX_INSET;
        final double y1 = box.y() + box.h() - BOX_INSET;
        if (x1 <= x0 || y1 <= y0) {
            return false;                              // no interior to speak of
        }
        if (Math.abs(a.y() - b.y()) <= RouteGeometry.ORTHO_TOL) {        // a horizontal run
            if (a.y() <= y0 || a.y() >= y1) {
                return false;
            }
            return Math.max(a.x(), b.x()) > x0 && Math.min(a.x(), b.x()) < x1;
        }
        if (Math.abs(a.x() - b.x()) <= RouteGeometry.ORTHO_TOL) {        // a vertical run
            if (a.x() <= x0 || a.x() >= x1) {
                return false;
            }
            return Math.max(a.y(), b.y()) > y0 && Math.min(a.y(), b.y()) < y1;
        }
        return false;                                  // a diagonal run: never in an orthogonalised route
    }
}
