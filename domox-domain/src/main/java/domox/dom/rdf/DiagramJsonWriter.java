package domox.dom.rdf;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import domox.dom.rdf.geometry.Box;
import domox.dom.rdf.geometry.Bounds;
import domox.dom.rdf.geometry.Pt;

/**
 * <b>Serialises a UML diagram to the D3 payload</b> the client draws.
 *
 * <p>Two shapes, one client.  The <b>static</b> flavour carries the geometry the client needs and no
 * layout engine (see {@link UmlLayoutDiagrams}):</p>
 * <pre>
 *   { "layout":"uml", "title":"…", "canvas":{"width":…,"height":…},
 *     "nodes":[{"id","name","kind","x","y","w","h"}],
 *     "links":[{"source","target","kind","label","points":[[x,y],…]}],
 *     "legend":[{"color","label","description"}] }
 * </pre>
 *
 * <p>The <b>force</b> flavour ({@code "force":true}) carries a <em>seed</em> rectangle per box and no
 * routes, and the client runs a {@code d3-force} simulation over it:</p>
 * <pre>
 *   { "layout":"uml", "force":true, "title":"…", "canvas":{"width":…,"height":…},
 *     "nodes":[{"id","name","kind","x","y","w","h","attributes":[…],"methods":[…],
 *               "archetype","color"}],
 *     "links":[{"source","target","kind","label","arrow"}],
 *     "legend":[{"kind","color","label","description"}] }
 * </pre>
 *
 * <p>{@code "packages":true} is added to the force flavour when the boxes are <b>sub-domains</b> rather
 * than classes — the sub-domain overview.  The geometry is unchanged; only the {@code kind} of each node
 * ({@code "Package"} instead of {@code "Class"}) and the legend's node row differ, so the client draws a
 * package symbol and reads each node's {@code attributes} as that package's outward classes.</p>
 *
 * <p>The export's coordinates are absolute within a much larger sheet, so every coordinate is translated
 * to start at {@link #PADDING} and the canvas is the drawing's bounding box plus that margin.</p>
 *
 * <p>Extracted from {@code UmlLayoutDiagrams}; it is handed the boxes, the links and the bounds that
 * class computed, and does nothing but turn them into JSON.</p>
 */
final class DiagramJsonWriter {

    /** Margin drawn around the reconstructed picture, in model units. */
    private static final int PADDING = 24;

    /** The model element kinds the reconstructed nodes carry. */
    private static final String NODE_KIND = "Class";

    /** The kind a <b>sub-domain overview</b> node carries — the client draws it as a package symbol. */
    private static final String PACKAGE_KIND = "Package";

    private DiagramJsonWriter() {
    }

    /**
     * What the force flavour draws inside a box, beyond its name: its members, and the archetype that
     * colours it.
     *
     * <p>Empty on the static flavour, which draws none of it — the reconstruction is a picture of where
     * the model's author put things, not of what the classes contain.</p>
     *
     * <p>{@code superclass} is set on a box the is-a arrows point at, so the client can draw its name in
     * italics — the UML convention for an abstract class, and the one thing that tells a reader which
     * boxes the generalizations below belong to.</p>
     */
    record NodeDetail(List<String> attributes, List<String> methods, String archetype, String color,
                      boolean superclass) {

        /** A box with no members, no archetype, and nothing inheriting from it. */
        static final NodeDetail NONE = new NodeDetail(List.of(), List.of(), null, null, false);

        /** The same detail, marked as a box the is-a arrows point at. */
        NodeDetail asSuperclass() {
            return new NodeDetail(attributes, methods, archetype, color, true);
        }
    }

    /**
     * The translation every coordinate takes: the drawing's own origin moved to {@link #PADDING}, so the
     * picture starts at the margin rather than wherever the model's author left it.
     */
    private record Origin(double dx, double dy) {

        /** The translation that puts {@code bounds}' top-left corner at {@link #PADDING}. */
        static Origin of(final Bounds bounds) {
            return new Origin(PADDING - bounds.minX(), PADDING - bounds.minY());
        }

        /** A model x, translated. */
        double x(final double value) {
            return value + dx;
        }

        /** A model y, translated. */
        double y(final double value) {
            return value + dy;
        }
    }

    /**
     * One legend row: the swatch the client draws it from ({@code kind} and {@code color}) and the words
     * beside it.
     *
     * <p>{@code kind} is what the client branches on — a box, a line, an arrow — so a row cannot describe
     * something the drawing does not look like.  Holding the four parts of a row together is what keeps the
     * legend's colours and wording out of the JSON-building code below.</p>
     */
    private record LegendEntry(String kind, String color, String label, String description) {

        /** This row as one JSON object, in the order the client reads it. */
        String toJson() {
            return "{\"kind\":\"" + escape(kind) + "\",\"color\":\"" + escape(color) + "\",\"label\":\""
                    + escape(label) + "\",\"description\":\"" + escape(description) + "\"}";
        }
    }

    /**
     * Renders the diagram — its canvas, its nodes, its links and the legend — as one JSON object.
     *
     * @param title    the diagram's display name, drawn as the title
     * @param bounds   the drawing's bounding box, before the margin is added
     * @param nodeIds  the boxes to draw, in the order they are emitted
     * @param boxes    the box rectangles, keyed by view id
     * @param nameOf   the class name a view draws, for the node label
     * @param links    the connectors, already routed and clipped on the static flavour
     * @param force    {@code true} for the force flavour: members and archetype per node, no link points
     * @param packages {@code true} when the boxes are sub-domains to be drawn as packages; only meaningful
     *                 with {@code force}, and it only changes each node's {@code kind} and the legend
     * @param detailOf the members and archetype of each box, read only when {@code force} is set; may be
     *                 {@code null} otherwise
     */
    static String write(final String title, final Bounds bounds, final List<String> nodeIds,
                        final Map<String, Box> boxes, final Function<String, String> nameOf,
                        final List<Link> links, final boolean force, final boolean packages,
                        final Function<String, NodeDetail> detailOf) {
        final Origin origin = Origin.of(bounds);
        final StringBuilder json = new StringBuilder(1024);
        appendHeader(json, title, bounds, force, packages);
        appendNodes(json, nodeIds, boxes, nameOf, force, packages, detailOf, origin);
        appendLinks(json, links, force, origin);
        appendLegend(json, nodeIds, detailOf, force, packages);
        json.append('}');
        return json.toString();
    }

    /** The opening of the payload: the flavour, the title, and the canvas the drawing is padded into. */
    private static void appendHeader(final StringBuilder json, final String title, final Bounds bounds,
                                     final boolean force, final boolean packages) {
        json.append("{\"layout\":\"uml\",");
        if (force) {
            json.append("\"force\":true,");
        }
        if (force && packages) {
            json.append("\"packages\":true,");
        }
        json.append("\"title\":\"").append(escape(title)).append("\",")
                .append("\"canvas\":{\"width\":").append(num(bounds.width() + 2 * PADDING))
                .append(",\"height\":").append(num(bounds.height() + 2 * PADDING)).append("},");
    }

    /** Every box, in the order given. */
    private static void appendNodes(final StringBuilder json, final List<String> nodeIds,
                                    final Map<String, Box> boxes, final Function<String, String> nameOf,
                                    final boolean force, final boolean packages,
                                    final Function<String, NodeDetail> detailOf,
                                    final Origin origin) {
        json.append("\"nodes\":[");
        for (int i = 0; i < nodeIds.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            appendNode(json, nodeIds.get(i), boxes.get(nodeIds.get(i)), nameOf, force, packages,
                    detailOf, origin);
        }
        json.append("],");
    }

    /** One box: where it is, what it is called, and what the force flavour draws inside it. */
    private static void appendNode(final StringBuilder json, final String id, final Box box,
                                   final Function<String, String> nameOf, final boolean force,
                                   final boolean packages,
                                   final Function<String, NodeDetail> detailOf, final Origin origin) {
        json.append("{\"id\":\"").append(escape(id)).append('"')
                .append(",\"name\":\"").append(escape(nameOf.apply(id))).append('"')
                .append(",\"kind\":\"").append(packages ? PACKAGE_KIND : NODE_KIND).append('"')
                .append(",\"x\":").append(num(origin.x(box.x())))
                .append(",\"y\":").append(num(origin.y(box.y())))
                .append(",\"w\":").append(num(box.w()))
                .append(",\"h\":").append(num(box.h()));
        if (force) {
            appendDetail(json, detailOf == null ? NodeDetail.NONE : detailOf.apply(id));
        }
        json.append('}');
    }

    /** Every connector, in the order given. */
    private static void appendLinks(final StringBuilder json, final List<Link> links, final boolean force,
                                    final Origin origin) {
        json.append("\"links\":[");
        for (int i = 0; i < links.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            appendLink(json, links.get(i), force, origin);
        }
        json.append("],");
    }

    /**
     * One connector: its two ends, its kind, its label and its arrowhead — plus, on the static flavour, the
     * route it was drawn along.
     */
    private static void appendLink(final StringBuilder json, final Link link, final boolean force,
                                   final Origin origin) {
        json.append("{\"source\":\"").append(escape(link.source())).append('"')
                .append(",\"target\":\"").append(escape(link.target())).append('"')
                .append(",\"kind\":\"").append(link.kind()).append('"')
                .append(",\"label\":\"").append(escape(link.label())).append('"')
                .append(",\"arrow\":").append(link.arrow());
        if (!force) {
            json.append(",\"points\":");
            appendPoints(json, link.points(), origin);
        }
        json.append('}');
    }

    /** A connector's route, as a JSON array of {@code [x,y]} pairs — the static flavour's own geometry. */
    private static void appendPoints(final StringBuilder json, final List<Pt> points, final Origin origin) {
        json.append('[');
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            final Pt point = points.get(i);
            json.append('[').append(num(origin.x(point.x()))).append(',').append(num(origin.y(point.y())))
                    .append(']');
        }
        json.append(']');
    }

    /** One node's member compartments and archetype, appended inside its JSON object. */
    private static void appendDetail(final StringBuilder json, final NodeDetail detail) {
        json.append(",\"attributes\":");
        appendStrings(json, detail.attributes());
        json.append(",\"methods\":");
        appendStrings(json, detail.methods());
        if (detail.archetype() != null) {
            json.append(",\"archetype\":\"").append(escape(detail.archetype())).append('"');
        }
        if (detail.color() != null) {
            json.append(",\"color\":\"").append(escape(detail.color())).append('"');
        }
        if (detail.superclass()) {
            json.append(",\"superclass\":true");
        }
    }

    /** A JSON array of strings; {@code null} reads as empty, so the writer never emits a bare {@code null}. */
    private static void appendStrings(final StringBuilder json, final List<String> values) {
        json.append('[');
        boolean first = true;
        for (final String value : values == null ? List.<String>of() : values) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escape(value)).append('"');
        }
        json.append(']');
    }

    /**
     * The legend: the box and the two line kinds, then — on the force flavour — one row per archetype the
     * diagram actually draws.
     *
     * <p>On the overview the node row says <em>package</em> rather than <em>class</em>, and carries the
     * package symbol's blue: the row has to describe what is on screen, and a reader of the overview is
     * looking at sub-domains, not at classes.</p>
     */
    private static void appendLegend(final StringBuilder json, final List<String> nodeIds,
                                     final Function<String, NodeDetail> detailOf, final boolean force,
                                     final boolean packages) {
        final List<LegendEntry> entries = new ArrayList<>();
        entries.addAll(packages ? List.of(PACKAGE_LEGEND) : CLASS_LEGEND);
        entries.addAll(LINE_LEGEND);
        if (force && !packages) {
            entries.addAll(archetypesDrawn(nodeIds, detailOf));
        }
        json.append("\"legend\":[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(entries.get(i).toJson());
        }
        json.append(']');
    }

    /**
     * The legend row that opens a diagram's legend: the box itself, described as what the boxes actually
     * are — a class on every diagram but the overview, where they are sub-domains.
     *
     * <p>Each row carries the {@code kind} the client draws its swatch from, so the legend cannot drift
     * from what the drawing actually looks like; keeping the colours and the wording here, rather than in
     * the JSON-building code, is what makes that checkable at a glance.</p>
     */
    private static final List<LegendEntry> CLASS_LEGEND = List.of(
            new LegendEntry("node", "#3498DB", "Class", "a class box, where the model puts it"));

    /** The same row for the sub-domain overview, whose boxes are packages rather than classes. */
    private static final LegendEntry PACKAGE_LEGEND = new LegendEntry("node", "#3498DB", "Package",
            "a sub-domain, listing the classes that relate to another sub-domain");

    /** The two line kinds, which read the same in every UML diagram. */
    private static final List<LegendEntry> LINE_LEGEND = List.of(
            new LegendEntry("association", "#7f8c8d", "Association", "a plain line between two classes"),
            new LegendEntry("generalization", "#000000", "Generalization",
                    "the arrow points at the super-class (is-a)"));

    /** The description every archetype row carries — the archetypes themselves are the model's own. */
    private static final String ARCHETYPE_DESCRIPTION = "a Coad/Lefebvre archetype, which colours the box";

    /**
     * One legend row per archetype the diagram actually draws, in first-appearance order — so the legend
     * can neither name an archetype no box uses nor omit one that is on screen, and its swatch colour is
     * the fill the boxes carry.
     */
    private static List<LegendEntry> archetypesDrawn(final List<String> nodeIds,
                                                     final Function<String, NodeDetail> detailOf) {
        final List<LegendEntry> entries = new ArrayList<>();
        if (detailOf == null) {
            return entries;
        }
        final Set<String> drawn = new LinkedHashSet<>();
        for (final String id : nodeIds) {
            final NodeDetail detail = detailOf.apply(id);
            if (detail != null && detail.archetype() != null && detail.color() != null
                    && drawn.add(detail.archetype())) {
                entries.add(new LegendEntry("archetype", detail.color(), detail.archetype(),
                        ARCHETYPE_DESCRIPTION));
            }
        }
        return entries;
    }

    /** Integral values print without a {@code .0} — the JSON is read by humans as well as by D3. */
    private static String num(final double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? Long.toString((long) value)
                : Double.toString(value);
    }

    private static String escape(final String value) {
        return RdfJson.escape(value);
    }
}
