package domox.webapp.unittests;

import de.agilecoders.wicket.core.Bootstrap;
import de.agilecoders.wicket.webjars.WicketWebjars;
import domox.webapp.config.UmlDiagramTabsPanel;
import org.apache.wicket.markup.html.SecurePackageResourceGuard;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.Test;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardrail for {@link UmlDiagramTabsPanel} — the page {@code DomainModels#showUmlDiagrams} opens: one
 * bootstrap tab per diagram, labelled with the sub-domain name.
 *
 * <p><b>What this pins.</b>  That the labels are the ones the view model offers, that each tab's content
 * is a D3 panel (so a tab is drawn by the same renderer as the single-diagram pages), and — the reason
 * the payload is asked for from inside {@code ITab#getPanel} — that opening the page assembles <em>one</em>
 * diagram, not all of them: assembling one reads the whole model (~2 s for the 970-class model), so a
 * ten-sub-domain page that assembled eagerly would take ~18 s to appear.</p>
 *
 * <p>No Spring context, no Causeway session and no browser: the panel takes plain data (its
 * {@link UmlDiagramTabsPanel.Source}), so it renders in a bare {@code WicketTester} — the same cheap
 * harness {@code StaleComponentRecoveryEndToEndTest} uses, which is why this runs in
 * {@code scripts/check.sh}.</p>
 */
class UmlDiagramTabsPanelTest {

    /**
     * The three things a bare Wicket app needs before a D3 panel renders, all of which the real viewer
     * does for the application: install wicket-webjars (the D3 panel's header references are webjar
     * references), install wicket-bootstrap (the tab strip is {@code AjaxBootstrapTabbedPanel}), and let
     * the package-resource guard serve {@code .mjs}/{@code .css} (Wicket's default guard blocks them, so
     * the integration script's URL cannot be generated).  Mirrors the extension's own
     * {@code D3TestApplication}, which does the first and third.
     */
    public static class TestApp extends WebApplication {
        @Override
        protected void init() {
            super.init();
            WicketWebjars.install(this);
            Bootstrap.install(this);
            final SecurePackageResourceGuard resourceGuard =
                    (SecurePackageResourceGuard) getResourceSettings().getPackageResourceGuard();
            // '+…' allows, '-…' denies: the guard's pattern syntax
            resourceGuard.addPattern("+*.mjs");
            resourceGuard.addPattern("+*.css");
        }

        @Override
        public Class<? extends WebPage> getHomePage() {
            return null;
        }
    }

    /**
     * The panel, rendered into a synthetic page — the Wicket idiom for a panel test, and the reason this
     * class needs no markup file of its own.
     */
    private static WicketTester render(final UmlDiagramTabsPanel.Source diagrams) {
        final var tester = new WicketTester(new TestApp());
        tester.startComponentInPage(new UmlDiagramTabsPanel("tabs", diagrams));
        return tester;
    }

    /**
     * The view model as the panel sees it: three tabs, each payload naming the tab it belongs to.
     * Records which payloads were asked for — that record <em>is</em> the laziness assertion.
     */
    private static class RecordingSource implements UmlDiagramTabsPanel.Source {
        @Serial
        private static final long serialVersionUID = 1L;

        private final List<String> asked = new ArrayList<>();

        @Override
        public List<String> tabNames() {
            return List.of("Sub-domain overview", "Inventory", "Whole model");
        }

        @Override
        public String payloadOf(final String tabName) {
            asked.add(tabName);
            return "{\"layout\":\"uml\",\"force\":true,\"nodes\":[{\"id\":\"%s\"}],\"links\":[],\"legend\":[]}"
                    .formatted(tabName);
        }
    }

    @Test
    void rendersOneTabPerDiagram_labelledWithItsName() {
        final WicketTester tester = render(new RecordingSource());

        final String html = tester.getLastResponse().getDocument();
        assertThat(html).as("a bootstrap tab strip")
                .contains("nav-tabs");
        assertThat(html).as("one tab per diagram, labelled with the sub-domain name")
                .contains("Sub-domain overview").contains("Inventory").contains("Whole model");
        assertThat(html).as("the shown tab holds a D3 panel — the same renderer as the single-diagram pages")
                .contains("d3GraphCanvas");
    }

    @Test
    void assemblesOnlyTheTabThatIsShown() {
        final RecordingSource source = new RecordingSource();

        final WicketTester tester = render(source);

        assertThat(source.asked)
                .as("Wicket builds the panel of the shown tab only, so the page assembles one diagram")
                .containsExactly("Sub-domain overview");
        assertThat(tester.getLastResponse().getDocument())
                .as("and only that one panel is in the DOM")
                .containsOnlyOnce("d3GraphCanvas");
    }
}
