package domox.dom.rdf;

import java.util.ArrayList;
import java.util.List;

import domox.dom.rdf.geometry.Box;
import domox.dom.rdf.geometry.Pt;

/**
 * The <b>route geometry</b> of the UML reconstruction: turning the polyline a UML/OntoUML export stores
 * for a connector into one that can actually be drawn.
 *
 * <p>The export states its path points in an order that is <em>not</em> the drawing order, sometimes
 * leaves a point belonging to another connector in the path, ends a path inside the box it points at, and
 * draws a third of its segments as diagonals.  This class is the whole sanitation stage that turns that
 * raw list into a clean polyline: it drops the strays, re-chains the points end to end from the box the
 * route starts at, pulls the two ends out to the box edges, and makes every segment horizontal or
 * vertical.  It works on plain {@link Pt} lists and {@link Box}es only — no knowledge of connectors,
 * diagrams, or RDF — so it is as testable as the primitives it operates on.</p>
 *
 * <p>Extracted from {@code UmlLayoutDiagrams}; the connector routing that <em>consumes</em> these routes
 * is {@link OrthogonalRouter}.</p>
 */
final class RouteGeometry {

    /**
     * Slack, in model units, added to the distance between a connector's two boxes when deciding whether
     * a bend still belongs to its route.  The floor keeps a self-loop's bow — whose two boxes coincide,
     * so their centre distance is zero — from being mistaken for a stray.
     */
    private static final int ROUTE_SLACK = 60;

    /**
     * How far from <em>both</em> of its boxes a route's end may lie and still be usable.
     * <p>
     * The export sometimes leaves a point belonging to another connector at the end of a path, which
     * would draw the line — and, for a generalization, the arrowhead — into empty space.  Such a path is
     * discarded in favour of a straight line between the two boxes.
     */
    private static final int END_SLACK = 30;

    /**
     * Two coordinates closer than this count as aligned, when making a route orthogonal.
     * <p>
     * Package-private: the route <em>scoring</em> that stays in {@code UmlLayoutDiagrams} tests
     * axis-alignment against the same tolerance.
     */
    static final double ORTHO_TOL = 1.0;

    /**
     * Makes a route orthogonal: every segment runs horizontally or vertically, as a UML tool draws its
     * connectors.
     * <p>
     * The export's own routes are a third diagonal (241 of 842 segments in the bundled model), which
     * reads as hand-drawn rather than laid out.  A diagonal run is split by one corner, placed so the
     * route leaves the box perpendicular to the edge it starts on, arrives perpendicular to the edge it
     * enters, and turns a proper 90 degrees in between.  The corner stays inside the segment's own
     * bounding box, so the route never wanders further than the diagonal it replaces.
     */
    static List<Pt> orthogonalise(final List<Pt> points, final Box startBox, final Box endBox) {
        if (points.size() < 2) {
            return points;
        }
        final List<Pt> out = new ArrayList<>();
        out.add(points.getFirst());
        boolean previousWasHorizontal = false;
        for (int i = 1; i < points.size(); i++) {
            final Pt prev = out.getLast();
            final Pt cur = points.get(i);
            final double dx = Math.abs(cur.x() - prev.x());
            final double dy = Math.abs(cur.y() - prev.y());
            if (dx > ORTHO_TOL && dy > ORTHO_TOL) {
                final boolean first = i == 1;
                final boolean last = i == points.size() - 1;
                // The axis the run must leave on: square to the box it starts at, else a 90-degree turn.
                final boolean firstVertical = first && startBox != null
                        ? !onVerticalEdge(prev, startBox)
                        : previousWasHorizontal;
                // The axis it must arrive on: square to the box it enters, else the complementary axis.
                final boolean lastVertical = last && endBox != null
                        ? !onVerticalEdge(cur, endBox)
                        : !firstVertical;
                if (firstVertical == lastVertical) {
                    // Both ends demand the same axis, so one corner cannot serve both — a staircase does.
                    final double midX = (prev.x() + cur.x()) / 2;
                    final double midY = (prev.y() + cur.y()) / 2;
                    if (firstVertical) {
                        out.add(new Pt(prev.x(), midY));
                        out.add(new Pt(cur.x(), midY));
                    } else {
                        out.add(new Pt(midX, prev.y()));
                        out.add(new Pt(midX, cur.y()));
                    }
                    previousWasHorizontal = !firstVertical;
                } else {
                    out.add(firstVertical ? new Pt(prev.x(), cur.y()) : new Pt(cur.x(), prev.y()));
                    previousWasHorizontal = firstVertical;
                }
            } else {
                previousWasHorizontal = dx > ORTHO_TOL;
            }
            out.add(cur);
        }
        return removeSpurs(out);
    }

    /**
     * Removes a point that only doubles back on its neighbour.
     * <p>
     * The export's routes contain short spurs — a bend eight units to the left of the one before it — and
     * the orthogonalisation turns each into a visible kink.  Dropping the middle point leaves a straight
     * run instead, because a reversal means the two neighbours already share an axis.
     */
    private static List<Pt> removeSpurs(final List<Pt> points) {
        final List<Pt> out = new ArrayList<>(points);
        boolean removed = true;
        while (removed && out.size() > 2) {
            removed = false;
            for (int i = 1; i < out.size() - 1; i++) {
                if (isReversal(out.get(i - 1), out.get(i), out.get(i + 1))) {
                    out.remove(i);
                    removed = true;
                    break;
                }
            }
        }
        return out;
    }

    /** Whether {@code b} sits between {@code a} and {@code c} on one axis — i.e. the run doubles back. */
    private static boolean isReversal(final Pt a, final Pt b, final Pt c) {
        final double abx = b.x() - a.x();
        final double aby = b.y() - a.y();
        final double bcx = c.x() - b.x();
        final double bcy = c.y() - b.y();
        return abx * bcx + aby * bcy < 0 && Math.abs(abx * bcy - aby * bcx) < 1e-9;
    }

    /** Whether {@code point} sits on the left or right edge of {@code box} rather than top or bottom. */
    private static boolean onVerticalEdge(final Pt point, final Box box) {
        return Math.abs(point.x() - box.x()) <= ORTHO_TOL
                || Math.abs(point.x() - (box.x() + box.w())) <= ORTHO_TOL;
    }

    /**
     * Re-orders a path's points into a connected polyline, starting from the point that sits on
     * {@code startBox} and always taking the nearest remaining point next.
     * <p>
     * Returns the points untouched when the start cannot be located — the export's own order is better
     * than an invented one.
     */
    static List<Pt> chain(final List<Pt> points, final Box startBox) {
        if (startBox == null) {
            return points;
        }
        if (points.size() == 2) {
            // A straight line needs no chaining, but it must still run from the end the export starts at
            // — otherwise the arrow at the far end would point the wrong way.
            return !contains(startBox, points.get(0)) && contains(startBox, points.get(1))
                    ? List.of(points.get(1), points.get(0))
                    : points;
        }
        int start = -1;
        for (int i = 0; i < points.size(); i++) {
            if (contains(startBox, points.get(i))) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return points;
        }
        final List<Pt> remaining = new ArrayList<>(points);
        final List<Pt> out = new ArrayList<>();
        out.add(remaining.remove(start));
        while (!remaining.isEmpty()) {
            final Pt last = out.getLast();
            int nearest = 0;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                final double dx = remaining.get(i).x() - last.x();
                final double dy = remaining.get(i).y() - last.y();
                final double distance = dx * dx + dy * dy;
                if (distance < best) {
                    best = distance;
                    nearest = i;
                }
            }
            out.add(remaining.remove(nearest));
        }
        return out;
    }

    /** Whether {@code point} lies on (or inside) the box, within a small tolerance. */
    private static boolean contains(final Box box, final Pt point) {
        final double tolerance = 2;
        return point.x() >= box.x() - tolerance && point.x() <= box.x() + box.w() + tolerance
                && point.y() >= box.y() - tolerance && point.y() <= box.y() + box.h() + tolerance;
    }

    /**
     * Pulls a route's two ends out to the edges of the boxes it runs between.
     * <p>
     * A UML export ends a path <em>inside</em> the box it points at — 14 units in, in this model — and
     * the boxes are drawn on top of the connectors, so an arrowhead whose tip sat under the box was
     * painted over.  Only a path that happened to end exactly on the boundary showed an arrow at all,
     * which is why one generalization out of thirty-six appeared.
     */
    static List<Pt> clipToBoxes(final List<Pt> points, final Box startBox, final Box endBox) {
        if (points.size() < 2) {
            return points;
        }
        final List<Pt> clipped = new ArrayList<>(points);
        clipped.set(0, clipToEdge(points.get(1), points.get(0), startBox));
        final int last = clipped.size() - 1;
        clipped.set(last, clipToEdge(clipped.get(last - 1), clipped.get(last), endBox));
        return clipped;
    }

    /**
     * The point where the segment {@code from → to} crosses into {@code box}.
     *
     * @return the entry point, or {@code to} unchanged when the segment never enters the box
     */
    private static Pt clipToEdge(final Pt from, final Pt to, final Box box) {
        if (box == null || !contains(box, to)) {
            return to;
        }
        final double dx = to.x() - from.x();
        final double dy = to.y() - from.y();
        double enter = 0;
        double exit = 1;
        if (dx == 0) {
            if (from.x() < box.x() || from.x() > box.x() + box.w()) {
                return to;
            }
        } else {
            final double a = (box.x() - from.x()) / dx;
            final double b = (box.x() + box.w() - from.x()) / dx;
            enter = Math.max(enter, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
        }
        if (dy == 0) {
            if (from.y() < box.y() || from.y() > box.y() + box.h()) {
                return to;
            }
        } else {
            final double a = (box.y() - from.y()) / dy;
            final double b = (box.y() + box.h() - from.y()) / dy;
            enter = Math.max(enter, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
        }
        if (enter > exit || enter <= 0 || enter >= 1) {
            return to;
        }
        return new Pt(from.x() + enter * dx, from.y() + enter * dy);
    }

    /**
     * Whether {@code point} is too far from a route's two boxes to belong to it.
     * <p>
     * The export sometimes leaves a point belonging to <em>another</em> connector in a path — a UFO-C
     * generalization carrying points from the region where the {@code Action Universal} views sit, say —
     * which draws a line off into empty space.  A genuine bend lies between the two boxes, so anything
     * farther from both of them than the boxes are from each other is dropped.
     */
    static boolean straysFrom(final Pt point, final Box startBox, final Box endBox) {
        if (startBox == null || endBox == null) {
            return false;
        }
        final double limit = Math.max(ROUTE_SLACK, centreDistance(startBox, endBox));
        return distanceTo(startBox, point) > limit && distanceTo(endBox, point) > limit;
    }

    /** Whether {@code point} lies farther than {@link #END_SLACK} from both boxes. */
    static boolean farFromBothBoxes(final Pt point, final Box startBox, final Box endBox) {
        if (startBox == null || endBox == null) {
            return false;
        }
        return distanceTo(startBox, point) > END_SLACK && distanceTo(endBox, point) > END_SLACK;
    }

    /** Distance from {@code point} to the nearest edge of {@code box} — zero when it is inside. */
    private static double distanceTo(final Box box, final Pt point) {
        final double dx = Math.max(Math.max(box.x() - point.x(), 0), point.x() - (box.x() + box.w()));
        final double dy = Math.max(Math.max(box.y() - point.y(), 0), point.y() - (box.y() + box.h()));
        return Math.hypot(dx, dy);
    }

    /** Distance between two boxes' centres — how far apart a route's ends are. */
    static double centreDistance(final Box first, final Box second) {
        return Math.hypot(
                (first.x() + first.w() / 2) - (second.x() + second.w() / 2),
                (first.y() + first.h() / 2) - (second.y() + second.h() / 2));
    }
}
