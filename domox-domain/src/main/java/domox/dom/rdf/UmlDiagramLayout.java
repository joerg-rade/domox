package domox.dom.rdf;

import domox.dom.rdf.geometry.Box;
import domox.dom.rdf.geometry.Bounds;
import domox.dom.rdf.geometry.Pt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Lays out a class diagram that has <b>no geometry of its own</b>, and serialises it as the D3 payload
 * {@code d3-integration.mjs} draws statically.
 *
 * <p><b>Why this exists.</b>  {@link UmlLayoutDiagrams} reconstructs a picture from the geometry an
 * OntoUML export <em>stores</em> next to its model — the boxes come back exactly as the model's author
 * drew them.  A diagram <em>assembled</em> from the CRC candidates ({@code generate.ClassDiagram}) has
 * no such geometry: it is a list of class names and a list of relations, so it can only be rendered by
 * a tool that lays it out itself.  PlantUML is one such tool (the PDF path: Kroki → PDF → pdf.js); this
 * is the other, and it hands the client the same {@code "layout":"uml"} payload the reconstruction
 * produces — so the client needs no change and the two pictures cannot drift.</p>
 *
 * <p><b>What it does.</b>  A deterministic grid: the nodes are placed in a
 * {@code ceil(sqrt(n))}-column grid in the order given, and <em>a change of {@link Node#group()}
 * starts a new band</em> — one empty row apart — so a model's sub-domains read as separate blocks
 * rather than as one interleaved soup.  Boxes are then widened to fit their labels
 * ({@link LabelLayout}, the same pass the reconstruction uses, which keeps one font size across the
 * diagram), and the connectors are routed orthogonally around the boxes and each other by
 * {@link OrthogonalRouter}, via {@link UmlLayoutDiagrams#routeAll} — the very routing the
 * reconstruction uses, generalizations merging into a trunk at the super-class included.  Nothing here
 * draws a bend by hand: the router owns that, and its invariants are asserted by
 * {@code UmlLayoutIntegrityTest} / {@code scripts/uml-diagram-report.py}.</p>
 *
 * <p><b>Determinism is the caller's job, and that is deliberate.</b>  This class does not sort: the
 * order of {@code nodes} is the reading order of the grid and the order of {@code edges} is the order
 * the router places them in (it spreads the ports of several connectors on one edge "in the order the
 * model gave them", and the first generalization of a group carries the single arrowhead).  The caller
 * therefore sorts, and the picture is reproducible from the model alone — which is what lets the
 * offline guardrails compare a payload with the previous one.</p>
 *
 * <p><b>Total, never throwing.</b>  An empty model, a null title, a duplicate node id and an edge whose
 * endpoint is not drawn are all tolerated: the first yields an empty (tiny) canvas, a duplicate id is
 * <em>one</em> box — which is what PlantUML does with two classes of the same name — and an edge to a
 * class that is not in the diagram is dropped rather than drawn into empty space (the same rule
 * {@code ClassDiagram} applies to a sub-domain boundary).  A diagram action must not fail because the
 * model is thin.</p>
 *
 * <p><b>Not a Causeway service.</b>  A pure static function of its arguments, like
 * {@code ModelContent}: it has no injected collaborators, so there is nothing for the metamodel to
 * introspect and nothing for a test to wire up.</p>
 */
public final class UmlDiagramLayout {

    /** The link kinds the client picks its stroke colour and arrowhead from (see {@link Link}). */
    public static final String ASSOCIATION = Link.ASSOCIATION;

    /** @see #ASSOCIATION */
    public static final String GENERALIZATION = Link.GENERALIZATION;

    /** A box's width, in model units — wide enough for a ~22-character class name before widening. */
    private static final double BOX_W = 180;

    /**
     * A box's height, in model units.  The client draws a name compartment separator 20 units down and
     * the label above it, so the lower part of the box stays empty — the classes carry no members in
     * this view, and an empty compartment is how a UML class box says so.
     */
    private static final double BOX_H = 40;

    /** Kept clear between two columns: room for a label widened leftwards, plus a visible channel. */
    private static final double COLUMN_GAP = 72;

    /**
     * Kept clear between two rows.  Wider than the horizontal gap because the vertical channel is what a
     * generalization's trunk and its {@link PortLayout} departure stub are read in.
     */
    private static final double ROW_GAP = 80;

    private static final double COLUMN_STEP = BOX_W + COLUMN_GAP;

    private static final double ROW_STEP = BOX_H + ROW_GAP;

    private UmlDiagramLayout() {
    }

    /** One box: the id the links refer to, the label it draws, and the band it belongs to. */
    public record Node(String id, String name, String group) {
    }

    /**
     * One connector, before it is routed.
     *
     * <p>{@code source} / {@code target} are read the way {@link Link} reads them: for a
     * {@link #GENERALIZATION} the <em>source is the super-class</em> — the arrow points at it, and
     * {@link PortLayout} merges a group of them into the trunk on that box's edge.  A CRC
     * {@code CollaboratorCdd} states the opposite (its source is the sub-class), so the caller swaps the
     * ends when it maps one.</p>
     */
    public record Edge(String source, String target, String kind, String label) {
    }

    /**
     * Lays the diagram out and serialises it.
     *
     * @param title the diagram's display name, drawn as the title; blank is tolerated
     * @param nodes the boxes, in reading order (a change of {@code group} starts a new band)
     * @param edges the connectors, in the order the router should place them
     * @return the {@code {"layout":"uml",…}} JSON the client draws statically
     */
    public static String render(final String title, final List<Node> nodes, final List<Edge> edges) {
        final Map<String, Box> boxes = place(nodes);
        final Function<String, String> nameOf = nameOf(nodes);

        // Widen before the routes are read: a route's ends are clipped to the box edges, so a box widened
        // afterwards would swallow the arrowheads again (the order UmlLayoutDiagrams itself keeps).
        LabelLayout.widenToFitLabels(boxes, nameOf);

        final List<Link> links = new ArrayList<>();
        for (final Edge edge : edges) {
            // An endpoint the diagram does not draw would leave the connector a line into empty space.
            if (edge == null || !boxes.containsKey(edge.source()) || !boxes.containsKey(edge.target())) {
                continue;
            }
            // No stored path, so no attachment hint: PortLayout picks the edge pair itself, which is the
            // case the router already handles for a connector whose path was unusable.
            links.add(new Link(edge.source(), edge.target(),
                    edge.kind() == null ? ASSOCIATION : edge.kind(),
                    edge.label() == null ? "" : edge.label(), List.of(), true));
        }
        UmlLayoutDiagrams.routeAll(links, boxes);

        final List<Pt> points = new ArrayList<>();
        for (final Link link : links) {
            points.addAll(link.points());
        }
        final Bounds bounds = Bounds.of(boxes.values(), points);

        // The client draws the boxes in this order, so sorting by label makes a box's z-order — and the
        // order a reader scans them in — independent of the order the model happened to list its classes.
        final List<String> nodeIds = new ArrayList<>(boxes.keySet());
        nodeIds.sort(Comparator.comparing(nameOf).thenComparing(id -> id));

        return DiagramJsonWriter.write(title == null ? "" : title, bounds, nodeIds, boxes, nameOf, links);
    }

    // -- the grid --------------------------------------------------------------

    /**
     * Places the boxes in a {@code ceil(sqrt(n))}-column grid, in the order given, starting a new band —
     * one empty row below the previous one — whenever the group changes.
     *
     * <p>A square-ish grid is what keeps a large model's picture usable: 970 classes come out 32 columns
     * by 31 rows rather than one 4000-unit-wide strip, so the canvas the client fits into the cell keeps
     * a workable aspect ratio.  The band break is what makes a sub-domain <em>visible</em>: without it a
     * model whose classes are grouped by sub-domain would interleave them row by row.</p>
     */
    private static Map<String, Box> place(final List<Node> nodes) {
        final Map<String, Box> boxes = new LinkedHashMap<>();
        final int columns = columnsFor(distinctIds(nodes).size());

        int row = 0;
        int column = 0;
        String band = null;
        for (final Node node : nodes) {
            if (node == null || node.id() == null || boxes.containsKey(node.id())) {
                continue;                       // the same id twice is one box, as PlantUML draws it
            }
            final String group = node.group() == null ? "" : node.group();
            if (!group.equals(band)) {
                if (band != null) {
                    if (column != 0) {
                        row++;                  // close the half-filled row the previous band ends in
                    }
                    row++;                      // and leave one empty row, so the bands read apart
                    column = 0;
                }
                band = group;
            }
            boxes.put(node.id(), new Box(column * COLUMN_STEP, row * ROW_STEP, BOX_W, BOX_H));
            if (++column == columns) {
                column = 0;
                row++;
            }
        }
        return boxes;
    }

    /** A square-ish grid, and a single column for one box — never zero, so the placement loop is total. */
    private static int columnsFor(final int nodeCount) {
        return nodeCount <= 1 ? 1 : Math.max(1, (int) Math.ceil(Math.sqrt(nodeCount)));
    }

    /** The ids that will actually get a box, in first-appearance order. */
    private static List<String> distinctIds(final List<Node> nodes) {
        final LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (final Node node : nodes) {
            if (node != null && node.id() != null) {
                ids.add(node.id());
            }
        }
        return new ArrayList<>(ids);
    }

    /** The label a box draws, keyed by id — the first name given for an id wins. */
    private static Function<String, String> nameOf(final List<Node> nodes) {
        final Map<String, String> names = new LinkedHashMap<>();
        for (final Node node : nodes) {
            if (node != null && node.id() != null) {
                names.putIfAbsent(node.id(), node.name() == null ? node.id() : node.name());
            }
        }
        return id -> names.getOrDefault(id, Objects.toString(id, ""));
    }
}
