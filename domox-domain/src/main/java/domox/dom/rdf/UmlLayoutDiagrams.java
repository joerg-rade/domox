package domox.dom.rdf;

import domox.port.RdfGraphTO;
import domox.port.RdfTripleTO;
import jakarta.annotation.Priority;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * <p>Coordinates are translated so the drawing starts at {@code PADDING} and the canvas is the
 * drawing's bounding box plus that margin — an export's own coordinates are absolute within a much
 * larger sheet.</p>
 */
@DomainService
@Named("domox.dom.rdf.UmlLayoutDiagrams")
@Priority(PriorityPrecedence.LATE)
public class UmlLayoutDiagrams {

    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

    // -- OntoUML vocabulary (https://w3id.org/ontouml#) -------------------------
    private static final String ONTOUML_NS = "https://w3id.org/ontouml#";
    private static final String ONTOUML_NAME = ONTOUML_NS + "name";
    private static final String ONTOUML_DIAGRAM = ONTOUML_NS + "Diagram";
    private static final String ONTOUML_CONTAINS_VIEW = ONTOUML_NS + "containsView";
    private static final String ONTOUML_CLASS_VIEW = ONTOUML_NS + "ClassView";
    private static final String ONTOUML_RELATION_VIEW = ONTOUML_NS + "RelationView";
    private static final String ONTOUML_GENERALIZATION_VIEW = ONTOUML_NS + "GeneralizationView";
    private static final String ONTOUML_IS_VIEW_OF = ONTOUML_NS + "isViewOf";
    private static final String ONTOUML_SHAPE = ONTOUML_NS + "shape";
    private static final String ONTOUML_SOURCE_VIEW = ONTOUML_NS + "sourceView";
    private static final String ONTOUML_TARGET_VIEW = ONTOUML_NS + "targetView";
    private static final String ONTOUML_TOP_LEFT = ONTOUML_NS + "topLeftPosition";
    private static final String ONTOUML_WIDTH = ONTOUML_NS + "width";
    private static final String ONTOUML_HEIGHT = ONTOUML_NS + "height";
    private static final String ONTOUML_X = ONTOUML_NS + "xCoordinate";
    private static final String ONTOUML_Y = ONTOUML_NS + "yCoordinate";
    private static final String ONTOUML_POINT = ONTOUML_NS + "point";

    /** Margin drawn around the reconstructed picture, in model units. */
    private static final int PADDING = 24;

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

    /** Padding kept inside a box, around its label, when widening the box to fit. */
    private static final int LABEL_PADDING = 14;

    /** Two coordinates closer than this count as aligned, when making a route orthogonal. */
    private static final double ORTHO_TOL = 1.0;

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

    /** Kept clear between two neighbouring boxes so a widened one does not touch the next. */
    private static final int LABEL_GAP = 8;

    /**
     * Safety factor on the estimated label width — the domain cannot measure the real font, so it errs
     * wide.  A narrower font than the one measured just leaves a slightly roomier box.
     */
    private static final double LABEL_SAFETY = 1.10;

    /** Advance width per lower-case letter, at the label font (12px bold sans-serif), in model units. */
    private static final double[] LOWER = {
            6.7, 7.3, 6.7, 7.3, 6.7, 4.0, 7.3, 7.3, 3.3, 3.3, 6.7, 3.3, 10.7,
            7.3, 7.3, 7.3, 7.3, 4.7, 6.7, 4.0, 7.3, 6.7, 9.3, 6.7, 6.7, 6.0};

    /** Advance width per upper-case letter, same conditions. */
    private static final double[] UPPER = {
            8.7, 8.7, 8.7, 8.7, 8.0, 7.3, 9.3, 8.7, 3.3, 6.7, 8.7, 7.3, 10.0,
            8.7, 9.3, 8.0, 9.3, 8.7, 8.0, 7.3, 8.7, 8.0, 11.3, 8.0, 8.0, 7.3};

    /** Fallback box when a view carries no rectangle (so a class is never drawn as a point). */
    private static final double DEFAULT_W = 140;
    private static final double DEFAULT_H = 60;

    /** The model element kinds the reconstructed nodes carry. */
    private static final String NODE_KIND = "Class";
    /** The link kinds; the client picks the stroke colour and the arrowhead from these. */
    private static final String LINK_ASSOCIATION = "association";
    private static final String LINK_GENERALIZATION = "generalization";

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

    // -- public API ------------------------------------------------------------

    /**
     * Names of the diagrams the export ships, alphabetically.
     *
     * <p>Empty for a graph that is not a UML/OntoUML export — a plain OWL file has no views, so there
     * is no stored picture to reconstruct.</p>
     */
    @Programmatic
    public List<String> diagramNames(final RdfGraphTO graph) {
        final Index index = Index.of(graph);
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
        final Index index = Index.of(graph);
        final String diagram = index.resolveDiagram(diagramName);
        final List<String> viewOrder = index.containsView.getOrDefault(diagram, List.of());

        // -- boxes: the diagram's own ClassViews, each with its rectangle --------
        final Map<String, Box> boxes = new LinkedHashMap<>();
        for (final String view : viewOrder) {
            if (ONTOUML_CLASS_VIEW.equals(index.type.get(view))) {
                final Box box = index.boxOf(view);
                if (box != null) {
                    boxes.put(view, box);
                }
            }
        }
        // Widen before the routes are read: their ends are clipped to the box edges, so a box widened
        // afterwards would swallow the arrowheads again.
        widenToFitLabels(boxes, index);

        // -- connectors: resolved first, so every endpoint box is known before the routes are filtered
        final List<Route> routes = new ArrayList<>();
        for (final String view : viewOrder) {
            final String type = index.type.get(view);
            final String kind = ONTOUML_GENERALIZATION_VIEW.equals(type) ? LINK_GENERALIZATION
                    : ONTOUML_RELATION_VIEW.equals(type) ? LINK_ASSOCIATION
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
        // -- shift the export's absolute coordinates into a padded canvas -------
        final Bounds bounds = Bounds.of(boxes, links);
        final double dx = PADDING - bounds.minX();
        final double dy = PADDING - bounds.minY();

        final List<String> nodeIds = new ArrayList<>(boxes.keySet());
        nodeIds.sort(Comparator.comparing(index::elementName).thenComparing(id -> id));

        final StringBuilder json = new StringBuilder(1024);
        json.append('{')
                .append("\"layout\":\"uml\",")
                .append("\"title\":\"").append(escape(index.displayNameOf(diagram))).append("\",")
                .append("\"canvas\":{\"width\":").append(num(bounds.width() + 2 * PADDING))
                .append(",\"height\":").append(num(bounds.height() + 2 * PADDING)).append("},");

        json.append("\"nodes\":[");
        boolean first = true;
        for (final String id : nodeIds) {
            final Box box = boxes.get(id);
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"id\":\"").append(escape(id)).append('"')
                    .append(",\"name\":\"").append(escape(index.elementName(id))).append('"')
                    .append(",\"kind\":\"").append(NODE_KIND).append('"')
                    .append(",\"x\":").append(num(box.x + dx))
                    .append(",\"y\":").append(num(box.y + dy))
                    .append(",\"w\":").append(num(box.w))
                    .append(",\"h\":").append(num(box.h))
                    .append('}');
        }
        json.append("],");

        json.append("\"links\":[");
        first = true;
        for (final Link link : links) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"source\":\"").append(escape(link.source)).append('"')
                    .append(",\"target\":\"").append(escape(link.target)).append('"')
                    .append(",\"kind\":\"").append(link.kind).append('"')
                    .append(",\"label\":\"").append(escape(link.label)).append('"')
                    .append(",\"arrow\":").append(link.arrow)
                    .append(",\"points\":[");
            boolean firstPoint = true;
            for (final Pt point : link.points) {
                if (!firstPoint) {
                    json.append(',');
                }
                firstPoint = false;
                json.append('[').append(num(point.x + dx)).append(',').append(num(point.y + dy)).append(']');
            }
            json.append("]}");
        }
        json.append("],");

        // The legend explains the two line kinds (and the box).  Each entry carries the `kind` the
        // client uses to draw the matching swatch — a line, an arrow, or a box — so the legend can
        // never drift from what the drawing actually looks like.
        json.append("\"legend\":[")
                .append("{\"kind\":\"node\",\"color\":\"#3498DB\",\"label\":\"Class\",")
                .append("\"description\":\"a class box, where the model puts it\"},")
                .append("{\"kind\":\"association\",\"color\":\"#7f8c8d\",\"label\":\"Association\",")
                .append("\"description\":\"a plain line between two classes\"},")
                .append("{\"kind\":\"generalization\",\"color\":\"#e67e22\",\"label\":\"Generalization\",")
                .append("\"description\":\"the arrow points at the super-class (is-a)\"}")
                .append(']');

        json.append('}');
        return json.toString();
    }

    // -- the one-pass index over the export ------------------------------------

    /** Everything the reconstruction needs, collected in a single pass over the triples. */
    private static final class Index {
        final Map<String, String> type = new LinkedHashMap<>();
        final Map<String, String> name = new LinkedHashMap<>();
        final Map<String, List<String>> containsView = new LinkedHashMap<>();
        final Map<String, String> isViewOf = new LinkedHashMap<>();
        final Map<String, String> shape = new LinkedHashMap<>();
        final Map<String, String> sourceView = new LinkedHashMap<>();
        final Map<String, String> targetView = new LinkedHashMap<>();
        final Map<String, String> topLeft = new LinkedHashMap<>();
        final Map<String, String> width = new LinkedHashMap<>();
        final Map<String, String> height = new LinkedHashMap<>();
        final Map<String, String> x = new LinkedHashMap<>();
        final Map<String, String> y = new LinkedHashMap<>();
        final Map<String, List<String>> points = new LinkedHashMap<>();

        static Index of(final RdfGraphTO graph) {
            final Index index = new Index();
            for (final RdfTripleTO triple : graph.triples()) {
                if (triple.literal()) {
                    switch (triple.predicate()) {
                        case ONTOUML_NAME -> index.name.putIfAbsent(triple.subject(), triple.objectValue());
                        case ONTOUML_WIDTH -> index.width.putIfAbsent(triple.subject(), triple.objectValue());
                        case ONTOUML_HEIGHT -> index.height.putIfAbsent(triple.subject(), triple.objectValue());
                        case ONTOUML_X -> index.x.putIfAbsent(triple.subject(), triple.objectValue());
                        case ONTOUML_Y -> index.y.putIfAbsent(triple.subject(), triple.objectValue());
                        default -> { }
                    }
                } else {
                    switch (triple.predicate()) {
                        case RDF_TYPE -> index.type.putIfAbsent(triple.subject(), triple.objectValue());
                        case ONTOUML_CONTAINS_VIEW -> index.containsView
                                .computeIfAbsent(triple.subject(), k -> new ArrayList<>()).add(triple.objectValue());
                        case ONTOUML_IS_VIEW_OF -> index.isViewOf.putIfAbsent(triple.subject(), triple.objectValue());
                        case ONTOUML_SHAPE -> index.shape.putIfAbsent(triple.subject(), triple.objectValue());
                        case ONTOUML_SOURCE_VIEW -> index.sourceView.putIfAbsent(triple.subject(), triple.objectValue());
                        case ONTOUML_TARGET_VIEW -> index.targetView.putIfAbsent(triple.subject(), triple.objectValue());
                        case ONTOUML_TOP_LEFT -> index.topLeft.putIfAbsent(triple.subject(), triple.objectValue());
                        case ONTOUML_POINT -> index.points
                                .computeIfAbsent(triple.subject(), k -> new ArrayList<>()).add(triple.objectValue());
                        default -> { }
                    }
                }
            }
            return index;
        }

        /** Every diagram of the export: anything typed {@code ontouml:Diagram}, plus any view owner. */
        Set<String> diagrams() {
            final Set<String> out = new LinkedHashSet<>();
            type.forEach((iri, kind) -> {
                if (ONTOUML_DIAGRAM.equals(kind)) {
                    out.add(iri);
                }
            });
            out.addAll(containsView.keySet());
            return out;
        }

        String displayNameOf(final String iri) {
            final String explicit = name.get(iri);
            return explicit != null && !explicit.isBlank() ? explicit : RdfGraphDiagrams.localName(iri);
        }

        /** A view's label is the name of the model element it draws, never the view's own id. */
        String elementName(final String view) {
            final String element = isViewOf.get(view);
            return displayNameOf(element != null ? element : view);
        }

        /** The label a connector carries — a relation's name; blank for the many anonymous ones. */
        String relationLabelOf(final String view) {
            final String element = isViewOf.get(view);
            if (element == null) {
                return "";
            }
            final String explicit = name.get(element);
            return explicit != null ? explicit : "";
        }

        String resolveDiagram(final String diagramName) {
            final List<String> candidates = new ArrayList<>(diagrams());
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException(
                        "No UML diagram in the graph: it carries no ontouml:Diagram / containsView resources");
            }
            if (diagramName != null && !diagramName.isBlank()) {
                for (final String candidate : candidates) {
                    if (diagramName.trim().equalsIgnoreCase(displayNameOf(candidate))) {
                        return candidate;
                    }
                }
                throw new IllegalArgumentException("No UML diagram named '" + diagramName.trim() + "' — have: "
                        + candidates.stream().map(this::displayNameOf).sorted().toList());
            }
            // Blank -> the richest diagram, so a caller who does not know the names still gets a picture.
            return candidates.stream()
                    .max(Comparator.comparingInt(candidate -> containsView.getOrDefault(candidate, List.of()).size()))
                    .orElseThrow();
        }

        /** The rectangle a class view occupies, or {@code null} when the view carries no geometry. */
        Box boxOf(final String view) {
            final String rectangle = shape.get(view);
            if (rectangle == null) {
                return null;
            }
            final String corner = topLeft.get(rectangle);
            if (corner == null) {
                return null;
            }
            final Double left = number(x.get(corner));
            final Double top = number(y.get(corner));
            if (left == null || top == null) {
                return null;
            }
            final Double w = number(width.get(rectangle));
            final Double h = number(height.get(rectangle));
            return new Box(left, top, w != null ? w : DEFAULT_W, h != null ? h : DEFAULT_H);
        }

        /**
         * The polyline a connector follows, in <b>drawing</b> order.
         * <p>
         * The export states its points in an order that is <em>not</em> the drawing order: over half of
         * the multi-point paths zig-zag (a bend is listed before the one nearer the start), which draws
         * as a line that doubles back and so appears to stop in mid-air.  The points are therefore
         * re-chained by nearest neighbour from the end that sits on {@code startBox} — the export always
         * begins a path at its {@code targetView}, so the arrow at the far end keeps pointing where it
         * should.  A two-point path is already a straight line and is left exactly as stated.
         */
        List<Pt> pointsOf(final String view, final Box startBox, final Box endBox) {
            final String path = shape.get(view);
            final List<String> raw = path == null ? null : points.get(path);
            if (raw == null) {
                return List.of();
            }
            final List<String> ordered = new ArrayList<>(raw);
            ordered.sort(Comparator.comparingInt(UmlLayoutDiagrams::pointIndex));
            final List<Pt> out = new ArrayList<>();
            for (final String point : ordered) {
                final Double px = number(x.get(point));
                final Double py = number(y.get(point));
                if (px != null && py != null) {
                    out.add(new Pt(px, py));
                }
            }
            // The export marks a connector whose geometry it could not store with (0,0) sentinels —
            // `(0,0) → bend → (0,0)`, which would otherwise draw a line out of the canvas corner and
            // straight back.  Dropping them leaves too few points, so the connector falls back to a
            // straight line between its two boxes (the client does that for an empty `points`).
            out.removeIf(point -> point.x == 0 && point.y == 0);
            // A bend far from both boxes is not part of this route either: the export sometimes leaves a
            // point belonging to *another* connector in the path (a UFO-C generalization carrying points
            // from the region where the Action Universal views sit), which draws a line off into empty
            // space.  Dropping it falls back to the surviving points — a straight line when none are left.
            out.removeIf(point -> straysFrom(point, startBox, endBox));
            if (out.size() < 2) {
                return List.of();
            }
            final List<Pt> chained = chain(out, startBox);
            if (chained.size() < 2) {
                return List.of();
            }
            // A route that ends far from BOTH boxes is unusable — the export has left a point belonging to
            // another connector at one end, so the line (and a generalization's arrow) would point at
            // empty space.  A straight line between the two boxes is always right, so fall back to that.
            if (farFromBothBoxes(chained.getFirst(), startBox, endBox)
                    || farFromBothBoxes(chained.getLast(), startBox, endBox)) {
                return List.of();
            }
            return orthogonalise(clipToBoxes(chained, startBox, endBox), startBox, endBox);
        }
    }

    // -- helpers ---------------------------------------------------------------

    /**
     * Widens a box so its class name fits, which keeps <b>one font size across the whole diagram</b> — a
     * shrunk label is the fallback, not the rule.
     * <p>
     * A model's box is only as wide as its own layout says, and over a third of them are narrower than
     * their class name.  The box grows to the right when there is room, otherwise to the left; when a
     * neighbouring box leaves no room either way the width is left alone and the client shrinks that one
     * label instead.  Measured on the bundled model: 103 boxes grow right, 16 grow left, 7 have no room.
     */
    private static void widenToFitLabels(final Map<String, Box> boxes, final Index index) {
        for (final String id : new ArrayList<>(boxes.keySet())) {
            final Box box = boxes.get(id);
            final double shortfall = estimatedLabelWidth(index.elementName(id)) + LABEL_PADDING - box.w;
            if (shortfall <= 0) {
                continue;
            }
            if (room(boxes, id, box, true) >= shortfall) {
                boxes.put(id, new Box(box.x, box.y, box.w + shortfall, box.h));
            } else if (room(boxes, id, box, false) >= shortfall) {
                boxes.put(id, new Box(box.x - shortfall, box.y, box.w + shortfall, box.h));
            }
        }
    }

    /**
     * Free space beside {@code box} before the nearest box that overlaps it vertically — to the right
     * ({@code rightwards}) or to the left.
     *
     * @return {@link Double#MAX_VALUE} when nothing is in the way, {@code 0} when a neighbour already
     *         reaches the box
     */
    private static double room(final Map<String, Box> boxes, final String id, final Box box,
                               final boolean rightwards) {
        double best = Double.MAX_VALUE;
        for (final Map.Entry<String, Box> other : boxes.entrySet()) {
            if (other.getKey().equals(id)) {
                continue;
            }
            final Box b = other.getValue();
            if (b.y >= box.y + box.h || box.y >= b.y + b.h) {
                continue;                                   // no vertical overlap: not in the way
            }
            if (rightwards) {
                if (b.x + b.w <= box.x + box.w) {
                    continue;                               // it ends before our right edge
                }
                best = Math.clamp(b.x - LABEL_GAP - (box.x + box.w), 0, best);
            } else {
                if (b.x >= box.x) {
                    continue;                               // it starts after our left edge
                }
                best = Math.clamp(box.x - LABEL_GAP - (b.x + b.w), 0, best);
            }
        }
        return best;
    }

    /**
     * Estimated width of a label, in model units, at the label font size.
     * <p>
     * The domain has no font to measure with, so this uses a per-character advance table (12px bold
     * sans-serif, measured in Chromium and padded by {@link #LABEL_SAFETY}).  The client measures the
     * real text and shrinks only when this estimate still falls short — the exception, not the rule.
     * <p>
     * Public for the guardrail test; it is a pure function and static, so Causeway does not treat it as a
     * member (see {@code localName}).
     */
    public static double estimatedLabelWidth(final String label) {
        if (label == null) {
            return 0;
        }
        double width = 0;
        for (int i = 0; i < label.length(); i++) {
            width += advance(label.charAt(i));
        }
        return width * LABEL_SAFETY;
    }

    /** Advance width of one character, in model units at the label font size. */
    private static double advance(final char c) {
        if (c >= 'a' && c <= 'z') {
            return LOWER[c - 'a'];
        }
        if (c >= 'A' && c <= 'Z') {
            return UPPER[c - 'A'];
        }
        if (c >= '0' && c <= '9') {
            return 6.7;
        }
        return switch (c) {
            case '_' -> 6.7;
            case ' ' -> 3.8;
            case '(', ')', '-' -> 4.0;
            case '.', ',' -> 3.3;
            default -> 8.0;             // an unmeasured character: assume a wide one
        };
    }

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
    private static List<Pt> orthogonalise(final List<Pt> points, final Box startBox, final Box endBox) {
        if (points.size() < 2) {
            return points;
        }
        final List<Pt> out = new ArrayList<>();
        out.add(points.getFirst());
        boolean previousWasHorizontal = false;
        for (int i = 1; i < points.size(); i++) {
            final Pt prev = out.getLast();
            final Pt cur = points.get(i);
            final double dx = Math.abs(cur.x - prev.x);
            final double dy = Math.abs(cur.y - prev.y);
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
                    final double midX = (prev.x + cur.x) / 2;
                    final double midY = (prev.y + cur.y) / 2;
                    if (firstVertical) {
                        out.add(new Pt(prev.x, midY));
                        out.add(new Pt(cur.x, midY));
                    } else {
                        out.add(new Pt(midX, prev.y));
                        out.add(new Pt(midX, cur.y));
                    }
                    previousWasHorizontal = !firstVertical;
                } else {
                    out.add(firstVertical ? new Pt(prev.x, cur.y) : new Pt(cur.x, prev.y));
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
            if (!LINK_GENERALIZATION.equals(link.kind()) || superBox == null) {
                continue;
            }
            firstOfGroup.putIfAbsent(link.source(), i);
            attach.computeIfAbsent(link.source(), id -> facingEdgeMidpoint(superBox, links, id, boxes));
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
        final Map<Integer, Pt> spread = spreadPorts(links, boxes);
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

            final Pt shared = LINK_GENERALIZATION.equals(link.kind()) ? attach.get(link.source()) : null;
            // An inheritance edge must run along the edge's normal for a while before the arrowhead, so the
            // sub-classes' lines merge into one visible trunk instead of arriving sideways and turning in
            // the last two units.  The router therefore aims at a point that far outside the edge, and the
            // trunk into the box is appended.
            final Pt trunk = shared == null ? null : trunkPoint(end, shared, all);
            final Pt endPort = shared != null ? trunk : portFor(spread, i, false, end, to);
            final int arrival = shared != null ? OrthogonalRouter.inward(end, shared) : OrthogonalRouter.NONE;
            final Box endBox = shared != null ? new Box(trunk.x(), trunk.y(), 0, 0) : end;
            // An inheritance edge leaves the middle of its sub-class's TOP edge, going straight up — the
            // way a UML tool draws it, and the reason a sub-class reads as "below" its super-class.  When
            // the sub-class sits closer to its super-class than the stub is long, there is no room to rise
            // that far before the trunk: forcing it anyway would make the route overshoot and loop back, so
            // the direction is left free (the attachment point is still the middle of the top edge).
            final Pt fromPort = shared != null
                    ? new Pt(start.x() + start.w() / 2, start.y())
                    : portFor(spread, i, true, start, from);
            final int departure = shared != null && hasRoomToRise(fromPort, trunk)
                    ? OrthogonalRouter.UP : OrthogonalRouter.NONE;

            List<Pt> route = OrthogonalRouter.route(start, endBox, fromPort, endPort,
                    departure, arrival, all, placed);
            if (route.size() >= 2 && shared != null) {
                route = new ArrayList<>(route);
                route.add(shared);                              // the trunk into the super-class
            }
            if (route.size() < 2) {
                route = bestFallbackRoute(start, end, all, placed);
            }
            if (route.size() < 2) {
                continue;
            }
            final boolean arrow = shared == null || i == firstOfGroup.get(link.source());
            links.set(i, new Link(link.source(), link.target(), link.kind(), link.label(), route, arrow));

            if (shared == null) {
                addSegments(placed, route);
                continue;
            }
            // The route's last segment is the group's shared trunk: every member of the group runs along the
            // same line there, so on the picture it is ONE line and must count once.  Only the member that
            // reaches furthest out extends it — otherwise a group of four would charge every later route
            // four times for crossing a single line, and routes would detour around a line that is not there.
            addSegments(placed, route.subList(0, route.size() - 1));
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
    private static Map<Integer, Pt> spreadPorts(final List<Link> links, final Map<String, Box> boxes) {
        final Map<EdgeKey, List<End>> groups = new LinkedHashMap<>();
        final Set<EdgeKey> reserved = new LinkedHashSet<>();
        for (int i = 0; i < links.size(); i++) {
            final Link link = links.get(i);
            final Box start = boxes.get(link.target());
            final Box end = boxes.get(link.source());
            if (LINK_GENERALIZATION.equals(link.kind())) {
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
    private static Pt portFor(final Map<Integer, Pt> spread, final int link, final boolean start,
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
     * Whether a sub-class's top edge has room to leave straight up for the full departure stub before the
     * trunk point.
     * <p>
     * Only a route that has to <em>rise</em> to the trunk can overshoot it: when the trunk is below the
     * sub-class (its super-class is underneath) the route descends and the stub is always safe.
     */
    private static boolean hasRoomToRise(final Pt fromPort, final Pt trunk) {
        final double rise = fromPort.y() - trunk.y();
        return rise <= 0 || rise >= OrthogonalRouter.DEPARTURE_STUB;
    }

    /**
     * The point the trunk should reach: {@link #TRUNK_STUB} outside {@code attach} along its edge's outward
     * normal — shortened when another box is in the way, which happens when a sub-class sits just below its
     * super-class.  Aiming at a point inside that sub-class would make the search fail outright and drop the
     * connector back to the side-approaching fallback.
     */
    private static Pt trunkPoint(final Box box, final Pt attach, final List<Box> boxes) {
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
    private static Pt facingEdgeMidpoint(final Box superBox, final List<Link> links, final String superId,
                                         final Map<String, Box> boxes) {
        double dx = 0;
        double dy = 0;
        for (final Link link : links) {
            if (!LINK_GENERALIZATION.equals(link.kind()) || !superId.equals(link.source())) {
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

    /** The orthogonal route between two boxes that crosses the fewest already-placed routes. */
    private static List<Pt> bestFallbackRoute(final Box start, final Box end,
                                              final List<Box> boxes, final List<Segment> placed) {
        List<Pt> best = List.of();
        long fewest = Long.MAX_VALUE;
        for (final Pt from : edgeMidpoints(start)) {
            for (final Pt to : edgeMidpoints(end)) {
                final List<Pt> route = orthogonalise(List.of(from, to), start, end);
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
                new Pt(box.x + box.w / 2, box.y),
                new Pt(box.x + box.w / 2, box.y + box.h),
                new Pt(box.x, box.y + box.h / 2),
                new Pt(box.x + box.w, box.y + box.h / 2));
    }

    private static void addSegments(final List<Segment> into, final List<Pt> points) {
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
        return (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x);
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
        final double x0 = box.x + BOX_INSET;
        final double x1 = box.x + box.w - BOX_INSET;
        final double y0 = box.y + BOX_INSET;
        final double y1 = box.y + box.h - BOX_INSET;
        if (x1 <= x0 || y1 <= y0) {
            return false;                              // no interior to speak of
        }
        if (Math.abs(a.y - b.y) <= ORTHO_TOL) {        // a horizontal run
            if (a.y <= y0 || a.y >= y1) {
                return false;
            }
            return Math.max(a.x, b.x) > x0 && Math.min(a.x, b.x) < x1;
        }
        if (Math.abs(a.x - b.x) <= ORTHO_TOL) {        // a vertical run
            if (a.x <= x0 || a.x >= x1) {
                return false;
            }
            return Math.max(a.y, b.y) > y0 && Math.min(a.y, b.y) < y1;
        }
        return false;                                  // a diagonal run: never in an orthogonalised route
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
        final double abx = b.x - a.x;
        final double aby = b.y - a.y;
        final double bcx = c.x - b.x;
        final double bcy = c.y - b.y;
        return abx * bcx + aby * bcy < 0 && Math.abs(abx * bcy - aby * bcx) < 1e-9;
    }

    /** Whether {@code point} sits on the left or right edge of {@code box} rather than top or bottom. */
    private static boolean onVerticalEdge(final Pt point, final Box box) {
        return Math.abs(point.x - box.x) <= ORTHO_TOL
                || Math.abs(point.x - (box.x + box.w)) <= ORTHO_TOL;
    }

    /** The trailing {@code _<n>} of a path point IRI gives the export's <em>stated</em> order. */
    private static int pointIndex(final String iri) {
        final String local = RdfGraphDiagrams.localName(iri);
        final int underscore = local.lastIndexOf('_');
        if (underscore < 0 || underscore + 1 >= local.length()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(local.substring(underscore + 1));
        } catch (final NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Re-orders a path's points into a connected polyline, starting from the point that sits on
     * {@code startBox} and always taking the nearest remaining point next.
     * <p>
     * Returns the points untouched when the start cannot be located — the export's own order is better
     * than an invented one.
     */
    private static List<Pt> chain(final List<Pt> points, final Box startBox) {
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
                final double dx = remaining.get(i).x - last.x;
                final double dy = remaining.get(i).y - last.y;
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

    /**
     * Pulls a route's two ends out to the edges of the boxes it runs between.
     * <p>
     * A UML export ends a path <em>inside</em> the box it points at — 14 units in, in this model — and
     * the boxes are drawn on top of the connectors, so an arrowhead whose tip sat under the box was
     * painted over.  Only a path that happened to end exactly on the boundary showed an arrow at all,
     * which is why one generalization out of thirty-six appeared.
     */
    private static List<Pt> clipToBoxes(final List<Pt> points, final Box startBox, final Box endBox) {
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
        final double dx = to.x - from.x;
        final double dy = to.y - from.y;
        double enter = 0;
        double exit = 1;
        if (dx == 0) {
            if (from.x < box.x || from.x > box.x + box.w) {
                return to;
            }
        } else {
            final double a = (box.x - from.x) / dx;
            final double b = (box.x + box.w - from.x) / dx;
            enter = Math.max(enter, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
        }
        if (dy == 0) {
            if (from.y < box.y || from.y > box.y + box.h) {
                return to;
            }
        } else {
            final double a = (box.y - from.y) / dy;
            final double b = (box.y + box.h - from.y) / dy;
            enter = Math.max(enter, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
        }
        if (enter > exit || enter <= 0 || enter >= 1) {
            return to;
        }
        return new Pt(from.x + enter * dx, from.y + enter * dy);
    }

    /**
     * Whether {@code point} is too far from a route's two boxes to belong to it.
     * <p>
     * The export sometimes leaves a point belonging to <em>another</em> connector in a path — a UFO-C
     * generalization carrying points from the region where the {@code Action Universal} views sit, say —
     * which draws a line off into empty space.  A genuine bend lies between the two boxes, so anything
     * farther from both of them than the boxes are from each other is dropped.
     */
    private static boolean straysFrom(final Pt point, final Box startBox, final Box endBox) {
        if (startBox == null || endBox == null) {
            return false;
        }
        final double limit = Math.max(ROUTE_SLACK, centreDistance(startBox, endBox));
        return distanceTo(startBox, point) > limit && distanceTo(endBox, point) > limit;
    }

    /** Whether {@code point} lies farther than {@link #END_SLACK} from both boxes. */
    private static boolean farFromBothBoxes(final Pt point, final Box startBox, final Box endBox) {
        if (startBox == null || endBox == null) {
            return false;
        }
        return distanceTo(startBox, point) > END_SLACK && distanceTo(endBox, point) > END_SLACK;
    }

    /** Distance from {@code point} to the nearest edge of {@code box} — zero when it is inside. */
    private static double distanceTo(final Box box, final Pt point) {
        final double dx = Math.max(Math.max(box.x - point.x, 0), point.x - (box.x + box.w));
        final double dy = Math.max(Math.max(box.y - point.y, 0), point.y - (box.y + box.h));
        return Math.hypot(dx, dy);
    }

    /** Distance between two boxes' centres — how far apart a route's ends are. */
    private static double centreDistance(final Box first, final Box second) {
        return Math.hypot(
                (first.x + first.w / 2) - (second.x + second.w / 2),
                (first.y + first.h / 2) - (second.y + second.h / 2));
    }

    /** How far a connector reaches — the distance between its two boxes' centres, for the routing order. */
    private static double span(final Link link, final Map<String, Box> boxes) {
        final Box start = boxes.get(link.target());
        final Box end = boxes.get(link.source());
        return start == null || end == null ? 0 : centreDistance(start, end);
    }

    /** Whether {@code point} lies on (or inside) the box, within a small tolerance. */
    private static boolean contains(final Box box, final Pt point) {
        final double tolerance = 2;
        return point.x >= box.x - tolerance && point.x <= box.x + box.w + tolerance
                && point.y >= box.y - tolerance && point.y <= box.y + box.h + tolerance;
    }

    private static Double number(final String lexical) {
        if (lexical == null || lexical.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(lexical.trim());
        } catch (final NumberFormatException ex) {
            return null;
        }
    }

    /** Integral values print without a {@code .0} — the JSON is read by humans as well as by D3. */
    private static String num(final double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? Long.toString((long) value)
                : Double.toString(value);
    }

    private static String escape(final String value) {
        return RdfGraphDiagrams.escapeJson(value);
    }

    // -- value carriers --------------------------------------------------------

    /** A class box, in the export's own coordinate system. */
    record Box(double x, double y, double w, double h) { }

    /** One point of a connector's polyline. */
    record Pt(double x, double y) { }

    /**
     * A connector: the two views it joins, what it means, the polyline it follows, and whether it carries
     * the arrowhead.  Generalizations that share a super-class merge into one trunk there, and only the
     * first of them draws the arrow.
     */
    private record Link(String source, String target, String kind, String label, List<Pt> points,
                        boolean arrow) { }

    /** A connector as resolved from the graph, before its route is read. */
    private record Route(String view, String source, String target, String kind,
                         Box sourceBox, Box targetBox) { }

    /** One segment of an already-placed route, for the crossing count. */
    record Segment(Pt from, Pt to) { }

    /** One edge of one box — the connectors that attach there share it. */
    private record EdgeKey(String box, int edge) { }

    /** One end of a connector: which link, which end, and where along its edge the model attached it. */
    private record End(int link, boolean start, double along) { }

    /** The drawing's bounding box — what the padded canvas is computed from. */
    private record Bounds(double minX, double minY, double maxX, double maxY) {

        /** The bounding box of the boxes alone: the area a route's bends must stay within. */
        static Bounds of(final Map<String, Box> boxes) {
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (final Box box : boxes.values()) {
                minX = Math.min(minX, box.x);
                minY = Math.min(minY, box.y);
                maxX = Math.max(maxX, box.x + box.w);
                maxY = Math.max(maxY, box.y + box.h);
            }
            return minX > maxX ? new Bounds(0, 0, 0, 0) : new Bounds(minX, minY, maxX, maxY);
        }

        /** The bounding box of the whole picture: the boxes plus every surviving connector point. */
        static Bounds of(final Map<String, Box> boxes, final List<Link> links) {
            final Bounds base = of(boxes);
            double minX = base.minX;
            double minY = base.minY;
            double maxX = base.maxX;
            double maxY = base.maxY;
            for (final Link link : links) {
                for (final Pt point : link.points) {
                    minX = Math.min(minX, point.x);
                    minY = Math.min(minY, point.y);
                    maxX = Math.max(maxX, point.x);
                    maxY = Math.max(maxY, point.y);
                }
            }
            return new Bounds(minX, minY, maxX, maxY);
        }

        double width() {
            return maxX - minX;
        }

        double height() {
            return maxY - minY;
        }
    }
}
