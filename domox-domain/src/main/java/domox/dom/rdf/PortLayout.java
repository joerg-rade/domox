package domox.dom.rdf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import domox.dom.rdf.geometry.Box;
import domox.dom.rdf.geometry.Pt;

/**
 * <b>Port layout</b> for the UML reconstruction: where each connector attaches on its box, and where an
 * inheritance trunk merges into its super-class.
 *
 * <p>A box has only four edge midpoints, so every connector leaving the same side of a box would be
 * drawn from the very same point and could not be told apart.  The connectors that share an edge are
 * therefore spaced evenly along it ({@link #spreadPorts}), still clear of both corners and in the order
 * the model gave them.  An inheritance edge is the exception: it keeps the fixed place of its own
 * ({@link #facingEdgeMidpoint}) and only <em>reserves</em> it, so the associations on that edge are
 * pushed clear of the middle ({@link #clearOfMiddle}).</p>
 *
 * <p>All the generalizations into one super-class arrive along that super-class's inward normal, at the
 * point {@link #facingEdgeMidpoint} picks, each first reaching {@link #trunkPoint} — the stub that makes
 * them visibly merge into one trunk instead of arriving sideways.</p>
 *
 * <p>Works on {@link Box}es, {@link Pt}s and {@link Link}s only — no knowledge of diagrams or RDF.
 * Extracted from {@code UmlLayoutDiagrams}; the routes these ports serve are shaped by
 * {@link RouteGeometry}, scored by {@link RouteScoring} and laid out by {@link OrthogonalRouter}.</p>
 */
final class PortLayout {

    /**
     * How much vertical offset a super-class's sub-classes must have, relative to their horizontal offset,
     * before the inheritance trunk is placed on the bottom (or top) edge rather than on a side — see
     * {@link #facingEdgeMidpoint}.
     */
    private static final double TRUNK_VERTICAL_BIAS = 0.25;

    /**
     * How far an inheritance trunk runs along the super-class edge's normal before the arrowhead — long
     * enough that the sub-classes' lines visibly merge into one trunk, rather than arriving sideways and
     * turning in the last unit or two.
     */
    private static final double TRUNK_STUB = 24.0;

    /** Kept clear between the trunk's merge point and whatever box shortened it. */
    private static final double TRUNK_GAP = 4.0;

    /**
     * Where each connector attaches on its box, spread along the edge it shares with the others.
     * <p>
     * A box's four edge midpoints are where every connector would otherwise attach, so several
     * associations leaving one side of a box would be drawn from the very same point and could not be told
     * apart.  Connectors that share an edge are therefore spaced evenly along it — still clear of both
     * corners, and in the order the model gave them.
     * <p>
     * An inheritance edge keeps its own fixed place (the sub-class's top middle, the super-class's facing
     * edge), so it does not take part; it only <em>reserves</em> that place, and the associations on the
     * same edge are pushed clear of it.
     *
     * @return the port for each connector end, keyed by {@code linkIndex * 2 + (start ? 0 : 1)}; an end
     *         that is not in the map is left to the router
     */
    static Map<Integer, Pt> spreadPorts(final List<Link> links, final Map<String, Box> boxes) {
        final Map<EdgeKey, List<End>> groups = new LinkedHashMap<>();
        final Set<EdgeKey> reserved = new LinkedHashSet<>();
        for (int i = 0; i < links.size(); i++) {
            final Link link = links.get(i);
            final Box start = boxes.get(link.target());
            final Box end = boxes.get(link.source());
            if (Link.GENERALIZATION.equals(link.kind())) {
                if (start != null) {
                    reserved.add(new EdgeKey(link.target(), 0));    // the sub-class's top middle
                }
                if (end != null) {
                    final Pt attach = facingEdgeMidpoint(end, links, link.source(), boxes);
                    reserved.add(new EdgeKey(link.source(), OrthogonalRouter.edgeOf(end, attach)));
                }
                continue;
            }
            final List<Pt> hint = link.points();
            final Pt from = hint.isEmpty() ? null : hint.getFirst();
            final Pt to = hint.isEmpty() ? null : hint.getLast();
            attachAt(groups, boxes, link.target(), i, true, attachPoint(start, end, from));
            attachAt(groups, boxes, link.source(), i, false, attachPoint(end, start, to));
        }

        final Map<Integer, Pt> ports = new LinkedHashMap<>();
        for (final Map.Entry<EdgeKey, List<End>> group : groups.entrySet()) {
            final List<End> ends = group.getValue();
            final boolean keepMiddleFree = reserved.contains(group.getKey());
            ends.sort(Comparator.comparingDouble(End::along));
            final Box box = boxes.get(group.getKey().box());
            for (int k = 0; k < ends.size(); k++) {
                final double fraction = (k + 1.0) / (ends.size() + 1.0);
                ports.put(ends.get(k).link() * 2 + (ends.get(k).start() ? 0 : 1),
                        OrthogonalRouter.pointOnEdge(box, group.getKey().edge(),
                                keepMiddleFree ? clearOfMiddle(fraction) : fraction));
            }
        }
        return ports;
    }

    /**
     * Where a connector end attaches: the stored point when it lies on the box, else the middle of the edge
     * facing the box it runs to.  Every end therefore has a real attachment, so every one of them can take
     * part in the spreading.
     */
    private static Pt attachPoint(final Box box, final Box other, final Pt hint) {
        if (box != null && hint != null && OrthogonalRouter.nearBoundary(box, hint)) {
            return hint;
        }
        return facingEdgeMidpoint(box, other);
    }

    /** Records that a connector end attaches to {@code id}'s box, if the stored point is on its outline. */
    private static void attachAt(final Map<EdgeKey, List<End>> groups, final Map<String, Box> boxes,
                                 final String id, final int link, final boolean start, final Pt at) {
        final Box box = boxes.get(id);
        if (box == null || at == null || !OrthogonalRouter.nearBoundary(box, at)) {
            return;
        }
        final int edge = OrthogonalRouter.edgeOf(box, at);
        final double along = switch (edge) {
            case 0, 1 -> box.w() == 0 ? 0.5 : (at.x() - box.x()) / box.w();
            default -> box.h() == 0 ? 0.5 : (at.y() - box.y()) / box.h();
        };
        groups.computeIfAbsent(new EdgeKey(id, edge), key -> new ArrayList<>())
                .add(new End(link, start, along));
    }

    /**
     * Squeezes a position towards the ends of the edge, leaving the middle free for an inheritance trunk —
     * so an association and the generalization it shares an edge with do not start from the same point.
     */
    private static double clearOfMiddle(final double fraction) {
        final double gap = 0.1;                                 // the middle tenth of the edge stays free
        return fraction < 0.5
                ? fraction * (1 - gap)
                : 0.5 + gap / 2 + (fraction - 0.5) * (1 - gap);
    }

    /**
     * The port one end of a connector uses: the spread position when it shares its edge with others, else
     * the middle of the edge the model attached it to, else {@code null} — and then the router picks the
     * cheapest of the box's four edge midpoints itself.
     */
    static Pt portFor(final Map<Integer, Pt> spread, final int link, final boolean start,
                      final Box box, final Pt hint) {
        final Pt spreadPort = spread.get(link * 2 + (start ? 0 : 1));
        if (spreadPort != null) {
            return spreadPort;
        }
        return hint == null ? null : OrthogonalRouter.edgeMidpoint(box, hint);
    }

    /**
     * The middle of the edge of {@code box} that faces {@code other}.
     * <p>
     * A connector whose stored path was unusable has no attachment of its own, so it takes the edge facing
     * the box it runs to — which lets it take part in the spreading like every other connector, instead of
     * the router landing it on whatever midpoint it likes and possibly on top of another.
     */
    private static Pt facingEdgeMidpoint(final Box box, final Box other) {
        if (box == null || other == null) {
            return null;
        }
        final double dx = (other.x() + other.w() / 2) - (box.x() + box.w() / 2);
        final double dy = (other.y() + other.h() / 2) - (box.y() + box.h() / 2);
        return Math.abs(dx) >= Math.abs(dy)
                ? OrthogonalRouter.pointOnEdge(box, dx >= 0 ? 3 : 2, 0.5)
                : OrthogonalRouter.pointOnEdge(box, dy >= 0 ? 1 : 0, 0.5);
    }

    /**
     * The middle of the super-class edge that faces its sub-classes — where every generalization into it
     * arrives.
     * <p>
     * Inheritance reads as a <b>vertical tree</b> — the super-class above, its sub-classes below, the arrows
     * pointing up into it — so the trunk enters the <b>bottom</b> edge when the sub-classes are below (and
     * the top edge when they are above).  Averaging the direction to the sub-classes and taking the edge
     * whose normal points that way is not enough on its own: a wide fan of sub-classes that sits to one side
     * has a larger horizontal offset than vertical, and the trunk would then enter the <em>side</em> of the
     * super-class while every sub-class is underneath it.  The vertical therefore wins unless the
     * sub-classes are essentially level with the box ({@link #TRUNK_VERTICAL_BIAS}), where the side they are
     * on is the only thing to go by.
     */
    static Pt facingEdgeMidpoint(final Box superBox, final List<Link> links, final String superId,
                                 final Map<String, Box> boxes) {
        double dx = 0;
        double dy = 0;
        for (final Link link : links) {
            if (!Link.GENERALIZATION.equals(link.kind()) || !superId.equals(link.source())) {
                continue;
            }
            final Box sub = boxes.get(link.target());
            if (sub == null) {
                continue;
            }
            dx += (sub.x() + sub.w() / 2) - (superBox.x() + superBox.w() / 2);
            dy += (sub.y() + sub.h() / 2) - (superBox.y() + superBox.h() / 2);
        }
        if (Math.abs(dy) > TRUNK_VERTICAL_BIAS * Math.abs(dx)) {
            return OrthogonalRouter.pointOnEdge(superBox, dy >= 0 ? 1 : 0, 0.5);
        }
        return OrthogonalRouter.pointOnEdge(superBox, dx >= 0 ? 3 : 2, 0.5);
    }

    /**
     * The point the trunk should reach: {@link #TRUNK_STUB} outside {@code attach} along its edge's outward
     * normal — shortened when another box is in the way, which happens when a sub-class sits just below its
     * super-class.  Aiming at a point inside that sub-class would make the search fail outright and drop the
     * connector back to the side-approaching fallback.
     */
    static Pt trunkPoint(final Box box, final Pt attach, final List<Box> boxes) {
        final int edge = OrthogonalRouter.edgeOf(box, attach);
        final double dx = edge == 2 ? -1 : edge == 3 ? 1 : 0;
        final double dy = edge == 0 ? -1 : edge == 1 ? 1 : 0;

        double stub = TRUNK_STUB;
        for (final Box other : boxes) {
            final double distance = entryAlong(attach, dx, dy, other);
            if (distance > 0) {
                stub = Math.min(stub, distance - TRUNK_GAP);
            }
        }
        stub = Math.max(stub, 2);
        return new Pt(attach.x() + dx * stub, attach.y() + dy * stub);
    }

    /**
     * How far along the ray from {@code from} in direction {@code (dx, dy)} a box is entered — {@code 0}
     * when the ray misses it or runs away from it.
     */
    private static double entryAlong(final Pt from, final double dx, final double dy, final Box box) {
        final double tolerance = 0.5;
        if (dx != 0) {
            if (from.y() <= box.y() + tolerance || from.y() >= box.y() + box.h() - tolerance) {
                return 0;
            }
            final double edge = dx > 0 ? box.x() : box.x() + box.w();
            final double distance = (edge - from.x()) * dx;
            return distance > 0 ? distance : 0;
        }
        if (from.x() <= box.x() + tolerance || from.x() >= box.x() + box.w() - tolerance) {
            return 0;
        }
        final double edge = dy > 0 ? box.y() : box.y() + box.h();
        final double distance = (edge - from.y()) * dy;
        return distance > 0 ? distance : 0;
    }

    /**
     * Whether a sub-class's top edge has room to leave straight up for the full departure stub before the
     * trunk point.
     * <p>
     * Only a route that has to <em>rise</em> to the trunk can overshoot it: when the trunk is below the
     * sub-class (its super-class is underneath) the route descends and the stub is always safe.
     */
    static boolean hasRoomToRise(final Pt fromPort, final Pt trunk) {
        final double rise = fromPort.y() - trunk.y();
        return rise <= 0 || rise >= OrthogonalRouter.DEPARTURE_STUB;
    }

    // -- value carriers --------------------------------------------------------

    /** One edge of one box — the connectors that attach there share it. */
    private record EdgeKey(String box, int edge) { }

    /** One end of a connector: which link, which end, and where along its edge the model attached it. */
    private record End(int link, boolean start, double along) { }
}
