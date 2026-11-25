package domox.webapp.config;

import de.agilecoders.wicket.core.markup.html.bootstrap.tabs.AjaxBootstrapTabbedPanel;
import domox.dom.crc.service.UmlDiagramSet;
import org.apache.causeway.extensions.d3.applib.config.D3Config;
import org.apache.causeway.extensions.d3.wkt.integration.components.D3Panel;
import org.apache.wicket.extensions.markup.html.tabs.AbstractTab;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a {@link UmlDiagramSet} as one bootstrap tab per diagram, <b>labelled with the sub-domain
 * name</b> — the page {@code DomainModels#showUmlDiagrams} opens.
 *
 * <p><b>Why a panel of its own.</b>  Causeway's own tab group cannot do this: a tab name comes from the
 * layout XML ({@code BSTab} carries nothing but a name, and {@code TabGroupPanel} labels each tab from
 * it), and one layout serves every instance of a view model — so the sub-domains, which are data, cannot
 * be layout tabs.  This panel builds the tabs in code instead, from
 * {@link UmlDiagramSet#tabNames()}, and is selected for the view model's page by
 * {@link UmlDiagramTabsPanelComponentFactory}.</p>
 *
 * <p><b>Each diagram is assembled when its tab is shown.</b>  The payload is asked for from inside
 * {@link ITab#getPanel}, which Wicket calls for the shown tab only, so opening the page assembles one
 * diagram rather than all of them — assembling one reads the whole model (~2 s for the 970-class
 * model).  The panel itself draws each diagram with the extension's own {@code D3Panel}, so a tab is
 * the same picture, drawn by the same renderer, as the diagram page.</p>
 *
 * <p><b>The data comes through {@link Source}, not through the domain object.</b>  A Wicket page is
 * serialized into the session, so a component must not hold the view model pojo (which holds the model
 * and its entities): the factory passes a {@code Source} that reads the object model's current pojo on
 * demand — Causeway's own object model is what knows how to serialize itself.  It also makes this panel
 * a plain presentational component, so {@code UmlDiagramTabsPanelTest} can render it in a bare
 * {@code WicketTester}, with no Spring context and no Causeway session.</p>
 */
public class UmlDiagramTabsPanel extends Panel {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The tab labels and the diagram behind each — what the panel needs, and nothing else.
     *
     * <p>Implementations read the page's view model on demand, so a tab's diagram is assembled when that
     * tab is shown.</p>
     */
    public interface Source extends Serializable {
        /** The tab labels, in the order the page shows them. */
        List<String> tabNames();
        /** The D3 payload of one tab — the JSON the D3 panel draws. */
        String payloadOf(String tabName);
    }

    public UmlDiagramTabsPanel(final String id, final Source diagrams) {
        super(id);
        add(new AjaxBootstrapTabbedPanel<>("tabs", tabsFor(diagrams)));
    }

    /**
     * One tab per label, each holding that diagram's D3 panel.
     *
     * <p>Package-private and static so the test can build the tabs without a page.</p>
     */
    static List<ITab> tabsFor(final Source diagrams) {
        final List<ITab> tabs = new ArrayList<>();
        for (final String tabName : diagrams.tabNames()) {
            tabs.add(new AbstractTab(Model.of(tabName)) {
                @Serial
                private static final long serialVersionUID = 1L;

                @Override
                public Panel getPanel(final String panelId) {
                    return new D3Panel(panelId, D3Config.defaults().withGraphData(diagrams.payloadOf(tabName)));
                }
            });
        }
        return tabs;
    }
}
