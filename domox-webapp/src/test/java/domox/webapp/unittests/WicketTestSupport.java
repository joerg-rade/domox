package domox.webapp.unittests;

import de.agilecoders.wicket.core.Bootstrap;
import de.agilecoders.wicket.webjars.WicketWebjars;
import org.apache.wicket.markup.html.SecurePackageResourceGuard;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.util.tester.WicketTester;

/**
 * The bootstrap every Wicket unit test in this repo needs, in one place.
 *
 * <p><b>Why this exists.</b> A {@code WicketTester} over a bare {@link WebApplication} renders a plain
 * panel, but a <em>D3 panel</em> or an <em>{@code AjaxBootstrapTabbedPanel}</em> needs three things the
 * real application does at init — and each one is a separate, differently-worded test failure if it is
 * missing (see {@link #installD3Prerequisites}). Deriving them took a jar dive plus a three-failure loop;
 * they are a fact about this stack, not about any one test.</p>
 *
 * <p>Usage — one line, and the panel renders:</p>
 * <pre>
 * final WicketTester tester = WicketTestSupport.newTester();
 * tester.startComponentInPage(new SomePanel("panel", source));
 * </pre>
 *
 * <p>A test that renders neither a D3 panel nor a bootstrap tab strip does not need this:
 * {@code StaleComponentRecoveryEndToEndTest} keeps its own plain {@code WebApplication}, and
 * {@code UmlDiagramTabsPanelComponentFactoryTest} needs no tester at all. The facts behind the section
 * below are in {@code .clinerules/memory-bank/technical/CAUSEWAY-LAYOUT.md} §9.</p>
 */
public final class WicketTestSupport {

    private WicketTestSupport() {
    }

    /**
     * The three things a bare Wicket app needs before a D3 panel or a bootstrap tab strip renders, all of
     * which the real viewer does for the application:
     *
     * <ul>
     *   <li>{@code WicketWebjars.install} — the D3 panel's header references are webjar references, so
     *       without it the resource URL cannot be resolved;</li>
     *   <li>{@code Bootstrap.install} — {@code AjaxBootstrapTabbedPanel} needs the bootstrap theme and
     *       behaviour;</li>
     *   <li>the package-resource guard's {@code +*.mjs} / {@code +*.css} — Wicket's default
     *       {@link SecurePackageResourceGuard} blocks both, so the integration script's URL cannot be
     *       generated.</li>
     * </ul>
     *
     * <p>Mirrors the D3 extension's own {@code D3TestApplication}, which does the first and the third.</p>
     */
    public static void installD3Prerequisites(final WebApplication app) {
        WicketWebjars.install(app);
        Bootstrap.install(app);
        final SecurePackageResourceGuard resourceGuard =
                (SecurePackageResourceGuard) app.getResourceSettings().getPackageResourceGuard();
        // '+…' allows, '-…' denies: the guard's pattern syntax
        resourceGuard.addPattern("+*.mjs");
        resourceGuard.addPattern("+*.css");
    }

    /**
     * A {@link WicketTester} whose application already installs them. No home page and no markup file: a
     * component is rendered with {@code tester.startComponentInPage(...)}.
     */
    public static WicketTester newTester() {
        return new WicketTester(new TestApp());
    }

    /**
     * The synthetic application behind {@link #newTester()}. Public and static so a test can subclass it
     * when it needs its own init (e.g. a request-cycle listener) on top of the prerequisites.
     */
    public static class TestApp extends WebApplication {
        @Override
        protected void init() {
            super.init();
            installD3Prerequisites(this);
        }

        @Override
        public Class<? extends WebPage> getHomePage() {
            return null;
        }
    }
}
