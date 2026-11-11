package domox.webapp.integtests;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import org.apache.causeway.applib.annotation.Where;
import org.apache.causeway.applib.services.bookmark.Bookmark;
import org.apache.causeway.core.metamodel.consent.InteractionInitiatedBy;
import org.apache.causeway.core.metamodel.object.ManagedObject;
import org.apache.causeway.core.metamodel.objectmanager.ObjectManager;
import org.apache.causeway.core.metamodel.spec.feature.ObjectMember;
import org.apache.causeway.extensions.d3.metamodel.facet.D3GraphFacet;

import domox.dom.requirements.OntologyDiagrams;
import domox.dom.requirements.OntologyDiagram;

/**
 * Guards the {@link OntologyDiagram} view model returned by
 * {@link OntologyDiagrams#searchOntologyAsD3Graph(String)} — the object the Wicket viewer draws a D3 canvas
 * for.
 *
 * <p>An action returning a bare {@code String} is displayed as text ({@code @D3Graph} is a
 * property facet), so the action must hand back a view model whose {@code diagram} property already
 * carries the rendered graph.</p>
 */
@Transactional
class OntologyDiagramIntegTest extends ApplicationIntegTestAbstract {

    @Inject private OntologyDiagrams ontologyDiagrams;
    @Inject private ObjectManager objectManager;

    /**
     * The bundled fixture is a UML/OntoUML export: its elements are named by {@code ontouml:name}
     * and carry no {@code rdfs:label}, so a name is the only handle a search has on them.
     */
    private static final String SEARCH_TERM = "Commitment";
    private static final String SEARCH_TERM_NAME = "\"name\":\"Commitment\"";

    @Test
    void searchAction_returnsAViewModelCarryingTheRenderedGraph() {
        final OntologyDiagram viewModel = wrap(ontologyDiagrams).searchOntologyAsD3Graph(SEARCH_TERM);

        assertThat(viewModel.getSearchTerm()).isEqualTo(SEARCH_TERM);
        assertThat(viewModel.getDiagram()).contains(SEARCH_TERM_NAME);
    }

    /**
     * Adapting through the framework populates {@code @Inject} fields, exactly as the viewer does
     * with the object returned by an action.  This proves the lazy fallback works too, so an edited
     * search term (or a re-opened bookmark) still re-derives the graph.
     */
    @Test
    void frameworkInjectsTheService_soTheGraphIsReDerivedFromTheSearchTerm() {
        final OntologyDiagram viewModel =
                (OntologyDiagram) objectManager.adapt(new OntologyDiagram()).getPojo();

        // no cached graph on a freshly adapted instance, so getDiagram() must derive it
        viewModel.setSearchTerm(SEARCH_TERM);

        assertThat(viewModel.getDiagram()).contains(SEARCH_TERM_NAME);
    }

    /**
     * The view model must be bookmarkable — i.e. the viewer can round-trip it through a memento —
     * otherwise it cannot be rendered as a page.  The search term is the round-tripped state; the
     * graph is re-derived on the restored instance.
     */
    @Test
    void viewModelRoundTripsThroughABookmark() {
        final OntologyDiagram original = new OntologyDiagram();
        original.setSearchTerm(SEARCH_TERM);

        final Bookmark bookmark = objectManager.bookmark(objectManager.adapt(original));
        final OntologyDiagram restored =
                (OntologyDiagram) objectManager.debookmark(bookmark).getPojo();

        assertThat(restored.getSearchTerm()).isEqualTo(SEARCH_TERM);
        assertThat(restored.getDiagram()).contains(SEARCH_TERM_NAME);
    }

    /**
     * The crux of the rendering question: the Wicket D3 panel is selected by a
     * {@link D3GraphFacet} on the <em>property</em> (see the extension's
     * {@code D3PanelComponentFactory}, which registers for
     * {@code UiComponentType.ATTRIBUTE_NAME_AND_VALUE}).  So it is the property — not the action —
     * that makes a canvas appear, and the facet must really be applied to the view model's
     * {@code diagram} member.
     */
    @Test
    void diagramPropertyCarriesTheD3GraphFacet() {
        final ObjectMember diagram = objectManager.adapt(new OntologyDiagram())
                .objSpec()
                .lookupMember("diagram")
                .orElseThrow();

        assertThat(diagram.lookupFacet(D3GraphFacet.class)).isPresent();
    }

    /**
     * The search term is already surfaced in the object title ("Ontology: …"), so it must not be
     * rendered as a "Search Term" property.  It stays a metamodel member (hidden, not
     * {@code @Programmatic}) so the memento keeps round-tripping — see
     * {@link #viewModelRoundTripsThroughABookmark()}.
     */
    @Test
    void searchTermIsHiddenFromTheUi() {
        final ManagedObject mo = objectManager.adapt(new OntologyDiagram());
        final ObjectMember searchTerm = mo.objSpec().lookupMember("searchTerm").orElseThrow();

        assertThat(searchTerm.isVisible(mo, InteractionInitiatedBy.USER, Where.EVERYWHERE).isAllowed())
                .as("the redundant 'Search Term' property must not be shown")
                .isFalse();
    }
}
