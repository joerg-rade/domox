package domox.dom.rdf;

import java.util.List;
import java.util.Map;

import domox.port.RdfGraphTO;
import domox.port.RdfTripleTO;

import static domox.port.RdfTripleTO.literal;
import static domox.port.RdfTripleTO.resource;

/**
 * Shared OntoUML fixture for the UML-layout tests: the vocabulary the export is written in, and the
 * triple builders that spell a view's geometry out.
 * <p>
 * Hoisted out of {@code UmlLayoutDiagramsTest} when the per-collaborator tests were added — several test
 * classes need the same namespace and the same "one class view + its rectangle" triple run, and a private
 * copy in each would drift.  It is deliberately <em>not</em> a {@code *Test} class: it holds no tests, so
 * pitest does not pick it up as a target.
 */
final class UmlLayoutFixture {

    /** The OntoUML vocabulary the export is written in. */
    static final String OU = "https://w3id.org/ontouml#";

    /** The model's own namespace — the subjects the export mints. */
    static final String M = "http://example.org/model#";

    static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

    private UmlLayoutFixture() {
    }

    /** The triples for one class view: the class, its view, and the view's rectangle. */
    static List<RdfTripleTO> classView(final String name, final String view, final String rect,
                                       final double x, final double y, final double w, final double h) {
        return List.of(
                resource(M + name, RDF_TYPE, OU + "Class"),
                literal(M + name, OU + "name", name),
                resource(M + view, RDF_TYPE, OU + "ClassView"),
                resource(M + view, OU + "isViewOf", M + name),
                resource(M + view, OU + "shape", M + rect),
                resource(M + rect, OU + "topLeftPosition", M + rect + "_tl"),
                literal(M + rect + "_tl", OU + "xCoordinate", String.valueOf((long) x)),
                literal(M + rect + "_tl", OU + "yCoordinate", String.valueOf((long) y)),
                literal(M + rect, OU + "width", String.valueOf((long) w)),
                literal(M + rect, OU + "height", String.valueOf((long) h)));
    }

    /** A graph over the given triples, with no prefix map — the tests spell every IRI out in full. */
    static RdfGraphTO graph(final List<RdfTripleTO> triples) {
        return new RdfGraphTO(triples, Map.of());
    }

    /**
     * A small export: one diagram, class A at (100,50) 200x60, class B at (500,300) 160x60, and a
     * generalization A → B whose path points are deliberately listed <em>out of order</em>.
     */
    static RdfGraphTO sampleGraph() {
        return new RdfGraphTO(List.of(
                resource(M + "d1", RDF_TYPE, OU + "Diagram"),
                literal(M + "d1", OU + "name", "Demo"),
                resource(M + "d1", OU + "containsView", M + "cvA"),
                resource(M + "d1", OU + "containsView", M + "cvB"),
                resource(M + "d1", OU + "containsView", M + "gv1"),

                resource(M + "A", RDF_TYPE, OU + "Class"),
                literal(M + "A", OU + "name", "A"),
                resource(M + "cvA", RDF_TYPE, OU + "ClassView"),
                resource(M + "cvA", OU + "isViewOf", M + "A"),
                resource(M + "cvA", OU + "shape", M + "rA"),
                resource(M + "rA", OU + "topLeftPosition", M + "rA_tl"),
                literal(M + "rA_tl", OU + "xCoordinate", "100"),
                literal(M + "rA_tl", OU + "yCoordinate", "50"),
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
                literal(M + "rB", OU + "width", "160"),
                literal(M + "rB", OU + "height", "60"),

                // point _1 is stated before point _0 — the reconstruction must restore the order
                resource(M + "gv1", RDF_TYPE, OU + "GeneralizationView"),
                resource(M + "gv1", OU + "sourceView", M + "cvA"),
                resource(M + "gv1", OU + "targetView", M + "cvB"),
                resource(M + "gv1", OU + "shape", M + "p1"),
                resource(M + "p1", OU + "point", M + "p1_pt_1"),
                resource(M + "p1", OU + "point", M + "p1_pt_0"),
                // both ends sit ON a box, as the export states them (B's left edge, A's right edge)
                literal(M + "p1_pt_1", OU + "xCoordinate", "500"),
                literal(M + "p1_pt_1", OU + "yCoordinate", "330"),
                literal(M + "p1_pt_0", OU + "xCoordinate", "300"),
                literal(M + "p1_pt_0", OU + "yCoordinate", "80")
        ), Map.of());
    }
}
