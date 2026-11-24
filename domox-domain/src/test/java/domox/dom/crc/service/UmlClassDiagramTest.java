package domox.dom.crc.service;

import domox.dom.crc.model.DomainModel;
import generate.ClassDiagram;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The view model the diagram actions return: which diagram it is — its title, and the label that carries
 * the sub-domain — and the well-formed empty payload it draws before it has been given a graph.
 *
 * <p>The re-derivation fallback ({@code ensureDerived}, which needs Causeway's service injection) and the
 * layout itself are not exercised here: a unit test cannot inject {@link DomainModels} into the view
 * model, and laying a graph out is {@code UmlDiagramLayoutTest}'s subject.</p>
 */
class UmlClassDiagramTest {

    /** A graph with no content — the smallest thing a view model can be handed. */
    private static ClassDiagram.Graph emptyGraph() {
        return new ClassDiagram.Graph("Pet Shop — whole model", List.of(), List.of());
    }

    @Test
    void titleSaysWhichDiagramOfWhichModelItIs() {
        // given
        final DomainModel model = new DomainModel();

        // when / then
        assertThat(UmlClassDiagram.of(model, UmlClassDiagram.KIND_MODEL, null).title())
                .as("the whole model").contains("whole model");
        assertThat(UmlClassDiagram.of(model, UmlClassDiagram.KIND_SUB_DOMAIN, "Inventory").title())
                .as("one sub-domain, by name").contains("sub-domain Inventory");
        assertThat(UmlClassDiagram.of(model, UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW, null).title())
                .as("the overview").contains("sub-domain overview");
    }

    @Test
    void titleFallsBackWhenThereIsNoModel() {
        assertThat(UmlClassDiagram.of(null, UmlClassDiagram.KIND_MODEL, null).title())
                .isEqualTo("UML class diagram: Domain model — whole model");
    }

    /**
     * The label is where the sub-domain is shown, because a Causeway <em>tab</em> label cannot vary per
     * diagram: the tab name comes from the layout XML, and one layout serves every instance of the view
     * model.  So this is the assertion that keeps a reader able to tell one diagram from another.
     */
    @Test
    void namedD3GraphCarriesTheSubDomainTheDiagramShows() {
        assertThat(UmlClassDiagram.of(null, UmlClassDiagram.KIND_MODEL, null).namedD3Graph())
                .as("the whole model").isEqualTo("Whole model");
        assertThat(UmlClassDiagram.of(null, UmlClassDiagram.KIND_SUB_DOMAIN, "Inventory").namedD3Graph())
                .as("one sub-domain, by name").isEqualTo("Sub-domain: Inventory");
        assertThat(UmlClassDiagram.of(null, UmlClassDiagram.KIND_SUB_DOMAIN, null).namedD3Graph())
                .as("an unnamed sub-domain still says which view this is").isEqualTo("Sub-domain: (unnamed)");
        assertThat(UmlClassDiagram.of(null, UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW, null).namedD3Graph())
                .as("the overview").isEqualTo("Sub-domain overview");
        assertThat(UmlClassDiagram.of(null, null, null).namedD3Graph())
                .as("an unset kind reads as the whole model").isEqualTo("Whole model");
    }

    @Test
    void aFreshlyMementoedViewModelDrawsAnEmptyCanvasRatherThanFailing() {
        // given — what a page reload reconstructs: the memento keys only, no graph
        final UmlClassDiagram diagram = UmlClassDiagram.of(null, null, null);

        // when / then — a well-formed but empty payload, so the panel draws an empty canvas
        assertThat(diagram.getD3Graph())
                .contains("\"nodes\":[]")
                .contains("\"links\":[]");
    }

    @Test
    void changingTheKindInvalidatesTheCachedGraph() {
        // given
        final UmlClassDiagram diagram = UmlClassDiagram.of(null, UmlClassDiagram.KIND_MODEL, null)
                .withGraph(emptyGraph());

        // when
        diagram.setKind(UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW);

        // then — the cached graph must not outlive the key it was built for (no service is injected here,
        // so the re-derivation cannot run and the payload falls back to the empty canvas)
        assertThat(diagram.getD3Graph()).contains("\"nodes\":[]");
    }
}
