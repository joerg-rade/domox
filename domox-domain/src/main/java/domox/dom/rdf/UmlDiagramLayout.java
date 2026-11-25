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
import java.util.Set;
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
 * rather than as one interleaved soup.  Inside a band a class is seeded <em>above</em> the classes that
 * inherit from it, in rows of inheritance depth, so a hierarchy reads top-down the way the PDF's own
 * layout draws it.  Each box is <b>as wide as the widest line it draws</b> — its class name, or an
 * attribute or a method with its parameters — and each column is as wide as the widest box in it, so
 * nothing has to be shrunk and no box reaches into the column beside it.  A box also draws the three
 * compartments the PDF has always drawn and the D3 picture used to omit — the name, the class's approved
 * attributes and its responsibilities — with a separator under the name and one between the two member
 * compartments, whether or not a compartment holds anything.  Each node also carries its Coad/Lefebvre
 * archetype ({@code ClassType}), which colours the box.</p>
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

    /**
     * Drawn when there is no graph to lay out (a detached view model, or a model with no included class): a
     * well-formed but empty payload, so the panel renders an empty canvas rather than failing on a
     * {@code null}.
     *
     * <p>Flavour-neutral on purpose — it carries neither {@code force} nor a canvas — so every view model
     * that falls back to it shares the one literal rather than repeating it.</p>
     */
    public static final String EMPTY_GRAPH = "{\"layout\":\"uml\",\"nodes\":[],\"links\":[],\"legend\":[]}";

    /** The link kinds the client picks its stroke colour and arrowhead from (see {@link Link}). */
    public static final String ASSOCIATION = Link.ASSOCIATION;

    /** @see #ASSOCIATION */
    public static final String GENERALIZATION = Link.GENERALIZATION;

    /**
     * A box's <b>minimum</b> width, in model units — wide enough for a ~22-character class name.  A box is
     * built wider than this when one of its lines needs the room: see {@link #widths}.
     */
    private static final double BOX_W = 180;

    /**
     * A box's minimum height, in model units: the client draws the label and the two compartment separators
     * inside it, and a box with members is taller — see {@link #heightOf}.  It is also what a class with no
     * members at all gets, because three empty compartments need less room than this.
     */
    private static final double BOX_H = 40;

    /** The name compartment's height: the client separates it 20 units down, as it always has. */
    private static final double NAME_H = 20;

    /** Between a compartment's separator and its first member line, and before each later compartment. */
    private static final double COMPARTMENT_PAD = 8;

    /** One member line, at the member font size the client draws. */
    private static final double MEMBER_LINE_H = 12;

    /** Kept clear between two columns: a visible channel, so two boxes never read as one. */
    private static final double COLUMN_GAP = 72;

    /** Kept clear between two rows, so the seed leaves the client's simulation room to breathe. */
    private static final double ROW_GAP = 80;

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
     * its classes).</p>
     *
     * <p><b>A package reuses {@code attributes}.</b>  On the overview each node is a sub-domain, drawn as a
     * package symbol, and its {@code attributes} are the classes of that sub-domain that relate to a class
     * outside it (see {@code ClassDiagram#graphOverview}).  There is no separate field for them: a package's
     * contents occupy the same place in the box the server sizes as a class's members do, so the geometry
     * needs no special case — only the client's drawing differs.  Those same lines are listed again in
     * {@code contents}, each with the class row it stands for — which is what makes a content line a link
     * (see {@link Content}).</p>
     *
     * <p><b>{@code objectType} / {@code candidateId} — the row behind the box.</b>  A box that stands for a
     * domain row states which one, and the client makes the box a click-through to that row's page
     * ({@code /wicket/object/<objectType>:<id>}).  The pair is the vocabulary the CRC diagrams already use
     * ({@code CandidateD3Graph}), so both pictures are handled by one rule on the client; {@code objectType}
     * is the <em>Causeway</em> object type — read back from {@code @Named} by
     * {@code Candidate#getObjectType()}, never derived from the class name, or the URL would name a type
     * the framework does not know.  <b>The two are set together or not at all</b>: a box with no row behind
     * it (the sub-domain overview's packages, the reconstruction of an export's own geometry) leaves both
     * {@code null} and stays inert.</p>
     */
    public record Node(String id, String name, String group,
                       List<String> attributes, List<String> methods,
                       String archetype, String color,
                       Long candidateId, String objectType,
                       List<Content> contents) {

        /**
         * One line of a package's <b>contents</b>: the class name the body draws, and the class row that
         * name stands for.
         *
         * <p>A class box <em>is</em> one row, and states it once with {@code objectType} /
         * {@code candidateId}.  A package is a <em>list</em> of rows, so every line has to state its own:
         * a reader who clicks {@code Order} inside the Inventory package means that class, not the
         * sub-domain it happens to sit in — which is why a package itself carries no pair at all.</p>
         *
         * <p>{@code objectType} / {@code candidateId} are set together or not at all, exactly as on a box:
         * a line whose row is unknown (a package built from a picture alone) leaves both {@code null} and
         * the client draws it inert rather than linking to half an address.</p>
         */
        public record Content(String name, String objectType, Long candidateId) {
        }

        /** A box with no members, no archetype, no row behind it and no contents — a class drawn empty. */
        public Node(final String id, final String name, final String group) {
            this(id, name, group, List.of(), List.of(), null, null, null, null, List.of());
        }

        /** The same, for a box that draws members but stands for no row — a caller with only a picture. */
        public Node(final String id, final String name, final String group,
                    final List<String> attributes, final List<String> methods,
                    final String archetype, final String color) {
            this(id, name, group, attributes, methods, archetype, color, null, null, List.of());
        }

        /** The same, for a box that stands for a row but lists no contents — a class box. */
        public Node(final String id, final String name, final String group,
                    final List<String> attributes, final List<String> methods,
                    final String archetype, final String color,
                    final Long candidateId, final String objectType) {
            this(id, name, group, attributes, methods, archetype, color, candidateId, objectType,
                    List.of());
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
        return render(title, nodes, edges, false);
    }

    /**
     * The same, with each node drawn as a UML <b>package</b> rather than as a class box when {@code
     * packages} is set — the sub-domain overview, where a node <em>is</em> a sub-domain and its members are
     * the classes of that sub-domain that relate to a class outside it.
     *
     * <p>Nothing about the geometry changes: the seed grid, the bands and the box sizes are the same, and
     * the client swaps only what it draws inside each rectangle.  That is deliberate — the two flavours
     * must stay one code path, or the overview would drift away from the per-sub-domain diagrams it sits
     * beside.</p>
     *
     * @param packages whether the nodes are sub-domains to be drawn as packages
     */
    public static String render(final String title, final List<Node> nodes, final List<Edge> edges,
                                final boolean packages) {
        final Function<String, String> nameOf = nameOf(nodes);
        // Each box is built to fit its content, so nothing has to be widened afterwards — a box widened
        // after it was placed would overhang its neighbours, and the client's collision force sizes itself
        // from the rectangle it is handed.
        final Map<String, Box> boxes = place(nodes, edges, nameOf);
        final List<Link> links = linksOf(edges, boxes.keySet());

        // A box the is-a arrows point at draws its name in italics, so the client has to be told which ones
        // they are.  The picture decides it, not the model: a class nothing inherits from is not a
        // super-class in *this* diagram, however it may be used elsewhere.
        final Set<String> superclasses = superclassesOf(links);
        final Function<String, DiagramJsonWriter.NodeDetail> details = detailOf(nodes);

        return DiagramJsonWriter.write(title == null ? "" : title, Bounds.of(boxes.values()),
                nodeIdsOf(boxes, nameOf), boxes, nameOf, links, true, packages,
                id -> markIfSuperclass(details.apply(id), superclasses.contains(id)));
    }

    /**
     * The connectors to draw: one per edge whose <em>two ends are both drawn here</em>, in the order given.
     *
     * <p>Straight centre-to-centre connectors, so no points at all: the client's simulation moves the boxes,
     * and a route drawn here would be stale the moment the first tick ran.  Every generalization carries its
     * own arrowhead — with no trunk for them to merge into there is nothing to share one between, so
     * {@code arrow} is simply "is this an is-a edge".</p>
     */
    private static List<Link> linksOf(final List<Edge> edges, final Set<String> drawnIds) {
        final List<Link> links = new ArrayList<>();
        for (final Edge edge : edges) {
            // An endpoint the diagram does not draw would leave the connector a line into empty space.
            if (edge == null || !drawnIds.contains(edge.source()) || !drawnIds.contains(edge.target())) {
                continue;
            }
            final String kind = edge.kind() == null ? ASSOCIATION : edge.kind();
            links.add(new Link(edge.source(), edge.target(), kind,
                    edge.label() == null ? "" : edge.label(), List.of(), GENERALIZATION.equals(kind)));
        }
        return links;
    }

    /**
     * The boxes in the order the client draws them — by label, so a box's z-order, and the order a reader
     * scans them in, are independent of the order the model happened to list its classes.
     */
    private static List<String> nodeIdsOf(final Map<String, Box> boxes, final Function<String, String> nameOf) {
        final List<String> nodeIds = new ArrayList<>(boxes.keySet());
        nodeIds.sort(Comparator.comparing(nameOf).thenComparing(id -> id));
        return nodeIds;
    }

    /** A box's members and archetype, marked as a super-class when the is-a arrows point at it. */
    private static DiagramJsonWriter.NodeDetail markIfSuperclass(
            final DiagramJsonWriter.NodeDetail detail, final boolean superclass) {
        return superclass ? detail.asSuperclass() : detail;
    }

    /**
     * The ids the is-a arrows point at — the boxes whose names are drawn in italics.
     *
     * <p>A {@link Link} states a generalization the way its arrow reads: its {@code source} is the
     * super-class, which is also the end the arrowhead is drawn on (see {@link Edge}).  So the set is read
     * off the links, and a diagram with no inheritance in it has an empty one.</p>
     */
    private static Set<String> superclassesOf(final List<Link> links) {
        final Set<String> superclasses = new LinkedHashSet<>();
        for (final Link link : links) {
            if (GENERALIZATION.equals(link.kind())) {
                superclasses.add(link.source());
            }
        }
        return superclasses;
    }

    // -- the grid --------------------------------------------------------------

    /**
     * Places the boxes on a {@code ceil(sqrt(n))}-column grid, in the order given, sizing each column to
     * the widest box that lands in it and <b>starting a new band — one empty row below the previous one —
     * whenever the group changes</b>.
     *
     * <p><b>A column is as wide as the widest box in it</b>, because a box is built to fit its widest line
     * (see {@link #widths}): a responsibility can carry six parameters, so one box may be several times the
     * base width, and a fixed step would either overlap the column beside it or leave every other column
     * mostly empty.</p>
     *
     * <p><b>Inside a band, a class sits above the classes that inherit from it</b> when it can: the band is
     * filled in rows of inheritance depth (see {@link #depthsOf}), so a super-class and its sub-classes read
     * top-down the way the PDF's own layout draws them.  A band with no is-a edge in it — and one whose
     * is-a edges run in a circle, where there is no "above" to draw — is a single row, which is the plain
     * grid this has always been.</p>
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
    private static Map<String, Box> place(final List<Node> nodes, final List<Edge> edges,
                                          final Function<String, String> nameOf) {
        final int columns = columnsFor(distinctIds(nodes).size());
        final Map<String, Slot> slots = slots(nodes, edges, widths(nodes, nameOf), columns);
        final double[] offsets = columnOffsets(slots, columns);
        final Map<String, Box> boxes = new LinkedHashMap<>();
        for (final Map.Entry<String, Slot> entry : slots.entrySet()) {
            final Slot slot = entry.getValue();
            boxes.put(entry.getKey(), new Box(offsets[slot.column()], slot.y(), slot.width(), slot.height()));
        }
        return boxes;
    }

    /**
     * One box's place on the grid, before the columns are given their widths: the column it landed in, its
     * top edge, and the size {@link #widths} and {@link #heightOf} gave it.
     */
    private record Slot(int column, double y, double width, double height) {
    }

    /**
     * The left edge of every column: as wide as the widest box that lands in it, plus {@link #COLUMN_GAP},
     * so a box built to fit its content never reaches into the column beside it.
     */
    private static double[] columnOffsets(final Map<String, Slot> slots, final int columns) {
        final double[] width = new double[columns];
        for (final Slot slot : slots.values()) {
            width[slot.column()] = Math.max(width[slot.column()], slot.width());
        }
        final double[] offsets = new double[columns];
        for (int column = 1; column < columns; column++) {
            offsets[column] = offsets[column - 1] + width[column - 1] + COLUMN_GAP;
        }
        return offsets;
    }

    /** Fills every band in turn, one empty row apart, and returns each box's slot in the order placed. */
    private static Map<String, Slot> slots(final List<Node> nodes, final List<Edge> edges,
                                           final Map<String, Double> widths, final int columns) {
        final Map<String, Slot> slots = new LinkedHashMap<>();
        double y = 0;
        boolean first = true;
        for (final List<Node> band : bands(nodes)) {
            if (!first) {
                y += BOX_H + ROW_GAP;           // one empty row, so the bands read apart
            }
            first = false;
            y = placeBand(band, edges, widths, columns, y, slots);
        }
        return slots;
    }

    /**
     * The bands, in the order given: a maximal run of one group is a band, and an id that has already been
     * seen is dropped — the same id twice is <em>one</em> box, as PlantUML draws two classes of the same
     * name.
     */
    private static List<List<Node>> bands(final List<Node> nodes) {
        final List<List<Node>> bands = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        String group = null;
        for (final Node node : nodes) {
            if (node == null || node.id() == null || !seen.add(node.id())) {
                continue;
            }
            final String actual = node.group() == null ? "" : node.group();
            if (bands.isEmpty() || !actual.equals(group)) {
                bands.add(new ArrayList<>());
                group = actual;
            }
            bands.getLast().add(node);
        }
        return bands;
    }

    /**
     * Fills one band in rows of inheritance depth and returns the top edge below it.
     *
     * <p>Depth 0 is the band's first row — the classes that nothing else in the band is a sub-class of —
     * then the classes one below those, and so on, so a super-class is seeded <em>above</em> the classes
     * that inherit from it.  A row holds {@code columns} boxes and then wraps, and it is closed when the
     * depth's boxes run out, however much the wrap left it half-filled.</p>
     *
     * <p><b>A sub-class takes its super-class's column</b> (see {@link #columnFor}), so a hierarchy reads
     * top-down <em>in one column</em>: the is-a connector between the two boxes is then a short vertical line
     * inside that column, crossing nothing — where filling the row in the band's own order put the sub-class
     * wherever its index happened to fall, often several columns away, and the connector ran diagonally
     * across the boxes between them.</p>
     */
    private static double placeBand(final List<Node> band, final List<Edge> edges,
                                    final Map<String, Double> widths, final int columns,
                                    double y, final Map<String, Slot> slots) {
        final Map<String, Integer> depth = depthsOf(band, edges);
        final Map<String, List<String>> supers = supersInBand(band, edges);
        final Map<String, Integer> columnOf = new LinkedHashMap<>();
        final int deepest = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (int at = 0; at <= deepest; at++) {
            final Set<Integer> used = new LinkedHashSet<>();    // the columns taken in the row being filled
            double rowHeight = 0;
            for (final Node node : band) {
                if (depth.get(node.id()) != at) {
                    continue;                   // it belongs to another row
                }
                int column = columnFor(node, depth, supers, columnOf, used, at, columns);
                if (column < 0) {               // this row is full: wrap and start a fresh one
                    y += rowHeight + ROW_GAP;
                    rowHeight = 0;
                    used.clear();
                    column = columnFor(node, depth, supers, columnOf, used, at, columns);
                }
                final double height = heightOf(node);
                slots.put(node.id(), new Slot(column, y, widths.get(node.id()), height));
                columnOf.put(node.id(), column);
                used.add(column);
                rowHeight = Math.max(rowHeight, height);
            }
            y += rowHeight + ROW_GAP;           // close the row this depth ends in
        }
        return y;
    }

    /**
     * The column a box goes in: the column of a super-class that is <em>already placed in this band</em> and
     * whose column is still free in this row — which is what puts a sub-class directly under its super-class
     * — else the first free column, so the grid stays compact.
     *
     * <p>A super-class is always in a row above (the depth is the longest path from a root), so it is placed
     * before its sub-classes are, and a cycle — where there is no row above — has already been flattened to
     * one row by {@link #depthsOf}, leaving every box with no placed super-class.  The choice is a pure
     * function of the band and the columns taken so far, so the seed stays reproducible.</p>
     *
     * @return the column, or {@code -1} when the row has no free one left, so the caller can wrap
     */
    private static int columnFor(final Node node, final Map<String, Integer> depth,
                                 final Map<String, List<String>> supers,
                                 final Map<String, Integer> columnOf, final Set<Integer> used,
                                 final int at, final int columns) {
        for (final String parent : supers.getOrDefault(node.id(), List.of())) {
            final Integer column = columnOf.get(parent);
            if (column != null && depth.getOrDefault(parent, at) < at && !used.contains(column)) {
                return column;
            }
        }
        for (int column = 0; column < columns; column++) {
            if (!used.contains(column)) {
                return column;
            }
        }
        return -1;
    }

    /** Each box's super-classes <em>within its band</em>, keyed by the sub-class — see {@link #depthsOf}. */
    private static Map<String, List<String>> supersInBand(final List<Node> band, final List<Edge> edges) {
        final Set<String> ids = idsOf(band);
        final Map<String, List<String>> supers = new LinkedHashMap<>();
        for (final Edge edge : isAEdgesIn(ids, edges)) {
            supers.computeIfAbsent(edge.target(), key -> new ArrayList<>()).add(edge.source());
        }
        return supers;
    }

    /** The ids of the boxes in one band, in the order given — a duplicate id is one box. */
    private static Set<String> idsOf(final List<Node> band) {
        final Set<String> ids = new LinkedHashSet<>();
        for (final Node node : band) {
            ids.add(node.id());
        }
        return ids;
    }

    /** The is-a edges with <em>both</em> ends in this band — the only ones that can order its rows. */
    private static List<Edge> isAEdgesIn(final Set<String> ids, final List<Edge> edges) {
        final List<Edge> isA = new ArrayList<>();
        for (final Edge edge : edges) {
            if (edge != null && GENERALIZATION.equals(edge.kind())
                    && ids.contains(edge.source()) && ids.contains(edge.target())) {
                isA.add(edge);
            }
        }
        return isA;
    }

    /**
     * Each box's <b>inheritance depth</b> in its band: {@code 0} for a class that no other class in the band
     * inherits from, and one more than its deepest super-class otherwise.
     *
     * <p>Only the is-a edges with <em>both</em> ends drawn in this band count.  A band is a sub-domain and
     * is laid out on its own, so an edge that leaves it cannot say what is above what — and a sub-domain
     * diagram drops those edges anyway.</p>
     *
     * <p>The depth is the longest path from a root, found by relaxation: a round pushes every sub-class at
     * least one below each of its super-classes, and the round that moves nothing has the answer.  If a
     * round is <em>still</em> moving after one round per class, the is-a relation runs in a circle — a class
     * that is, transitively, its own super-class — and there is no "above" to draw, so every box in the band
     * goes back onto one row: the plain grid.  That is the "if possible" of the layering, and it is the only
     * thing a circle can sensibly produce.</p>
     */
    private static Map<String, Integer> depthsOf(final List<Node> band, final List<Edge> edges) {
        final Set<String> ids = idsOf(band);
        final List<Edge> isA = isAEdgesIn(ids, edges);
        final Map<String, Integer> depth = new LinkedHashMap<>();
        for (final String id : ids) {
            depth.put(id, 0);
        }
        for (int round = 0; round < ids.size(); round++) {
            if (!relax(isA, depth)) {
                return depth;                   // settled: the longest path from a root
            }
        }
        for (final String id : ids) {
            depth.put(id, 0);                   // a circle: there is no above, so there is one row
        }
        return depth;
    }

    /**
     * One relaxation round: every sub-class at least one below each of its super-classes.
     *
     * @return whether any depth moved, so the caller can tell "settled" from "still going round in circles"
     */
    private static boolean relax(final List<Edge> isA, final Map<String, Integer> depth) {
        boolean moved = false;
        for (final Edge edge : isA) {
            final int below = depth.get(edge.source()) + 1;
            if (depth.get(edge.target()) < below) {
                depth.put(edge.target(), below);
                moved = true;
            }
        }
        return moved;
    }

    /**
     * How tall a box must be to hold its three compartments: the name, the attributes and the methods.
     *
     * <p>The name compartment is a fixed {@link #NAME_H}, because that is where the client draws its first
     * separator.  A member compartment costs {@link #COMPARTMENT_PAD} plus {@link #MEMBER_LINE_H} per line
     * <b>whether or not it holds anything</b>: the client draws all three compartments and both separators
     * on every box — the classic UML class box, and what the PDF draws — so an empty compartment still
     * takes its padding.  A class with no members at all keeps the plain {@link #BOX_H} the picture has
     * always drawn, because the minimum is more than three empty compartments need.  Both figures are the
     * client's, and {@code scripts/verify-uml-render.sh} is what keeps them honest.</p>
     */
    private static double heightOf(final Node node) {
        return Math.max(BOX_H, NAME_H + compartmentHeight(node.attributes()) + compartmentHeight(node.methods()));
    }

    /** A member compartment's height — its padding plus one line per member — see {@link #heightOf}. */
    private static double compartmentHeight(final List<String> lines) {
        return COMPARTMENT_PAD + (lines == null ? 0 : lines.size() * MEMBER_LINE_H);
    }

    /**
     * The width each box must offer: <b>the widest line it draws</b> — its class name at the label font, or
     * an attribute or a method with its parameters at the member font.
     *
     * <p>This is what the reconstruction does with its widening pass ({@link LabelLayout}) and this path does
     * not need: there a box's width comes from the model and is grown until the name fits, because the
     * picture has to stay where the model's author put it; here the boxes are the layout's own, so they are
     * simply <em>built</em> to fit and nothing has to be shrunk.  A line can still overrun its box — the
     * estimate is a table, not a measurement — and the client shrinks that one line, exactly as it does on
     * the reconstruction.</p>
     */
    private static Map<String, Double> widths(final List<Node> nodes, final Function<String, String> nameOf) {
        final Map<String, Double> widths = new LinkedHashMap<>();
        for (final Node node : nodes) {
            if (node != null && node.id() != null) {
                widths.putIfAbsent(node.id(), widestLineOf(node, nameOf.apply(node.id())));
            }
        }
        return widths;
    }

    /** The width one box needs for its class name and its members, whichever is the wider. */
    private static double widestLineOf(final Node node, final String name) {
        double widest = Math.max(BOX_W,
                LabelMetrics.estimatedLabelWidth(name) + LabelMetrics.LABEL_PADDING);
        widest = Math.max(widest, widestMemberLine(node.attributes()));
        return Math.max(widest, widestMemberLine(node.methods()));
    }

    /** The widest line of one compartment, with the padding the client keeps inside a box around a line. */
    private static double widestMemberLine(final List<String> lines) {
        if (lines == null) {
            return 0;
        }
        double widest = 0;
        for (final String line : lines) {
            widest = Math.max(widest, LabelMetrics.estimatedMemberWidth(line) + LabelMetrics.MEMBER_WIDTH_PADDING);
        }
        return widest;
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
     * The members, the contents, the archetype and the row behind each box, keyed by id — the first node
     * given for an id wins, as in {@link #nameOf}.  A box the caller did not describe (or an id with no
     * node) reads as {@link DiagramJsonWriter.NodeDetail#NONE}, so the writer never has to null-check.
     */
    private static Function<String, DiagramJsonWriter.NodeDetail> detailOf(final List<Node> nodes) {
        final Map<String, DiagramJsonWriter.NodeDetail> details = new LinkedHashMap<>();
        for (final Node node : nodes) {
            if (node != null && node.id() != null) {
                details.putIfAbsent(node.id(), new DiagramJsonWriter.NodeDetail(
                        node.attributes() == null ? List.of() : node.attributes(),
                        node.methods() == null ? List.of() : node.methods(),
                        node.archetype(), node.color(), false,
                        node.objectType(), node.candidateId(),
                        contentsOf(node)));
            }
        }
        return id -> details.getOrDefault(id, DiagramJsonWriter.NodeDetail.NONE);
    }

    /**
     * A box's content lines, in the writer's own record — empty for a box that lists none (every class
     * box), so the writer emits the key only where there is something to say.
     */
    private static List<DiagramJsonWriter.ContentLine> contentsOf(final Node node) {
        final List<DiagramJsonWriter.ContentLine> lines = new ArrayList<>();
        for (final Node.Content content : node.contents() == null
                ? List.<Node.Content>of() : node.contents()) {
            if (content != null && content.name() != null) {
                lines.add(new DiagramJsonWriter.ContentLine(content.name(), content.objectType(),
                        content.candidateId()));
            }
        }
        return lines;
    }
}
