package domox.dom.rdf;

import domox.port.RdfGraphTO;
import domox.port.RdfTripleTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static domox.dom.rdf.UmlLayoutFixture.M;
import static domox.dom.rdf.UmlLayoutFixture.OU;
import static domox.dom.rdf.UmlLayoutFixture.RDF_TYPE;
import static domox.dom.rdf.UmlLayoutFixture.classView;
import static domox.dom.rdf.UmlLayoutFixture.sampleGraph;
import static domox.port.RdfTripleTO.literal;
import static domox.port.RdfTripleTO.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-level guardrail for the UML-layout reconstruction: an OntoUML export's view/layout resources
 * map back to boxes and connectors carrying their original geometry.
 * <p>
 * The end-to-end path over the real model (Jena load → layout JSON) is exercised live against the
 * bundled {@code ontology.ttl}; this pins the mapping rules without a Semantic-Web dependency.
 */
class UmlLayoutDiagramsTest {

    private final UmlLayoutDiagrams diagrams = new UmlLayoutDiagrams();

    @Test
    void namesTheDiagramsTheExportShips() {
        assertThat(diagrams.diagramNames(sampleGraph())).isEqualTo(List.of("Demo"));
    }

    /**
     * The boxes come back with the export's own rectangles, shifted so the drawing starts at the
     * padding — A (100,50) → (24,24) and B (500,300) → (424,274) for a min corner of (100,50).
     */
    @Test
    void reconstructsBoxesAtTheirStoredCoordinates() {
        final String json = diagrams.renderLayout(sampleGraph(), "Demo");

        assertThat(json).contains("\"layout\":\"uml\"");
        assertThat(json).contains("\"title\":\"Demo\"");
        assertThat(json).contains("\"canvas\":{\"width\":608,\"height\":358}");
        assertThat(json).contains("\"name\":\"A\",\"kind\":\"Class\",\"x\":24,\"y\":24,\"w\":200,\"h\":60");
        assertThat(json).contains("\"name\":\"B\",\"kind\":\"Class\",\"x\":424,\"y\":274,\"w\":160,\"h\":60");
    }

    /**
     * The connector runs from the target box to the source box — the direction the export states, and the
     * one that puts a generalization's arrowhead on the super-class — and both ends sit on the box edges,
     * because the box is drawn on top of whatever lies under it.
     */
    @Test
    void routesEachConnectorFromTheTargetBoxToTheSourceBox() {
        final String json = diagrams.renderLayout(sampleGraph(), "Demo");

        assertThat(json).contains("\"kind\":\"generalization\"");
        assertThat(json).as("the route leaves the sub-class's top middle and enters the super-class's bottom")
                .contains("\"points\":[[504,274],[504,254],[124,254],[124,108],[124,84]]");
    }

    /** A blank name picks the richest diagram rather than failing. */
    @Test
    void blankNamePicksTheRichestDiagram() {
        assertThat(diagrams.renderLayout(sampleGraph(), " ")).contains("\"title\":\"Demo\"");
    }

    @Test
    void rejectsAnUnknownDiagramName() {
        final RdfGraphTO graph = sampleGraph();

        assertThatThrownBy(() -> diagrams.renderLayout(graph, "nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope")
                .hasMessageContaining("Demo");
    }

    /** A plain OWL graph has no views, so there is no stored picture to reconstruct. */
    @Test
    void reportsAGraphWithoutAnyDiagram() {
        final RdfGraphTO plain = new RdfGraphTO(List.of(
                resource(M + "Cat", RDF_TYPE, "http://www.w3.org/2002/07/owl#Class")), Map.of());

        assertThat(diagrams.diagramNames(plain)).isEmpty();
        assertThatThrownBy(() -> diagrams.renderLayout(plain, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A connector whose endpoint is not a drawable box is skipped: the export points a RelationView at
     * another RelationView (whose shape is a Path, not a Rectangle), and a line into empty space is
     * worse than no line.
     */
    @Test
    void skipsConnectorsWhoseEndpointHasNoRectangle() {
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                resource(M + "d1", RDF_TYPE, OU + "Diagram"),
                literal(M + "d1", OU + "name", "Demo"),
                resource(M + "d1", OU + "containsView", M + "cvA"),

                resource(M + "A", RDF_TYPE, OU + "Class"),
                literal(M + "A", OU + "name", "A"),
                resource(M + "cvA", RDF_TYPE, OU + "ClassView"),
                resource(M + "cvA", OU + "isViewOf", M + "A"),
                resource(M + "cvA", OU + "shape", M + "rA"),
                resource(M + "rA", OU + "topLeftPosition", M + "rA_tl"),
                literal(M + "rA_tl", OU + "xCoordinate", "10"),
                literal(M + "rA_tl", OU + "yCoordinate", "10"),
                literal(M + "rA", OU + "width", "100"),
                literal(M + "rA", OU + "height", "50"),

                // rv1 runs from rv2 — a RelationView whose shape is a Path, so it has no box
                resource(M + "rv1", RDF_TYPE, OU + "RelationView"),
                resource(M + "rv1", OU + "sourceView", M + "rv2"),
                resource(M + "rv1", OU + "targetView", M + "cvA"),
                resource(M + "rv2", RDF_TYPE, OU + "RelationView"),
                resource(M + "rv2", OU + "shape", M + "rv2_path")
        ), Map.of());

        final String json = diagrams.renderLayout(graph, "Demo");

        assertThat(json).contains("\"name\":\"A\"");
        assertThat(json).as("the undrawable connector is skipped").contains("\"links\":[]");
    }

    /** The legend names what the drawing shows, and every entry says which swatch to draw. */
    @Test
    void explainsTheLineKindsInItsLegend() {
        final String json = diagrams.renderLayout(sampleGraph(), "Demo");

        assertThat(json).contains("{\"kind\":\"node\",\"color\":\"#3498DB\"");
        assertThat(json).contains("{\"kind\":\"association\",\"color\":\"#7f8c8d\"");
        assertThat(json).contains("{\"kind\":\"generalization\",\"color\":\"#e67e22\"");
        assertThat(json).as("the generalization is explained, not just named").contains("super-class");
    }

    /**
     * The export states path points in an order that is <em>not</em> the drawing order — here the bend
     * near the source is listed before the one near the target, which draws as a line that doubles back
     * and appears to stop in mid-air.  The reconstruction re-chains them, starting at the end that sits
     * on the target box (where the export always begins), and those two ends are what the router is told
     * to attach to — so a mis-chained path would attach to the wrong side of a box.
     */
    @Test
    void reChainsPathPointsThatTheExportStatedOutOfOrder() {
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                resource(M + "d1", RDF_TYPE, OU + "Diagram"),
                literal(M + "d1", OU + "name", "Demo"),
                resource(M + "d1", OU + "containsView", M + "cvA"),
                resource(M + "d1", OU + "containsView", M + "cvB"),
                resource(M + "d1", OU + "containsView", M + "gv1"),

                // the target box (the super-class), at (100,100) 200x60
                resource(M + "A", RDF_TYPE, OU + "Class"),
                literal(M + "A", OU + "name", "A"),
                resource(M + "cvA", RDF_TYPE, OU + "ClassView"),
                resource(M + "cvA", OU + "isViewOf", M + "A"),
                resource(M + "cvA", OU + "shape", M + "rA"),
                resource(M + "rA", OU + "topLeftPosition", M + "rA_tl"),
                literal(M + "rA_tl", OU + "xCoordinate", "100"),
                literal(M + "rA_tl", OU + "yCoordinate", "100"),
                literal(M + "rA", OU + "width", "200"),
                literal(M + "rA", OU + "height", "60"),

                // the source box (the sub-class), at (700,400) 200x60
                resource(M + "B", RDF_TYPE, OU + "Class"),
                literal(M + "B", OU + "name", "B"),
                resource(M + "cvB", RDF_TYPE, OU + "ClassView"),
                resource(M + "cvB", OU + "isViewOf", M + "B"),
                resource(M + "cvB", OU + "shape", M + "rB"),
                resource(M + "rB", OU + "topLeftPosition", M + "rB_tl"),
                literal(M + "rB_tl", OU + "xCoordinate", "700"),
                literal(M + "rB_tl", OU + "yCoordinate", "400"),
                literal(M + "rB", OU + "width", "200"),
                literal(M + "rB", OU + "height", "60"),

                // a clean Z route — but stated as _0 (target), _1 (far bend), _2 (near bend), _3 (source)
                resource(M + "gv1", RDF_TYPE, OU + "GeneralizationView"),
                resource(M + "gv1", OU + "sourceView", M + "cvB"),
                resource(M + "gv1", OU + "targetView", M + "cvA"),
                resource(M + "gv1", OU + "shape", M + "p1"),
                resource(M + "p1", OU + "point", M + "p1_pt_0"),
                resource(M + "p1", OU + "point", M + "p1_pt_1"),
                resource(M + "p1", OU + "point", M + "p1_pt_2"),
                resource(M + "p1", OU + "point", M + "p1_pt_3"),
                literal(M + "p1_pt_0", OU + "xCoordinate", "120"),
                literal(M + "p1_pt_0", OU + "yCoordinate", "140"),
                literal(M + "p1_pt_1", OU + "xCoordinate", "700"),
                literal(M + "p1_pt_1", OU + "yCoordinate", "300"),
                literal(M + "p1_pt_2", OU + "xCoordinate", "120"),
                literal(M + "p1_pt_2", OU + "yCoordinate", "300"),
                literal(M + "p1_pt_3", OU + "xCoordinate", "700"),
                literal(M + "p1_pt_3", OU + "yCoordinate", "430")
        ), Map.of());

        final String json = diagrams.renderLayout(graph, "Demo");

        assertThat(json).contains("\"canvas\":{\"width\":848,\"height\":428}");
        // The route leaves the middle of A's top edge — (200,100), going up 20 units before it turns — and
        // runs down B's top edge's normal for 24 units (the trunk) before entering it at (800,400).
        assertThat(json).as("the route leaves the sub-class's top middle and enters the super-class's top")
                .contains("\"points\":[[124,44],[124,24],[724,24],[724,320],[724,344]]");
    }

    /**
     * The export marks a connector whose geometry it could not store with {@code (0,0)} sentinels —
     * {@code (0,0) → bend → (0,0)} — which would draw a line out of the canvas corner and straight
     * back.  The path is dropped, so the connector carries no attachment hint and the router attaches it
     * to the box edges it finds cheapest.
     */
    @Test
    void dropsDegeneratePathsBuiltFromOriginSentinels() {
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                resource(M + "d1", RDF_TYPE, OU + "Diagram"),
                literal(M + "d1", OU + "name", "Demo"),
                resource(M + "d1", OU + "containsView", M + "cvA"),
                resource(M + "d1", OU + "containsView", M + "cvB"),
                resource(M + "d1", OU + "containsView", M + "rv1"),

                resource(M + "A", RDF_TYPE, OU + "Class"),
                literal(M + "A", OU + "name", "A"),
                resource(M + "cvA", RDF_TYPE, OU + "ClassView"),
                resource(M + "cvA", OU + "isViewOf", M + "A"),
                resource(M + "cvA", OU + "shape", M + "rA"),
                resource(M + "rA", OU + "topLeftPosition", M + "rA_tl"),
                literal(M + "rA_tl", OU + "xCoordinate", "100"),
                literal(M + "rA_tl", OU + "yCoordinate", "100"),
                literal(M + "rA", OU + "width", "200"),
                literal(M + "rA", OU + "height", "60"),

                resource(M + "B", RDF_TYPE, OU + "Class"),
                literal(M + "B", OU + "name", "B"),
                resource(M + "cvB", RDF_TYPE, OU + "ClassView"),
                resource(M + "cvB", OU + "isViewOf", M + "B"),
                resource(M + "cvB", OU + "shape", M + "rB"),
                resource(M + "rB", OU + "topLeftPosition", M + "rB_tl"),
                literal(M + "rB_tl", OU + "xCoordinate", "500"),
                literal(M + "rB_tl", OU + "yCoordinate", "300"),
                literal(M + "rB", OU + "width", "200"),
                literal(M + "rB", OU + "height", "60"),

                resource(M + "rv1", RDF_TYPE, OU + "RelationView"),
                resource(M + "rv1", OU + "sourceView", M + "cvA"),
                resource(M + "rv1", OU + "targetView", M + "cvB"),
                resource(M + "rv1", OU + "shape", M + "p1"),
                resource(M + "p1", OU + "point", M + "p1_pt_0"),
                resource(M + "p1", OU + "point", M + "p1_pt_1"),
                resource(M + "p1", OU + "point", M + "p1_pt_2"),
                literal(M + "p1_pt_0", OU + "xCoordinate", "0"),
                literal(M + "p1_pt_0", OU + "yCoordinate", "0"),
                literal(M + "p1_pt_1", OU + "xCoordinate", "1815"),
                literal(M + "p1_pt_1", OU + "yCoordinate", "1128"),
                literal(M + "p1_pt_2", OU + "xCoordinate", "0"),
                literal(M + "p1_pt_2", OU + "yCoordinate", "0")
        ), Map.of());

        final String json = diagrams.renderLayout(graph, "Demo");

        assertThat(json).as("the degenerate path is replaced by an orthogonal route between the box edges")
                .contains("\"points\":[[424,254],[424,54],[224,54]]");
    }

    /**
     * A bend that lies outside the drawing is not part of the route: the export sometimes leaves a point
     * belonging to <em>another</em> diagram in a path (a UFO-C connector carrying a point from the
     * far-right region where the UFO-A views sit), which draws a line off into empty space.  Dropping it
     * leaves the two endpoint points, and the canvas shrinks back to the real drawing.
     */
    @Test
    void dropsBendsThatLieOutsideTheDrawing() {
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                resource(M + "d1", RDF_TYPE, OU + "Diagram"),
                literal(M + "d1", OU + "name", "Demo"),
                resource(M + "d1", OU + "containsView", M + "cvA"),
                resource(M + "d1", OU + "containsView", M + "cvB"),
                resource(M + "d1", OU + "containsView", M + "rv1"),

                resource(M + "A", RDF_TYPE, OU + "Class"),
                literal(M + "A", OU + "name", "A"),
                resource(M + "cvA", RDF_TYPE, OU + "ClassView"),
                resource(M + "cvA", OU + "isViewOf", M + "A"),
                resource(M + "cvA", OU + "shape", M + "rA"),
                resource(M + "rA", OU + "topLeftPosition", M + "rA_tl"),
                literal(M + "rA_tl", OU + "xCoordinate", "100"),
                literal(M + "rA_tl", OU + "yCoordinate", "100"),
                literal(M + "rA", OU + "width", "200"),
                literal(M + "rA", OU + "height", "60"),

                resource(M + "B", RDF_TYPE, OU + "Class"),
                literal(M + "B", OU + "name", "B"),
                resource(M + "cvB", RDF_TYPE, OU + "ClassView"),
                resource(M + "cvB", OU + "isViewOf", M + "B"),
                resource(M + "cvB", OU + "shape", M + "rB"),
                resource(M + "rB", OU + "topLeftPosition", M + "rB_tl"),
                literal(M + "rB_tl", OU + "xCoordinate", "500"),
                literal(M + "rB_tl", OU + "yCoordinate", "300"),
                literal(M + "rB", OU + "width", "200"),
                literal(M + "rB", OU + "height", "60"),

                resource(M + "rv1", RDF_TYPE, OU + "RelationView"),
                resource(M + "rv1", OU + "sourceView", M + "cvA"),
                resource(M + "rv1", OU + "targetView", M + "cvB"),
                resource(M + "rv1", OU + "shape", M + "p1"),
                resource(M + "p1", OU + "point", M + "p1_pt_0"),
                resource(M + "p1", OU + "point", M + "p1_pt_1"),
                resource(M + "p1", OU + "point", M + "p1_pt_2"),
                literal(M + "p1_pt_0", OU + "xCoordinate", "110"),
                literal(M + "p1_pt_0", OU + "yCoordinate", "130"),
                // a point borrowed from another diagram, 1300 units away from anything here
                literal(M + "p1_pt_1", OU + "xCoordinate", "2000"),
                literal(M + "p1_pt_1", OU + "yCoordinate", "2000"),
                literal(M + "p1_pt_2", OU + "xCoordinate", "510"),
                literal(M + "p1_pt_2", OU + "yCoordinate", "330")
        ), Map.of());

        final String json = diagrams.renderLayout(graph, "Demo");

        assertThat(json).as("the out-of-drawing bend is dropped and the route made orthogonal")
                .contains("\"points\":[[424,254],[124,254],[124,84]]");
        assertThat(json).as("the canvas is the drawing, not the stray point")
                .contains("\"canvas\":{\"width\":648,\"height\":308}");
    }

    /**
     * A UML export ends a path <em>inside</em> the box it points at, and the boxes are drawn on top of the
     * connectors — so an arrowhead whose tip sat under the box was painted over.  Both ends are pulled
     * out to the box edges, which is what makes every arrow visible instead of only the one that
     * happened to end on the boundary.
     */
    @Test
    void clipsConnectorEndsToTheBoxEdges() {
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                resource(M + "d1", RDF_TYPE, OU + "Diagram"),
                literal(M + "d1", OU + "name", "Demo"),
                resource(M + "d1", OU + "containsView", M + "cvA"),
                resource(M + "d1", OU + "containsView", M + "cvB"),
                resource(M + "d1", OU + "containsView", M + "rv1"),

                // A sits directly above B, 140 units apart
                resource(M + "A", RDF_TYPE, OU + "Class"),
                literal(M + "A", OU + "name", "A"),
                resource(M + "cvA", RDF_TYPE, OU + "ClassView"),
                resource(M + "cvA", OU + "isViewOf", M + "A"),
                resource(M + "cvA", OU + "shape", M + "rA"),
                resource(M + "rA", OU + "topLeftPosition", M + "rA_tl"),
                literal(M + "rA_tl", OU + "xCoordinate", "100"),
                literal(M + "rA_tl", OU + "yCoordinate", "100"),
                literal(M + "rA", OU + "width", "200"),
                literal(M + "rA", OU + "height", "60"),

                resource(M + "B", RDF_TYPE, OU + "Class"),
                literal(M + "B", OU + "name", "B"),
                resource(M + "cvB", RDF_TYPE, OU + "ClassView"),
                resource(M + "cvB", OU + "isViewOf", M + "B"),
                resource(M + "cvB", OU + "shape", M + "rB"),
                resource(M + "rB", OU + "topLeftPosition", M + "rB_tl"),
                literal(M + "rB_tl", OU + "xCoordinate", "100"),
                literal(M + "rB_tl", OU + "yCoordinate", "300"),
                literal(M + "rB", OU + "width", "200"),
                literal(M + "rB", OU + "height", "60"),

                // the path's ends sit 30 units INSIDE each box, as the export states them
                resource(M + "rv1", RDF_TYPE, OU + "RelationView"),
                resource(M + "rv1", OU + "sourceView", M + "cvA"),
                resource(M + "rv1", OU + "targetView", M + "cvB"),
                resource(M + "rv1", OU + "shape", M + "p1"),
                resource(M + "p1", OU + "point", M + "p1_pt_0"),
                resource(M + "p1", OU + "point", M + "p1_pt_1"),
                literal(M + "p1_pt_0", OU + "xCoordinate", "200"),
                literal(M + "p1_pt_0", OU + "yCoordinate", "130"),
                literal(M + "p1_pt_1", OU + "xCoordinate", "200"),
                literal(M + "p1_pt_1", OU + "yCoordinate", "330")
        ), Map.of());

        final String json = diagrams.renderLayout(graph, "Demo");

        // (200,300) is B's top edge and (200,160) is A's bottom edge; translated by (-76,-76)
        assertThat(json).as("both ends are pulled out to the box edges")
                .contains("\"points\":[[124,224],[124,84]]");
    }

    /**
     * A class box standing directly between the two ends must be routed <em>around</em>.  The export's own
     * route ran straight through it, and a box is drawn on top of the connectors, so the line vanished
     * behind it — this is what routing afresh adds over reusing the stored bends.
     */
    @Test
    void routesAroundABoxThatStandsBetweenTheTwoEnds() {
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                resource(M + "d1", RDF_TYPE, OU + "Diagram"),
                literal(M + "d1", OU + "name", "Demo"),
                resource(M + "d1", OU + "containsView", M + "cvA"),
                resource(M + "d1", OU + "containsView", M + "cvB"),
                resource(M + "d1", OU + "containsView", M + "cvC"),
                resource(M + "d1", OU + "containsView", M + "rv1"),

                // A at (100,100) 200x60 and B at (100,500) 200x60, with C right between them
                resource(M + "A", RDF_TYPE, OU + "Class"),
                literal(M + "A", OU + "name", "A"),
                resource(M + "cvA", RDF_TYPE, OU + "ClassView"),
                resource(M + "cvA", OU + "isViewOf", M + "A"),
                resource(M + "cvA", OU + "shape", M + "rA"),
                resource(M + "rA", OU + "topLeftPosition", M + "rA_tl"),
                literal(M + "rA_tl", OU + "xCoordinate", "100"),
                literal(M + "rA_tl", OU + "yCoordinate", "100"),
                literal(M + "rA", OU + "width", "200"),
                literal(M + "rA", OU + "height", "60"),

                resource(M + "B", RDF_TYPE, OU + "Class"),
                literal(M + "B", OU + "name", "B"),
                resource(M + "cvB", RDF_TYPE, OU + "ClassView"),
                resource(M + "cvB", OU + "isViewOf", M + "B"),
                resource(M + "cvB", OU + "shape", M + "rB"),
                resource(M + "rB", OU + "topLeftPosition", M + "rB_tl"),
                literal(M + "rB_tl", OU + "xCoordinate", "100"),
                literal(M + "rB_tl", OU + "yCoordinate", "500"),
                literal(M + "rB", OU + "width", "200"),
                literal(M + "rB", OU + "height", "60"),

                resource(M + "C", RDF_TYPE, OU + "Class"),
                literal(M + "C", OU + "name", "C"),
                resource(M + "cvC", RDF_TYPE, OU + "ClassView"),
                resource(M + "cvC", OU + "isViewOf", M + "C"),
                resource(M + "cvC", OU + "shape", M + "rC"),
                resource(M + "rC", OU + "topLeftPosition", M + "rC_tl"),
                literal(M + "rC_tl", OU + "xCoordinate", "150"),
                literal(M + "rC_tl", OU + "yCoordinate", "280"),
                literal(M + "rC", OU + "width", "100"),
                literal(M + "rC", OU + "height", "100"),

                // no usable path, so the router picks the attachment points itself
                resource(M + "rv1", RDF_TYPE, OU + "RelationView"),
                resource(M + "rv1", OU + "sourceView", M + "cvA"),
                resource(M + "rv1", OU + "targetView", M + "cvB")
        ), Map.of());

        final String json = diagrams.renderLayout(graph, "Demo");

        // C spans x 150..250, so a route straight down the middle (x = 200) would run through it.  The
        // route instead goes round C's left edge, at x = 150.
        assertThat(json).as("the route goes around C instead of through it")
                .contains("\"points\":[[124,424],[74,424],[74,84],[124,84]]");
    }

    /**
     * Two sub-classes generalizing the same super-class merge: both arrive at the <b>middle</b> of the same
     * edge of the super-class, travelling along its inward normal, so their last segments are collinear and
     * read as one trunk — and only one of them draws the arrowhead.
     */
    @Test
    void mergesGeneralizationsIntoOneTrunkWithOneArrow() {
        final List<RdfTripleTO> triples = new ArrayList<>(List.of(
                resource(M + "d1", RDF_TYPE, OU + "Diagram"),
                literal(M + "d1", OU + "name", "Demo"),
                resource(M + "d1", OU + "containsView", M + "cvS"),
                resource(M + "d1", OU + "containsView", M + "cvC1"),
                resource(M + "d1", OU + "containsView", M + "cvC2"),
                resource(M + "d1", OU + "containsView", M + "gv1"),
                resource(M + "d1", OU + "containsView", M + "gv2")));

        // the super-class S, and two sub-classes below it
        triples.addAll(classView("S", "cvS", "rS", 100, 100, 200, 60));
        triples.addAll(classView("C1", "cvC1", "rC1", 100, 400, 200, 60));
        triples.addAll(classView("C2", "cvC2", "rC2", 400, 400, 200, 60));

        // both generalizations point at S (the export names the super-class as `sourceView`)
        triples.addAll(List.of(
                resource(M + "gv1", RDF_TYPE, OU + "GeneralizationView"),
                resource(M + "gv1", OU + "sourceView", M + "cvS"),
                resource(M + "gv1", OU + "targetView", M + "cvC1"),
                resource(M + "gv2", RDF_TYPE, OU + "GeneralizationView"),
                resource(M + "gv2", OU + "sourceView", M + "cvS"),
                resource(M + "gv2", OU + "targetView", M + "cvC2")));

        final String json = diagrams.renderLayout(new RdfGraphTO(triples, Map.of()), "Demo");

        // S is at (100,100) 200x60 and the drawing starts at the padding, so its bottom edge — the one
        // facing the sub-classes — runs at y=84 with its middle at x=124.
        assertThat(json.split("\\[124,84\\]\\]", -1).length - 1)
                .as("both connectors arrive at the middle of the super-class edge").isEqualTo(2);
        assertThat(json).contains("\"arrow\":true");
        assertThat(json).as("only one of them draws the arrowhead").contains("\"arrow\":false");
    }

    /**
     * Several associations leaving one box must not all start from the same point — they are spread along
     * the edge they share, so that they can be told apart.
     */
    @Test
    void spreadsConnectorsThatShareABoxEdge() {
        final List<RdfTripleTO> triples = new ArrayList<>(List.of(
                resource(M + "d1", RDF_TYPE, OU + "Diagram"),
                literal(M + "d1", OU + "name", "Demo"),
                resource(M + "d1", OU + "containsView", M + "cvA"),
                resource(M + "d1", OU + "containsView", M + "cvB"),
                resource(M + "d1", OU + "containsView", M + "cvC"),
                resource(M + "d1", OU + "containsView", M + "rv1"),
                resource(M + "d1", OU + "containsView", M + "rv2")));
        triples.addAll(classView("A", "cvA", "rA", 100, 100, 200, 60));
        triples.addAll(classView("B", "cvB", "rB", 500, 100, 200, 60));
        triples.addAll(classView("C", "cvC", "rC", 500, 400, 200, 60));
        // both associations leave A's RIGHT edge — each stored path starts at its middle
        triples.addAll(List.of(
                resource(M + "rv1", RDF_TYPE, OU + "RelationView"),
                resource(M + "rv1", OU + "sourceView", M + "cvB"),
                resource(M + "rv1", OU + "targetView", M + "cvA"),
                resource(M + "rv1", OU + "shape", M + "p1"),
                resource(M + "p1", OU + "point", M + "p1_0"),
                resource(M + "p1", OU + "point", M + "p1_1"),
                literal(M + "p1_0", OU + "xCoordinate", "300"),
                literal(M + "p1_0", OU + "yCoordinate", "130"),
                literal(M + "p1_1", OU + "xCoordinate", "500"),
                literal(M + "p1_1", OU + "yCoordinate", "130"),

                resource(M + "rv2", RDF_TYPE, OU + "RelationView"),
                resource(M + "rv2", OU + "sourceView", M + "cvC"),
                resource(M + "rv2", OU + "targetView", M + "cvA"),
                resource(M + "rv2", OU + "shape", M + "p2"),
                resource(M + "p2", OU + "point", M + "p2_0"),
                resource(M + "p2", OU + "point", M + "p2_1"),
                literal(M + "p2_0", OU + "xCoordinate", "300"),
                literal(M + "p2_0", OU + "yCoordinate", "130"),
                literal(M + "p2_1", OU + "xCoordinate", "500"),
                literal(M + "p2_1", OU + "yCoordinate", "430")));

        final String json = diagrams.renderLayout(new RdfGraphTO(triples, Map.of()), "Demo");

        // A's right edge runs from y=100 to y=160, so a third of the way is y=120 and two thirds is y=140 —
        // translated by (-76,-76).  Neither is the middle (y=130), where both would have started.
        assertThat(json).as("one association leaves A's right edge a third of the way down")
                .contains("\"points\":[[224,44]");
        assertThat(json).as("and the other two thirds of the way down")
                .contains("\"points\":[[224,64]");
        assertThat(json).as("they must not share the middle of the edge")
                .doesNotContain("\"points\":[[224,54]");
    }

}
