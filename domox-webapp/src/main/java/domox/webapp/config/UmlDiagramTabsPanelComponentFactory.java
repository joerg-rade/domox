package domox.webapp.config;

import domox.dom.crc.service.UmlDiagramSet;
import jakarta.inject.Inject;
import org.apache.causeway.viewer.commons.model.components.UiComponentType;
import org.apache.causeway.viewer.wicket.model.models.UiObjectWkt;
import org.apache.causeway.viewer.wicket.ui.components.object.ObjectComponentFactoryAbstract;
import org.apache.wicket.Component;
import org.apache.wicket.model.IModel;

import java.io.Serial;
import java.util.List;

/**
 * Renders a {@link UmlDiagramSet} — the view model behind {@code DomainModels#showUmlDiagrams} — as
 * {@link UmlDiagramTabsPanel}: one tab per diagram, labelled with the sub-domain name.
 *
 * <p><b>Why a component factory at all.</b>  The tab labels are the sub-domains, which are data, and a
 * Causeway layout tab name comes from the layout XML — one layout per object type, so it cannot vary per
 * instance.  A viewer component is the supported way out: this factory applies for the one view-model
 * type and replaces its page content, exactly as the D3 extension's own
 * {@code D3ObjectPanelComponentFactory} replaces the page of a {@code @D3Graph}-annotated type.</p>
 *
 * <p><b>Exclusively</b>, so the object's layout XML is not consulted and its grid is not built: the tabs
 * are the page.  That layout XML ({@code UmlDiagramSet.layout.xml}) still exists as the fallback for a
 * viewer that does not use this factory — it shows the whole-model diagram.</p>
 */
// fully qualified: org.apache.wicket.Component is imported below, and this class returns one
@org.springframework.stereotype.Component
public class UmlDiagramTabsPanelComponentFactory extends ObjectComponentFactoryAbstract {

    @Inject
    public UmlDiagramTabsPanelComponentFactory() {
        super(UiComponentType.DOMAIN_OBJECT, UmlDiagramTabsPanel.class);
    }

    /**
     * Whether this factory takes over the page for an object of {@code domainClass}.
     *
     * <p>The rule as a pure function, so {@code UmlDiagramTabsPanelComponentFactoryTest} can pin it
     * without a Causeway session — {@code ManagedObject} is a sealed interface, so the object model
     * cannot be mocked (the same reason {@link UmlDiagramTabsPanel#tabsFor} is package-private).</p>
     */
    public static boolean takesOver(final Class<?> domainClass) {
        return domainClass == UmlDiagramSet.class;
    }

    @Override
    public ApplicationAdvice doAppliesTo(final UiObjectWkt objectModel) {
        return ApplicationAdvice.appliesExclusivelyIf(
                takesOver(objectModel.getManagedObject().getCorrespondingClass()));
    }

    @Override
    public Component createComponent(final String id, final IModel<?> model) {
        return new UmlDiagramTabsPanel(id, sourceOf((UiObjectWkt) model));
    }

    /**
     * The panel's data, read from the object model on demand.
     *
     * <p>Deliberately not the view model itself: a Wicket component is serialized into the session, and
     * the view model holds the model and its entities.  Reading the current pojo when a tab is shown also
     * means a page reload (which rebuilds the view model from its memento) draws the same tabs.</p>
     */
    private static UmlDiagramTabsPanel.Source sourceOf(final UiObjectWkt objectModel) {
        return new UmlDiagramTabsPanel.Source() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            public List<String> tabNames() {
                return diagrams().tabNames();
            }

            @Override
            public String payloadOf(final String tabName) {
                return diagrams().payloadOf(tabName);
            }

            private UmlDiagramSet diagrams() {
                return (UmlDiagramSet) objectModel.getManagedObject().getPojo();
            }
        };
    }
}
