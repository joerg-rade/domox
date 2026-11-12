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
import domox.port.RdfGraphTO;
import domox.port.RdfTripleTO;

/**
 * <b>The layout an OntoUML export stores, read into lookups.</b>
 *
 * <p>A tool such as the OntoUML editor keeps the <em>picture</em>, not just the model: every
 * {@code ontouml:Diagram} lists its {@code ontouml:containsView}s; a {@code ClassView} carries a
 * {@code Rectangle} ({@code topLeftPosition} + {@code width}/{@code height}) and points at the class it
 * draws ({@code isViewOf}); a {@code RelationView} / {@code GeneralizationView} carries a {@code Path}
 * of {@code Point}s and names the two views it connects ({@code sourceView} / {@code targetView}).</p>
 *
 * <p>{@link #of(RdfGraphTO)} collects all of that in one pass over the triples, indexed by subject, so
 * {@link UmlLayoutDiagrams} can ask "which rectangle does this view occupy?" or "which way does this
 * connector run?" without walking the graph again.</p>
 *
 * <p>It also owns the OntoUML vocabulary the export is written in, since it is the only thing that
 * reads it.</p>
 *
 * <p>Extracted from {@code UmlLayoutDiagrams}; the value carriers it hands back ({@code Box},
 * {@code Pt}) live in {@code domox.dom.rdf.geometry}.</p>
 */
final class DiagramIndex {

    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

    // -- OntoUML vocabulary (https://w3id.org/ontouml#) -------------------------
    private static final String ONTOUML_NS = "https://w3id.org/ontouml#";
    private static final String ONTOUML_NAME = ONTOUML_NS + "name";
    private static final String ONTOUML_DIAGRAM = ONTOUML_NS + "Diagram";
    private static final String ONTOUML_CONTAINS_VIEW = ONTOUML_NS + "containsView";
    /** The kind a diagram node is drawn from — the one {@link UmlLayoutDiagrams} selects boxes by. */
    static final String ONTOUML_CLASS_VIEW = ONTOUML_NS + "ClassView";
    /** The kind of an association connector (see {@code Link.ASSOCIATION}). */
    static final String ONTOUML_RELATION_VIEW = ONTOUML_NS + "RelationView";
    /** The kind of a generalization connector (see {@code Link.GENERALIZATION}). */
    static final String ONTOUML_GENERALIZATION_VIEW = ONTOUML_NS + "GeneralizationView";
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

    /** Fallback box when a view carries no rectangle (so a class is never drawn as a point). */
    private static final double DEFAULT_W = 140;
    private static final double DEFAULT_H = 60;

    final Map<String, String> type = new LinkedHashMap<>();
    private final Map<String, String> name = new LinkedHashMap<>();
    final Map<String, List<String>> containsView = new LinkedHashMap<>();
    private final Map<String, String> isViewOf = new LinkedHashMap<>();
    private final Map<String, String> shape = new LinkedHashMap<>();
    final Map<String, String> sourceView = new LinkedHashMap<>();
    final Map<String, String> targetView = new LinkedHashMap<>();
    private final Map<String, String> topLeft = new LinkedHashMap<>();
    private final Map<String, String> width = new LinkedHashMap<>();
    private final Map<String, String> height = new LinkedHashMap<>();
    private final Map<String, String> x = new LinkedHashMap<>();
    private final Map<String, String> y = new LinkedHashMap<>();
    private final Map<String, List<String>> points = new LinkedHashMap<>();

    static DiagramIndex of(final RdfGraphTO graph) {
        final DiagramIndex index = new DiagramIndex();
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
        ordered.sort(Comparator.comparingInt(DiagramIndex::pointIndex));
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
        out.removeIf(point -> point.x() == 0 && point.y() == 0);
        // A bend far from both boxes is not part of this route either: the export sometimes leaves a
        // point belonging to *another* connector in the path (a UFO-C generalization carrying points
        // from the region where the Action Universal views sit), which draws a line off into empty
        // space.  Dropping it falls back to the surviving points — a straight line when none are left.
        out.removeIf(point -> RouteGeometry.straysFrom(point, startBox, endBox));
        if (out.size() < 2) {
            return List.of();
        }
        final List<Pt> chained = RouteGeometry.chain(out, startBox);
        if (chained.size() < 2) {
            return List.of();
        }
        // A route that ends far from BOTH boxes is unusable — the export has left a point belonging to
        // another connector at one end, so the line (and a generalization's arrow) would point at
        // empty space.  A straight line between the two boxes is always right, so fall back to that.
        if (RouteGeometry.farFromBothBoxes(chained.getFirst(), startBox, endBox)
                || RouteGeometry.farFromBothBoxes(chained.getLast(), startBox, endBox)) {
            return List.of();
        }
        return RouteGeometry.orthogonalise(RouteGeometry.clipToBoxes(chained, startBox, endBox), startBox, endBox);
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
}
