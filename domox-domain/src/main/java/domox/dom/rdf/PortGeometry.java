package domox.dom.rdf;

import java.util.List;

import domox.dom.rdf.geometry.Box;
import domox.dom.rdf.geometry.Pt;

/**
 * <b>Port geometry</b>: where a connector attaches to a class box, and which way it leaves or arrives.
 *
 * <p>This is the small vocabulary the UML reconstruction shares between the router and the port layout —
 * the four edges of a box (numbered {@code 0} top, {@code 1} bottom, {@code 2} left, {@code 3} right), the
 * four directions ({@link #RIGHT}, {@link #LEFT}, {@link #DOWN}, {@link #UP}, plus {@link #NONE} for
 * "any"), the point a fraction of the way along an edge, the <em>middle</em> of the edge a raw attachment
 * hint belongs to, and the inward normal a connector arrives along.</p>
 *
 * <p>It used to live inside {@link OrthogonalRouter}, which made that class responsible for two things at
 * once: the A* search over the Hanan grid, and the arithmetic of where a box's attachment points are.
 * {@link PortLayout} — which chooses the ports and never routes anything — depended on the router only for
 * these few helpers; it now depends on this class instead, and the router depends on the same vocabulary
 * without owning it.</p>
 *
 * <p>Pure functions over {@link Box} and {@link Pt} only — no diagram, no RDF, no state.</p>
 */
final class PortGeometry {

    /** How far a forced departure runs straight out of its box before it may turn. */
    static final double DEPARTURE_STUB = 20.0;

    /** No direction — "any", or a route that has not started yet. */
    static final int NONE = 0;
    /** The four directions a move can travel in, and the inward normal a connector arrives along. */
    static final int RIGHT = 1;
    static final int LEFT = 2;
    static final int DOWN = 3;
    static final int UP = 4;

    private PortGeometry() {
    }

    /** The attachment points to try on a box: the given one, or the four edge midpoints. */
    static List<Pt> ports(final Box box, final Pt hint) {
        if (hint != null && nearBoundary(box, hint)) {
            return List.of(hint);                               // the caller placed it on the edge already
        }
        return List.of(
                new Pt(box.x() + box.w() / 2, box.y()),
                new Pt(box.x() + box.w() / 2, box.y() + box.h()),
                new Pt(box.x(), box.y() + box.h() / 2),
                new Pt(box.x() + box.w(), box.y() + box.h() / 2));
    }

    /** Whether {@code point} is close enough to the box to have been an attachment on it. */
    static boolean nearBoundary(final Box box, final Pt point) {
        final double tolerance = 6;
        return point.x() >= box.x() - tolerance && point.x() <= box.x() + box.w() + tolerance
                && point.y() >= box.y() - tolerance && point.y() <= box.y() + box.h() + tolerance;
    }

    /** The box edge a point attaches to: {@code 0} top, {@code 1} bottom, {@code 2} left, {@code 3} right. */
    static int edgeOf(final Box box, final Pt point) {
        final double left = Math.abs(point.x() - box.x());
        final double right = Math.abs(point.x() - (box.x() + box.w()));
        final double top = Math.abs(point.y() - box.y());
        final double bottom = Math.abs(point.y() - (box.y() + box.h()));
        final double nearest = Math.min(Math.min(left, right), Math.min(top, bottom));
        if (nearest == left) {
            return 2;
        }
        if (nearest == right) {
            return 3;
        }
        return nearest == top ? 0 : 1;
    }

    /** A point {@code fraction} of the way along that edge — {@code 0} at one corner, {@code 1} at the other. */
    static Pt pointOnEdge(final Box box, final int edge, final double fraction) {
        return switch (edge) {
            case 0 -> new Pt(box.x() + box.w() * fraction, box.y());
            case 1 -> new Pt(box.x() + box.w() * fraction, box.y() + box.h());
            case 2 -> new Pt(box.x(), box.y() + box.h() * fraction);
            default -> new Pt(box.x() + box.w(), box.y() + box.h() * fraction);
        };
    }

    /**
     * The <b>middle</b> of the edge {@code hint} attaches to — never a corner.
     * <p>
     * The edge is the one the hint is nearest to (the same choice the raw attachment made), and only the
     * point along it moves: to the middle.  A connector that leaves or enters at a corner reads as a
     * mistake, while which side it uses is the model author's intent and is kept.
     */
    static Pt edgeMidpoint(final Box box, final Pt hint) {
        return pointOnEdge(box, edgeOf(box, hint), 0.5);
    }

    /**
     * The direction a connector travels in when it enters {@code box} at {@code port} — the inward normal
     * of the edge the port sits on.
     * <p>
     * Handing this to {@link OrthogonalRouter#route} as the required arrival direction is what makes every
     * generalization into one super-class come in along the same line, so they merge into a single trunk and
     * a single arrowhead.
     */
    static int inward(final Box box, final Pt port) {
        if (Math.abs(port.x() - box.x()) <= 0.5) {
            return RIGHT;
        }
        if (Math.abs(port.x() - (box.x() + box.w())) <= 0.5) {
            return LEFT;
        }
        if (Math.abs(port.y() - box.y()) <= 0.5) {
            return DOWN;
        }
        return UP;
    }
}
