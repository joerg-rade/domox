package domox.dom.rdf;

import domox.dom.rdf.geometry.Box;
import domox.dom.rdf.geometry.Bounds;

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
 * {@code d3-integration.mjs} draws — as a <b>force-simulated, draggable</b> graph whose boxes carry
 * their members.
 *
 * <p><b>Why this exists.</b>  {@link UmlLayoutDiagrams} reconstructs a picture from the geometry an
 * OntoUML export <em>stores</em> next to its model — the boxes come back exactly as the model's author
 * drew them.  A diagram <em>assembled</em> from the CRC candidates ({@code generate.ClassDiagram}) has
 * no such geometry: it is a list of class names and a list of relations, so it can only be rendered by
 * a tool that lays it out itself.  PlantUML is one such tool (the PDF path: Kroki → PDF → pdf.js); this
 * is the other.</p>
 *
 * <p><b>Two payload flavours, one client.</b>  The client branches on {@code "force":true}.  The
 * reconstruction's payload carries its own routed polylines and is drawn statically; this one carries a
 * <em>seed</em> rectangle per box and no routes at all, and the client runs a {@code d3-force}
 * simulation over them — so the reader can drag a box and the picture re-settles.  A route computed
 * here would be stale the moment the first tick ran, so the connectors are drawn as straight
 * centre-to-centre lines and the orthogonal router is not used at all: it remains the PDF's layout
 * engine, and its invariants ({@code UmlLayoutIntegrityTest},
 * {@code scripts/uml-diagram-report.py}) still hold over that path.</p>
 *
 * <p><b>What it does.</b>  A deterministic seed grid: the nodes are placed in a
 * {@code ceil(sqrt(n))}-column grid in the order given, and <em>a change of {@link Node#group()}
 * starts a new band</em> — one empty row apart — so a model's sub-domains read as separate blocks
 * rather than as one interleaved soup.  Boxes are then widened to fit their labels ({@link LabelLayout},
 * the same pass the reconstruction uses, which keeps one font size across the diagram), and a box is
 * made tall enough for its member compartments — the class's approved attributes and responsibilities,
 * which the PDF has always drawn and the D3 picture used to omit.  Each node also carries its
 * Coad/Lefebvre archetype ({@code ClassType}), which colours the box.</p>
 *
 * <p><b>Determinism is the caller's job, and that is deliberate.</b>  This class does not sort: the
 * order of {@code nodes} is the reading order of the grid.  The caller therefore sorts, and the
 * <em>seed</em> is reproducible from the model alone — which is what lets an offline guardrail compare
 * a payload with the previous one.  The client's simulation is seeded from it, so the settled picture
 * is reproducible too, up to the drag a reader applies by hand.</p>
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
     * A box's height when it carries <b>no members</b>, in model units: the client draws a name
     * compartment separator 20 units down and the label above it, and an empty compartment is how a UML
     * class box says "no members".  A box with members is taller — see {@link #heightOf}.
     */
    private static final double BOX_H = 40;

    /** The name compartment's height: the client separates it 20 units down, as it always has. */
    private static final double NAME_H = 20;

    /** Between a compartment's separator and its first member line, and before each later compartment. */
    private static final double MEMBER_PAD = 8;

    /** One member line, at the member font size the client draws. */
    private static final double MEMBER_LINE_H = 12;

    /** Kept clear between two columns: room for a label widened leftwards, plus a visible channel. */
    private static final double COLUMN_GAP = 72;

    /** Kept clear between two rows, so the seed leaves the client's simulation room to breathe. */
    private static final double ROW_GAP = 80;

    private static final double COLUMN_STEP = BOX_W + COLUMN_GAP;

    private UmlDiagramLayout() {
    }

    /**
     * One box: the id the links refer to, the label it draws, the band it belongs to, and — for the D3
     * picture, which draws them — the members it carries and the archetype that colours it.
     *
     * <p>{@code attributes} / {@code methods} are the export's own rendering of the approved members
     * ({@code name: type}, {@code name(params): ret}), one line each, and may be empty.  {@code archetype}
     * is the archetype's display name and {@code color} its CSS colour, or both {@code null} for a box
     * with no archetype (the sub-domain overview, which is a picture of the model's shape rather than of
     * its classes).  The three-argument constructor below is that no-members case, and is what the
     * overview uses.</p>
     */
    public record Node(String id, String name, String group,
                       List<String> attributes, List<String> methods,
                       String archetype, String color) {

        /** A box with no members and no archetype — the sub-domain overview's nodes. */
        public Node(final String id, final String name, final String group) {
            this(id, name, group, List.of(), List.of(), null, null);
        }
    }

    /**
     * One connector, before it is drawn.
     *
     * <p>{@code source} / {@code target} are read the way {@link Link} reads them: for a
     * {@link #GENERALIZATION} the <em>source is the super-class</em> — the arrow points at it.  A CRC
     * {@code CollaboratorCdd} states the opposite (its source is the sub-class), so the caller swaps the
     * ends when it maps one.</p>
     */
    public record Edge(String source, String target, String kind, String label) {
    }

    /**
     * Lays the diagram out and serialises it as the force-simulated D3 payload.
     *
     * @param title the diagram's display name, drawn as the title; blank is tolerated
     * @param nodes the boxes, in reading order (a change of {@code group} starts a new band)
     * @param edges the connectors, in the order they should be emitted
     * @return the {@code {"layout":"uml","force":true,…}} JSON the client simulates
     */
    public static String render(final String title, final List<Node> nodes, final List<Edge> edges) {
        final Map<String, Box> boxes = place(nodes);
        final Function<String, String> nameOf = nameOf(nodes);

        // Widen before anything is drawn: a box widened afterwards would overhang its neighbours, and the
        // client's collision force sizes itself from the rectangle it is handed.
        LabelLayout.widenToFitLabels(boxes, nameOf);

        final List<Link> links = new ArrayList<>();
        for (final Edge edge : edges) {
            // An endpoint the diagram does not draw would leave the connector a line into empty space.
            if (edge == null || !boxes.containsKey(edge.source()) || !boxes.containsKey(edge.target())) {
                continue;
            }
            final String kind = edge.kind() == null ? ASSOCIATION : edge.kind();
            // Straight centre-to-centre connectors, so no points at all: the client's simulation moves the
            // boxes, and a route drawn here would be stale the moment the first tick ran.  Every
            // generalization carries its own arrowhead — with no trunk for them to merge into there is
            // nothing to share one between, so `arrow` is simply "is this an is-a edge".
            links.add(new Link(edge.source(), edge.target(), kind,
                    edge.label() == null ? "" : edge.label(), List.of(), GENERALIZATION.equals(kind)));
        }

        final Bounds bounds = Bounds.of(boxes.values());

        // The client draws the boxes in this order, so sorting by label makes a box's z-order — and the
        // order a reader scans them in — independent of the order the model happened to list its classes.
        final List<String> nodeIds = new ArrayList<>(boxes.keySet());
        nodeIds.sort(Comparator.comparing(nameOf).thenComparing(id -> id));

        return DiagramJsonWriter.write(title == null ? "" : title, bounds, nodeIds, boxes, nameOf, links,
                true, detailOf(nodes));
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
     *
     * <p>The rectangles are a <b>seed</b>, not a finished picture: the client's simulation moves every box
     * from here.  They still matter — the forces are anchored to them, so a model's sub-domains stay in
     * the places the grid put them — which is why the row height is the tallest box <em>in that row</em>
     * rather than a constant: a box with members is several times the height of one without, and a fixed
     * step would seed them overlapping.</p>
     */
    private static Map<String, Box> place(final List<Node> nodes) {
        final Map<String, Box> boxes = new LinkedHashMap<>();
        final int columns = columnsFor(distinctIds(nodes).size());

        double y = 0;
        int column = 0;
        double rowHeight = 0;
        String band = null;
        for (final Node node : nodes) {
            if (node == null || node.id() == null || boxes.containsKey(node.id())) {
                continue;                       // the same id twice is one box, as PlantUML draws it
            }
            final String group = node.group() == null ? "" : node.group();
            if (!group.equals(band)) {
                if (band != null) {
                    if (column != 0) {
                        y += rowHeight + ROW_GAP;   // close the half-filled row the previous band ends in
                    }
                    y += BOX_H + ROW_GAP;           // and leave one empty row, so the bands read apart
                    column = 0;
                    rowHeight = 0;
                }
                band = group;
            }
            final double h = heightOf(node);
            boxes.put(node.id(), new Box(column * COLUMN_STEP, y, BOX_W, h));
            rowHeight = Math.max(rowHeight, h);
            if (++column == columns) {
                column = 0;
                y += rowHeight + ROW_GAP;
                rowHeight = 0;
            }
        }
        return boxes;
    }

    /**
     * How tall a box must be to hold its three compartments: the name, the attributes and the methods.
     *
     * <p>The name compartment is a fixed {@link #NAME_H}, because that is where the client draws its
     * separator.  A member compartment costs {@link #MEMBER_PAD} plus {@link #MEMBER_LINE_H} per line —
     * and <b>nothing at all when it is empty</b>, so a class with no members gets the plain 40-unit box
     * the picture has always drawn rather than an empty slot.  Both figures are the client's, and
     * {@code scripts/verify-uml-render.sh} is what keeps them honest.</p>
     */
    private static double heightOf(final Node node) {
        return Math.max(BOX_H, NAME_H + compartmentHeight(node.attributes()) + compartmentHeight(node.methods()));
    }

    /** A member compartment's height, or zero when it holds nothing — see {@link #heightOf}. */
    private static double compartmentHeight(final List<String> lines) {
        return lines == null || lines.isEmpty() ? 0 : MEMBER_PAD + lines.size() * MEMBER_LINE_H;
    }

    /**
     * A square-ish grid — never zero, so the placement loop is total: one box (and an empty diagram) gets a
     * single column, because {@code Math.max} floors it there whatever the count.
     */
    private static int columnsFor(final int nodeCount) {
        return Math.max(1, (int) Math.ceil(Math.sqrt(nodeCount)));
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

    /**
     * The members and archetype of each box, keyed by id — the first node given for an id wins, as in
     * {@link #nameOf}.  A box the caller did not describe (or an id with no node) reads as
     * {@link DiagramJsonWriter.NodeDetail#NONE}, so the writer never has to null-check.
     */
    private static Function<String, DiagramJsonWriter.NodeDetail> detailOf(final List<Node> nodes) {
        final Map<String, DiagramJsonWriter.NodeDetail> details = new LinkedHashMap<>();
        for (final Node node : nodes) {
            if (node != null && node.id() != null) {
                details.putIfAbsent(node.id(), new DiagramJsonWriter.NodeDetail(
                        node.attributes() == null ? List.of() : node.attributes(),
                        node.methods() == null ? List.of() : node.methods(),
                        node.archetype(), node.color()));
            }
        }
        return id -> details.getOrDefault(id, DiagramJsonWriter.NodeDetail.NONE);
    }
}
