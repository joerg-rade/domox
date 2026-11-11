package domox.webapp.config;

import domox.dom.requirements.OntologyDiagrams;
import domox.dom.requirements.OntologyDiagram;
import org.apache.causeway.core.metamodel.context.MetaModelContext;
import org.apache.causeway.core.metamodel.object.ManagedObject;
import org.apache.causeway.viewer.wicket.ui.pages.WebPageBase;
import org.apache.causeway.viewer.wicket.ui.pages.obj.DomainObjectPage;
import org.apache.wicket.request.mapper.parameter.PageParameters;

/**
 * Landing page for the D3 graph's Ctrl-click drill-down: renders the <b>full Causeway object page</b>
 * for the {@link OntologyDiagram} view model of {@code ?term=…}.
 *
 * <p>It exists because that page's own URL cannot be built outside the framework: a view-model id is a
 * <em>signed</em> memento that only the Causeway Wicket viewer can mint (pasting a RestfulObjects oid
 * into {@code /wicket/object/…} fails with <i>"Digital verification failed"</i>).  So the client opens
 * this page instead, which lets the framework mint the id: it builds the view model, adapts it to a
 * {@link ManagedObject}, and hands it to {@link DomainObjectPage#forAdapter(ManagedObject)} — the very
 * page the viewer shows when the action is run from the menu, chrome and all.</p>
 *
 * <p>Mounted by {@link OntologyDiagramResourceInitializer} at {@code /ontology-drilldown}.  It holds no
 * state (it redirects immediately), so it stays serializable.</p>
 */
public class OntologyDrilldownPage extends WebPageBase {

    /** Query parameter carrying the term to search for. */
    static final String TERM_PARAM = "term";

    public OntologyDrilldownPage(final PageParameters parameters) {
        super(parameters);
        final MetaModelContext metaModelContext = MetaModelContext.instanceElseFail();
        final String term = parameters.get(TERM_PARAM).toString("");
        final OntologyDiagram viewModel = metaModelContext.getServiceRegistry()
                .lookupServiceElseFail(OntologyDiagrams.class)
                .ontologyDiagram(term);
        final ManagedObject adapter = metaModelContext.getObjectManager().adapt(viewModel);
        setResponsePage(DomainObjectPage.forAdapter(adapter));
    }
}
