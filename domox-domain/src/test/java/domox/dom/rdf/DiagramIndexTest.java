package domox.dom.rdf;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import domox.dom.rdf.geometry.Box;
import domox.dom.rdf.geometry.Pt;
import domox.port.RdfTripleTO;

import static domox.dom.rdf.UmlLayoutFixture.M;
import static domox.dom.rdf.UmlLayoutFixture.OU;
import static domox.dom.rdf.UmlLayoutFixture.RDF_TYPE;
import static domox.dom.rdf.UmlLayoutFixture.graph;
import static domox.port.RdfTripleTO.literal;
import static domox.port.RdfTripleTO.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-level guardrail for {@link DiagramIndex} — the layout an OntoUML export stores, read into lookups.
 * <p>
 * The end-to-end {@code UmlLayoutDiagramsTest} exercises the happy path of these lookups through a
 * finished payload; this pins the lookups themselves, including the defensive paths the bundled export
 * never reaches: a malformed point IRI, a coordinate that is not a number, a relation with no name, and
 * the diagram chooser's default when a diagram carries no views at all.
 */
@DisplayName("DiagramIndex — the export's layout, read into lookups")
class DiagramIndexTest {

    /** The two boxes the connector paths are reconstructed between. */
    private static final Box START = new Box(0, 0, 100, 60);
    private static final Box END = new Box(300, 0, 100, 60);

    private static DiagramIndex indexOf(final List<RdfTripleTO> triples) {
        return DiagramIndex.of(graph(triples));
    }

    // -- the lookups -----------------------------------------------------------

    @Test
    @DisplayName("the index exposes the type lookup the layout selects boxes by")
    void theIndexExposesTheTypeLookup() {
        assertThat(indexOf(List.of(resource(M + "cvA", RDF_TYPE, OU + "ClassView"))).type)
                .containsEntry(M + "cvA", OU + "ClassView");
    }

    @Test
    @DisplayName("a diagram's views are collected in the order the export lists them")
    void aDiagramsViewsAreCollectedInOrder() {
        final DiagramIndex index = indexOf(List.of(
                resource(M + "d1", OU + "containsView", M + "cvA"),
                resource(M + "d1", OU + "containsView", M + "cvB")));

        assertThat(index.containsView.get(M + "d1")).containsExactly(M + "cvA", M + "cvB");
    }

    @Test
    @DisplayName("a connector's two views are indexed")
    void aConnectorsTwoViewsAreIndexed() {
        final DiagramIndex index = indexOf(List.of(
                resource(M + "gv1", OU + "sourceView", M + "cvA"),
                resource(M + "gv1", OU + "targetView", M + "cvB")));

        assertThat(index.sourceView).containsEntry(M + "gv1", M + "cvA");
        assertThat(index.targetView).containsEntry(M + "gv1", M + "cvB");
    }

    @Test
    @DisplayName("the first value stated for a subject wins")
    void theFirstValueStatedWins() {
        final DiagramIndex index = indexOf(List.of(
                literal(M + "d1", OU + "name", "First"),
                literal(M + "d1", OU + "name", "Second")));

        assertThat(index.displayNameOf(M + "d1")).isEqualTo("First");
    }

    // -- which resources are diagrams ------------------------------------------

    @Test
    @DisplayName("a diagram typed as one is a diagram")
    void aTypedDiagramIsADiagram() {
        assertThat(indexOf(List.of(resource(M + "d1", RDF_TYPE, OU + "Diagram"))).diagrams())
                .containsExactly(M + "d1");
    }

    @Test
    @DisplayName("a view owner counts as a diagram even without the type")
    void aViewOwnerIsADiagram() {
        assertThat(indexOf(List.of(resource(M + "d1", OU + "containsView", M + "cvA"))).diagrams())
                .containsExactly(M + "d1");
    }

    @Test
    @DisplayName("a class that owns no view is not a diagram")
    void aClassWithNoViewsIsNotADiagram() {
        assertThat(indexOf(List.of(resource(M + "A", RDF_TYPE, OU + "Class"))).diagrams()).isEmpty();
    }

    // -- names -----------------------------------------------------------------

    @Test
    @DisplayName("a view is named by its own name, else by its IRI's local name")
    void aViewIsNamedByNameOrLocalName() {
        final DiagramIndex index = indexOf(List.of(
                literal(M + "d1", OU + "name", "Demo"),
                resource(M + "d2", RDF_TYPE, OU + "Diagram")));

        assertThat(index.displayNameOf(M + "d1")).isEqualTo("Demo");
        assertThat(index.displayNameOf(M + "d2")).as("no name, so the IRI's local name").isEqualTo("d2");
        assertThat(index.displayNameOf(M + "missing")).isEqualTo("missing");
    }

    @Test
    @DisplayName("a blank name falls back to the IRI's local name")
    void aBlankNameFallsBackToTheLocalName() {
        assertThat(indexOf(List.of(literal(M + "d1", OU + "name", "   "))).displayNameOf(M + "d1"))
                .isEqualTo("d1");
    }

    @Test
    @DisplayName("a view's label is the name of the element it draws")
    void aViewsLabelIsTheElementName() {
        final DiagramIndex index = indexOf(List.of(
                literal(M + "A", OU + "name", "Pet"),
                resource(M + "cvA", OU + "isViewOf", M + "A")));

        assertThat(index.elementName(M + "cvA")).as("the class name, not the view's id").isEqualTo("Pet");
    }

    @Test
    @DisplayName("a view that draws nothing falls back to its own name")
    void aViewThatDrawsNothingFallsBackToItsOwnName() {
        assertThat(indexOf(List.of(literal(M + "cvA", OU + "name", "Lonely"))).elementName(M + "cvA"))
                .isEqualTo("Lonely");
    }

    @Test
    @DisplayName("a relation's label is its element's name")
    void aRelationsLabelIsItsElementName() {
        final DiagramIndex index = indexOf(List.of(
                literal(M + "rel1", OU + "name", "owns"),
                resource(M + "rv1", OU + "isViewOf", M + "rel1")));

        assertThat(index.relationLabelOf(M + "rv1")).isEqualTo("owns");
    }

    @Test
    @DisplayName("a relation view with no element is blank")
    void aRelationViewWithNoElementIsBlank() {
        assertThat(indexOf(List.of(literal(M + "rv1", OU + "name", "ignored")))
                .relationLabelOf(M + "rv1")).isEmpty();
    }

    @Test
    @DisplayName("a relation whose element carries no name is blank")
    void aRelationWithAnUnnamedElementIsBlank() {
        assertThat(indexOf(List.of(resource(M + "rv1", OU + "isViewOf", M + "rel1")))
                .relationLabelOf(M + "rv1")).isEmpty();
    }

    // -- resolveDiagram --------------------------------------------------------

    @Test
    @DisplayName("a named diagram is resolved case-insensitively, ignoring surrounding space")
    void aNamedDiagramIsResolvedCaseInsensitively() {
        final DiagramIndex index = indexOf(List.of(
                resource(M + "d1", RDF_TYPE, OU + "Diagram"), literal(M + "d1", OU + "name", "Demo"),
                resource(M + "d2", RDF_TYPE, OU + "Diagram"), literal(M + "d2", OU + "name", "Other")));

        assertThat(index.resolveDiagram("Demo")).isEqualTo(M + "d1");
        assertThat(index.resolveDiagram("  DEMO ")).isEqualTo(M + "d1");
        assertThat(index.resolveDiagram("other")).isEqualTo(M + "d2");
    }

    @Test
    @DisplayName("an unknown diagram name is rejected, listing the ones there are")
    void anUnknownDiagramNameIsRejected() {
        final DiagramIndex index = indexOf(List.of(
                resource(M + "d1", RDF_TYPE, OU + "Diagram"), literal(M + "d1", OU + "name", "Demo")));

        assertThatThrownBy(() -> index.resolveDiagram("Nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No UML diagram named 'Nope'")
                .hasMessageContaining("[Demo]");
    }

    @Test
    @DisplayName("a graph with no diagram at all is rejected")
    void aGraphWithNoDiagramIsRejected() {
        final DiagramIndex index = indexOf(List.of(resource(M + "A", RDF_TYPE, OU + "Class")));

        assertThatThrownBy(() -> index.resolveDiagram(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No UML diagram in the graph");
    }

    @Test
    @DisplayName("a blank name picks the diagram with the most views")
    void aBlankNamePicksTheRichestDiagram() {
        final DiagramIndex index = indexOf(List.of(
                resource(M + "d1", RDF_TYPE, OU + "Diagram"),
                resource(M + "d1", OU + "containsView", M + "cvA"),
                resource(M + "d2", RDF_TYPE, OU + "Diagram"),
                resource(M + "d2", OU + "containsView", M + "cvA"),
                resource(M + "d2", OU + "containsView", M + "cvB")));

        assertThat(index.resolveDiagram(null)).isEqualTo(M + "d2");
        assertThat(index.resolveDiagram("  ")).isEqualTo(M + "d2");
    }

    @Test
    @DisplayName("a diagram with no views at all still resolves when it is the only one")
    void aDiagramWithNoViewsResolves() {
        final DiagramIndex index = indexOf(List.of(resource(M + "d1", RDF_TYPE, OU + "Diagram")));

        assertThat(index.resolveDiagram(null)).isEqualTo(M + "d1");
    }

    // -- boxOf -----------------------------------------------------------------

    @Test
    @DisplayName("a class view's box comes back with the export's own rectangle")
    void aClassViewsBoxComesBack() {
        final DiagramIndex index = indexOf(List.of(
                resource(M + "cvA", OU + "shape", M + "rA"),
                resource(M + "rA", OU + "topLeftPosition", M + "rA_tl"),
                literal(M + "rA_tl", OU + "xCoordinate", "100"),
                literal(M + "rA_tl", OU + "yCoordinate", "50"),
                literal(M + "rA", OU + "width", "200"),
                literal(M + "rA", OU + "height", "60")));

        assertThat(index.boxOf(M + "cvA")).isEqualTo(new Box(100, 50, 200, 60));
    }

    @Test
    @DisplayName("a view with no rectangle has no box")
    void aViewWithNoRectangleHasNoBox() {
        assertThat(indexOf(List.of(resource(M + "cvA", OU + "isViewOf", M + "A"))).boxOf(M + "cvA")).isNull();
    }

    @Test
    @DisplayName("a rectangle with no corner has no box")
    void aRectangleWithNoCornerHasNoBox() {
        assertThat(indexOf(List.of(resource(M + "cvA", OU + "shape", M + "rA"))).boxOf(M + "cvA")).isNull();
    }

    @Test
    @DisplayName("a corner with no coordinates has no box")
    void aCornerWithNoCoordinatesHasNoBox() {
        final DiagramIndex index = indexOf(List.of(
                resource(M + "cvA", OU + "shape", M + "rA"),
                resource(M + "rA", OU + "topLeftPosition", M + "rA_tl"),
                literal(M + "rA_tl", OU + "xCoordinate", "10")));

        assertThat(index.boxOf(M + "cvA")).as("the y coordinate is missing").isNull();
    }

    @Test
    @DisplayName("a rectangle with no size falls back to the default width and height")
    void aRectangleWithNoSizeUsesTheDefaults() {
        final DiagramIndex index = indexOf(List.of(
                resource(M + "cvA", OU + "shape", M + "rA"),
                resource(M + "rA", OU + "topLeftPosition", M + "rA_tl"),
                literal(M + "rA_tl", OU + "xCoordinate", "10"),
                literal(M + "rA_tl", OU + "yCoordinate", "20")));

        assertThat(index.boxOf(M + "cvA")).isEqualTo(new Box(10, 20, 140, 60));
    }

    @Test
    @DisplayName("a rectangle with only a width takes the default height")
    void aRectangleWithOnlyAWidthTakesTheDefaultHeight() {
        final DiagramIndex index = indexOf(List.of(
                resource(M + "cvA", OU + "shape", M + "rA"),
                resource(M + "rA", OU + "topLeftPosition", M + "rA_tl"),
                literal(M + "rA_tl", OU + "xCoordinate", "10"),
                literal(M + "rA_tl", OU + "yCoordinate", "20"),
                literal(M + "rA", OU + "width", "300")));

        assertThat(index.boxOf(M + "cvA")).isEqualTo(new Box(10, 20, 300, 60));
    }

    @Test
    @DisplayName("a coordinate that is not a number leaves the view without a box")
    void aNonNumericCoordinateLeavesNoBox() {
        final DiagramIndex index = indexOf(List.of(
                resource(M + "cvA", OU + "shape", M + "rA"),
                resource(M + "rA", OU + "topLeftPosition", M + "rA_tl"),
                literal(M + "rA_tl", OU + "xCoordinate", "not a number"),
                literal(M + "rA_tl", OU + "yCoordinate", "20")));

        assertThat(index.boxOf(M + "cvA")).isNull();
    }

    @Test
    @DisplayName("a blank coordinate leaves the view without a box")
    void aBlankCoordinateLeavesNoBox() {
        final DiagramIndex index = indexOf(List.of(
                resource(M + "cvA", OU + "shape", M + "rA"),
                resource(M + "rA", OU + "topLeftPosition", M + "rA_tl"),
                literal(M + "rA_tl", OU + "xCoordinate", "  "),
                literal(M + "rA_tl", OU + "yCoordinate", "20")));

        assertThat(index.boxOf(M + "cvA")).isNull();
    }

    @Test
    @DisplayName("a box with a fractional size keeps it")
    void aBoxWithAFractionalSizeKeepsIt() {
        final DiagramIndex index = indexOf(List.of(
                resource(M + "cvA", OU + "shape", M + "rA"),
                resource(M + "rA", OU + "topLeftPosition", M + "rA_tl"),
                literal(M + "rA_tl", OU + "xCoordinate", "10.5"),
                literal(M + "rA_tl", OU + "yCoordinate", "20.25"),
                literal(M + "rA", OU + "width", "140.75"),
                literal(M + "rA", OU + "height", "60.5")));

        assertThat(index.boxOf(M + "cvA")).isEqualTo(new Box(10.5, 20.25, 140.75, 60.5));
    }

    // -- pointsOf --------------------------------------------------------------

    /** A connector whose path is the given points, numbered from zero in the stated order. */
    private static List<RdfTripleTO> path(final String... iriAndCoordinates) {
        final List<RdfTripleTO> triples = new ArrayList<>();
        triples.add(resource(M + "gv1", OU + "shape", M + "p1"));
        for (int i = 0; i < iriAndCoordinates.length; i += 3) {
            triples.add(resource(M + "p1", OU + "point", iriAndCoordinates[i]));
            triples.add(literal(iriAndCoordinates[i], OU + "xCoordinate", iriAndCoordinates[i + 1]));
            triples.add(literal(iriAndCoordinates[i], OU + "yCoordinate", iriAndCoordinates[i + 2]));
        }
        return triples;
    }

    @Test
    @DisplayName("a connector with no stored path has no points")
    void aConnectorWithNoPathHasNoPoints() {
        assertThat(indexOf(List.of(resource(M + "gv1", OU + "isViewOf", M + "rel1")))
                .pointsOf(M + "gv1", START, END)).isEmpty();
    }

    @Test
    @DisplayName("the path's points come back in the order their IRIs state, not the triple order")
    void thePathPointsComeBackInTheirStatedOrder() {
        // the export states point _1 before point _0
        final DiagramIndex index = indexOf(path(
                M + "p1_pt_1", "400", "30",
                M + "p1_pt_0", "100", "30"));

        assertThat(index.pointsOf(M + "gv1", START, END))
                .containsExactly(new Pt(100, 30), new Pt(300, 30));
    }

    @Test
    @DisplayName("a stored diagonal comes back orthogonalised and clipped to the box edges")
    void aStoredDiagonalComesBackOrthogonalisedAndClipped() {
        final DiagramIndex index = indexOf(path(
                M + "p1_pt_0", "50", "50",
                M + "p1_pt_1", "350", "10"));

        final List<Pt> points = index.pointsOf(M + "gv1", START, END);

        assertThat(points).isNotEmpty();
        for (int i = 1; i < points.size(); i++) {
            final Pt a = points.get(i - 1);
            final Pt b = points.get(i);
            assertThat(Math.abs(a.x() - b.x()) <= 1e-6 || Math.abs(a.y() - b.y()) <= 1e-6)
                    .as("segment %s -> %s must be axis-aligned", a, b).isTrue();
        }
    }

    @Test
    @DisplayName("a path built from (0,0) sentinels falls back to no points")
    void aSentinelPathFallsBackToNoPoints() {
        final DiagramIndex index = indexOf(path(
                M + "p1_pt_0", "0", "0",
                M + "p1_pt_1", "0", "0"));

        assertThat(index.pointsOf(M + "gv1", START, END)).isEmpty();
    }

    @Test
    @DisplayName("a point IRI with no index at all sorts last")
    void aPointWithNoIndexSortsLast() {
        final DiagramIndex index = indexOf(path(
                M + "noIndex", "400", "30",
                M + "p1_pt_0", "100", "30"));

        assertThat(index.pointsOf(M + "gv1", START, END))
                .containsExactly(new Pt(100, 30), new Pt(300, 30));
    }

    @Test
    @DisplayName("a point IRI ending at its own underscore sorts last too")
    void aPointIriEndingAtItsUnderscoreSortsLast() {
        final DiagramIndex index = indexOf(path(
                M + "p1_", "400", "30",
                M + "p1_pt_0", "100", "30"));

        assertThat(index.pointsOf(M + "gv1", START, END))
                .containsExactly(new Pt(100, 30), new Pt(300, 30));
    }

    @Test
    @DisplayName("a point IRI whose index is not a number sorts last")
    void aPointWithANonNumericIndexSortsLast() {
        final DiagramIndex index = indexOf(path(
                M + "p1_pt_abc", "400", "30",
                M + "p1_pt_0", "100", "30"));

        assertThat(index.pointsOf(M + "gv1", START, END))
                .containsExactly(new Pt(100, 30), new Pt(300, 30));
    }

    @Test
    @DisplayName("a point with no usable coordinate is skipped")
    void aPointWithNoUsableCoordinateIsSkipped() {
        final DiagramIndex index = indexOf(path(
                M + "p1_pt_0", "100", "30",
                M + "p1_pt_1", "junk", "30"));

        assertThat(index.pointsOf(M + "gv1", START, END))
                .as("only one usable point is left, so the connector falls back").isEmpty();
    }

    @Test
    @DisplayName("a bend far from both boxes is dropped, leaving a straight line")
    void aBendFarFromBothBoxesIsDropped() {
        final DiagramIndex index = indexOf(path(
                M + "p1_pt_0", "100", "30",
                M + "p1_pt_1", "1000", "1000",
                M + "p1_pt_2", "300", "30"));

        assertThat(index.pointsOf(M + "gv1", START, END))
                .as("the stray bend is gone").containsExactly(new Pt(100, 30), new Pt(300, 30));
    }

    @Test
    @DisplayName("a path whose end sits far from both boxes falls back to no points")
    void aPathEndingFarFromBothBoxesFallsBack() {
        final DiagramIndex index = indexOf(path(
                M + "p1_pt_0", "100", "30",
                M + "p1_pt_1", "150", "200"));

        assertThat(index.pointsOf(M + "gv1", START, END)).isEmpty();
    }
}
