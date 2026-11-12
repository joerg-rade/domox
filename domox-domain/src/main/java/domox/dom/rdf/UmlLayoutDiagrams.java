package domox.dom.rdf;

import domox.dom.rdf.geometry.Box;
import domox.dom.rdf.geometry.Bounds;
import domox.dom.rdf.geometry.Pt;
import domox.dom.rdf.geometry.Segment;
import domox.port.RdfGraphTO;
import jakarta.annotation.Priority;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconstructs a <b>UML class diagram</b> from the layout a UML/OntoUML export stores alongside its
 * model — the geometry {@link RdfGraphDiagrams} deliberately throws away.
 *
 * <p>A tool such as the OntoUML editor keeps the <em>picture</em>, not just the model: every
 * {@code ontouml:Diagram} lists its {@code ontouml:containsView}s; a {@code ClassView} carries a
 * {@code Rectangle} ({@code topLeftPosition} + {@code width}/{@code height}) and points at the class
 * it draws ({@code isViewOf}); a {@code RelationView} / {@code GeneralizationView} carries a
 * {@code Path} of {@code Point}s and names the two views it connects ({@code sourceView} /
 * {@code targetView}).  The boxes come back exactly as drawn; the connectors are then re-routed by
 * {@link OrthogonalRouter} — the two points where the export attached a path are kept, its bends are
 * not (see {@link #routeAll}).</p>
 *
 * <p>The JSON it produces carries its own geometry, so the client draws it statically:</p>
 * <pre>
 *   { "layout":"uml", "title":"…", "canvas":{"width":…,"height":…},
 *     "nodes":[{"id","name","kind","x","y","w","h"}],
 *     "links":[{"source","target","kind","label","points":[[x,y],…]}],
 *     "legend":[{"color","label","description"}] }
 * </pre>
 *
 * <p>Coordinates are translated so the drawing starts at {@code DiagramJsonWriter}'s padding and the
 * canvas is the drawing's bounding box plus that margin — an export's own coordinates are absolute
 * within a much larger sheet.  {@link DiagramJsonWriter} does that translation as it serialises.</p>
 */
@DomainService
@Named("domox.dom.rdf.UmlLayoutDiagrams")
@Priority(PriorityPrecedence.LATE)
public class UmlLayoutDiagrams {

    // -- public API ------------------------------------------------------------

    /**
     * Names of the diagrams the export ships, alphabetically.
     *
     * <p>Empty for a graph that is not a UML/OntoUML export — a plain OWL file has no views, so there
     * is no stored picture to reconstruct.</p>
     */
    @Programmatic
    public List<String> diagramNames(final RdfGraphTO graph) {
        final DiagramIndex index = DiagramIndex.of(graph);
        final List<String> names = new ArrayList<>();
        for (final String diagram : index.diagrams()) {
            final String name = index.displayNameOf(diagram);
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        Collections.sort(names);
        return names;
    }

    /**
     * Reconstructs the named diagram — its boxes and its connectors — as D3 JSON carrying geometry.
     *
     * @param diagramName the diagram's {@code ontouml:name}; blank picks the richest diagram (the one
     *                    with most views), so a caller that does not know the names still gets a
     *                    picture rather than nothing
     * @throws IllegalArgumentException when the graph ships no such diagram
     */
    @Programmatic
    public String renderLayout(final RdfGraphTO graph, final String diagramName) {
        final DiagramIndex index = DiagramIndex.of(graph);
        final String diagram = index.resolveDiagram(diagramName);
        final List<String> viewOrder = index.containsView.getOrDefault(diagram, List.of());

        // -- boxes: the diagram's own ClassViews, each with its rectangle --------
        final Map<String, Box> boxes = new LinkedHashMap<>();
        for (final String view : viewOrder) {
            if (DiagramIndex.ONTOUML_CLASS_VIEW.equals(index.type.get(view))) {
                final Box box = index.boxOf(view);
                if (box != null) {
                    boxes.put(view, box);
                }
            }
        }
        // Widen before the routes are read: their ends are clipped to the box edges, so a box widened
        // afterwards would swallow the arrowheads again.
        LabelLayout.widenToFitLabels(boxes, index::elementName);

        // -- connectors: resolved first, so every endpoint box is known before the routes are filtered
        final List<Route> routes = new ArrayList<>();
        for (final String view : viewOrder) {
            final String type = index.type.get(view);
            final String kind = DiagramIndex.ONTOUML_GENERALIZATION_VIEW.equals(type) ? Link.GENERALIZATION
                    : DiagramIndex.ONTOUML_RELATION_VIEW.equals(type) ? Link.ASSOCIATION
                    : null;
            if (kind == null) {
                continue;
            }
            final String source = index.sourceView.get(view);
            final String target = index.targetView.get(view);
            if (source == null || target == null) {
                continue;
            }
            // Both ends must be drawable boxes.  An endpoint the diagram lists only implicitly is still
            // drawn (it carries its own rectangle), but one without a rectangle — the export points a
            // RelationView at another RelationView, whose shape is a Path, say — would leave the
            // connector as a line into empty space, so it is skipped.
            final Box sourceBox = boxes.containsKey(source) ? boxes.get(source) : index.boxOf(source);
            final Box targetBox = boxes.containsKey(target) ? boxes.get(target) : index.boxOf(target);
            if (sourceBox == null || targetBox == null) {
                continue;
            }
            boxes.putIfAbsent(source, sourceBox);
            boxes.putIfAbsent(target, targetBox);
            routes.add(new Route(view, source, target, kind, sourceBox, targetBox));
        }

        if (boxes.isEmpty()) {
            throw new IllegalArgumentException("Diagram '" + index.displayNameOf(diagram)
                    + "' has no drawable class views");
        }

        final List<Link> links = new ArrayList<>();
        for (final Route route : routes) {
            links.add(new Link(route.source(), route.target(), route.kind(),
                    index.relationLabelOf(route.view()),
                    index.pointsOf(route.view(), route.targetBox(), route.sourceBox()),
                    true));
        }
        // Every connector is then routed afresh on the Hanan grid — orthogonal, never through a box, and
        // crossing as few of the routes already placed as possible (see OrthogonalRouter).
        routeAll(links, boxes);
        // -- the drawing's bounding box, which fixes the canvas the writer pads ----
        final Bounds bounds = Bounds.of(boxes.values(),
                links.stream().flatMap(link -> link.points().stream()).toList());

        final List<String> nodeIds = new ArrayList<>(boxes.keySet());
        nodeIds.sort(Comparator.comparing(index::elementName).thenComparing(id -> id));

        return DiagramJsonWriter.write(index.displayNameOf(diagram), bounds, nodeIds, boxes,
                index::elementName, links);
    }

    // -- helpers ---------------------------------------------------------------

    /**
     * Estimated width of a label, in model units, at the label font size — see {@link LabelMetrics}.
     * <p>
     * Kept as a public entry point for the guardrail test; it is a pure function and static, so Causeway
     * does not treat it as a member (see {@code localName}).
     */
    public static double estimatedLabelWidth(final String label) {
        return LabelMetrics.estimatedLabelWidth(label);
    }

    /**
     * Re-routes every connector with {@link OrthogonalRouter}: an orthogonal path that keeps clear of the
     * class boxes and of the routes already placed, so the picture no longer inherits the crossings the
     * export's own routing carries.
     * <p>
     * The export's stored path is still read first — not for its bends, but for <em>where it attached</em>:
     * its two ends say which edge of each box the connector left from, and the router starts and finishes
     * there, so a connector keeps the side of the box the model's author chose.  A connector whose stored
     * path was unusable has no such hint, and the router picks the cheapest edge pair itself.  When the
     * search finds nothing — a box it cannot get around within its region — the plain edge-pair route is
     * the safety net.
     */
    private static void routeAll(final List<Link> links, final Map<String, Box> boxes) {
        final List<Box> all = new ArrayList<>(boxes.values());
        final List<Segment> placed = new ArrayList<>();

        // Generalizations that share a super-class merge: they all arrive at the middle of the same edge of
        // that super-class, travelling along its inward normal, so their last segments are collinear — one
        // trunk, carrying one arrowhead (only the group's first connector draws it).
        final Map<String, Pt> attach = new LinkedHashMap<>();
        final Map<String, Integer> firstOfGroup = new LinkedHashMap<>();
        for (int i = 0; i < links.size(); i++) {
            final Link link = links.get(i);
            final Box superBox = boxes.get(link.source());
            if (!Link.GENERALIZATION.equals(link.kind()) || superBox == null) {
                continue;
            }
            firstOfGroup.putIfAbsent(link.source(), i);
            attach.computeIfAbsent(link.source(), id -> PortLayout.facingEdgeMidpoint(superBox, links, id, boxes));
        }

        // Shortest connectors first: a short route has the least room to detour, so it claims its direct
        // channel before a long one runs through it.  The output order is left alone — only the routing
        // order changes.
        final List<Integer> order = new ArrayList<>();
        for (int i = 0; i < links.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble((Integer i) -> span(links.get(i), boxes)));

        // How far each super-class's trunk already reaches — see the trunk handling below.
        final Map<String, Double> trunkReach = new LinkedHashMap<>();
        // Where each connector attaches — spread out along the edge when several share it.
        final Map<Integer, Pt> spread = PortLayout.spreadPorts(links, boxes);
        for (final int i : order) {
            final Link link = links.get(i);
            // The export starts a path at its targetView, so a route runs target -> source.
            final Box start = boxes.get(link.target());
            final Box end = boxes.get(link.source());
            if (start == null || end == null) {
                continue;
            }
            final List<Pt> hint = link.points();
            final Pt from = hint.isEmpty() ? null : hint.getFirst();
            final Pt to = hint.isEmpty() ? null : hint.getLast();

            final Pt shared = Link.GENERALIZATION.equals(link.kind()) ? attach.get(link.source()) : null;
            // An inheritance edge must run along the edge's normal for a while before the arrowhead, so the
            // sub-classes' lines merge into one visible trunk instead of arriving sideways and turning in
            // the last two units.  The router therefore aims at a point that far outside the edge, and the
            // trunk into the box is appended.
            final Pt trunk = shared == null ? null : PortLayout.trunkPoint(end, shared, all);
            final Pt endPort = shared != null ? trunk : PortLayout.portFor(spread, i, false, end, to);
            final int arrival = shared != null ? OrthogonalRouter.inward(end, shared) : OrthogonalRouter.NONE;
            final Box endBox = shared != null ? new Box(trunk.x(), trunk.y(), 0, 0) : end;
            // An inheritance edge leaves the middle of its sub-class's TOP edge, going straight up — the
            // way a UML tool draws it, and the reason a sub-class reads as "below" its super-class.  When
            // the sub-class sits closer to its super-class than the stub is long, there is no room to rise
            // that far before the trunk: forcing it anyway would make the route overshoot and loop back, so
            // the direction is left free (the attachment point is still the middle of the top edge).
            final Pt fromPort = shared != null
                    ? new Pt(start.x() + start.w() / 2, start.y())
                    : PortLayout.portFor(spread, i, true, start, from);
            final int departure = shared != null && PortLayout.hasRoomToRise(fromPort, trunk)
                    ? OrthogonalRouter.UP : OrthogonalRouter.NONE;

            List<Pt> route = OrthogonalRouter.route(start, endBox, fromPort, endPort,
                    departure, arrival, all, placed);
            if (route.size() >= 2 && shared != null) {
                route = new ArrayList<>(route);
                route.add(shared);                              // the trunk into the super-class
            }
            if (route.size() < 2) {
                route = RouteScoring.bestFallbackRoute(start, end, all, placed);
            }
            if (route.size() < 2) {
                continue;
            }
            final boolean arrow = shared == null || i == firstOfGroup.get(link.source());
            links.set(i, new Link(link.source(), link.target(), link.kind(), link.label(), route, arrow));

            if (shared == null) {
                RouteScoring.addSegments(placed, route);
                continue;
            }
            // The route's last segment is the group's shared trunk: every member of the group runs along the
            // same line there, so on the picture it is ONE line and must count once.  Only the member that
            // reaches furthest out extends it — otherwise a group of four would charge every later route
            // four times for crossing a single line, and routes would detour around a line that is not there.
            RouteScoring.addSegments(placed, route.subList(0, route.size() - 1));
            final Pt trunkEnd = route.getLast();
            final Pt trunkFrom = route.get(route.size() - 2);
            final double reach = Math.abs(trunkFrom.x() - trunkEnd.x()) + Math.abs(trunkFrom.y() - trunkEnd.y());
            final Double previous = trunkReach.get(link.source());
            if (previous == null || reach > previous) {
                trunkReach.put(link.source(), reach);
                placed.add(new Segment(trunkEnd, trunkFrom));
            }
        }
    }

    /** How far a connector reaches — the distance between its two boxes' centres, for the routing order. */
    private static double span(final Link link, final Map<String, Box> boxes) {
        final Box start = boxes.get(link.target());
        final Box end = boxes.get(link.source());
        return start == null || end == null ? 0 : RouteGeometry.centreDistance(start, end);
    }

    // -- value carriers --------------------------------------------------------

    /** A connector as resolved from the graph, before its route is read. */
    private record Route(String view, String source, String target, String kind,
                         Box sourceBox, Box targetBox) { }

}
