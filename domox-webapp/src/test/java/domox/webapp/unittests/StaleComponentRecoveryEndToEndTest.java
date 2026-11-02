package domox.webapp.unittests;

import domox.webapp.config.StaleComponentRecoveryListener;
import org.apache.wicket.core.request.handler.ComponentNotFoundException;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end guardrail for the stale-link recovery (see {@link StaleComponentRecoveryListener}).
 *
 * <p>Drives a real Wicket request cycle in-process: render {@link StaleLinkPage}, take the URL of
 * its listener link, remove that component from the page instance (making the link stale), then
 * request the old URL.  With the listener registered the page must be re-rendered; without it the
 * request must fail — which is what makes this a guardrail rather than a smoke test.</p>
 *
 * <p>No Spring context, no browser and no containers are involved, so this runs in the local
 * {@code scripts/check.sh} bundle (which runs {@code domox.webapp.unittests.**}).</p>
 */
class StaleComponentRecoveryEndToEndTest {

    /** Wicket app WITHOUT the recovery listener — the control case. */
    public static class PlainApp extends WebApplication {
        @Override
        public Class<? extends WebPage> getHomePage() {
            return StaleLinkPage.class;
        }
    }

    /** Wicket app WITH the listener registered, as {@code StaleLinkRecoveryInitializer} does. */
    public static class RecoveryApp extends PlainApp {
        @Override
        protected void init() {
            super.init();
            getRequestCycleListeners().add(new StaleComponentRecoveryListener());
        }
    }

    @Test
    void staleListenerRequestReRendersThePage() {
        final var tester = new WicketTester(new RecoveryApp());
        tester.startPage(StaleLinkPage.class);
        final var stalePage = tester.getLastRenderedPage();
        final String listenerUrl = tester.urlFor((Link<?>) stalePage.get("stale"));

        // make the link stale: the page keeps its id, the component is gone
        stalePage.remove("stale");

        tester.executeUrl(listenerUrl);

        final var rendered = tester.getLastRenderedPage();
        assertNotNull(rendered, "a stale link must still render a page");
        assertEquals(StaleLinkPage.class, rendered.getClass());
        assertNotSame(stalePage, rendered,
                "recovery must render a FRESH page instance (PageProvider(pageClass, params))");
        assertEquals(200, tester.getLastResponse().getStatus(),
                "the recovered request must be a normal page render, not an error page");
    }

    @Test
    void withoutTheListenerTheSameRequestFails() {
        final var tester = new WicketTester(new PlainApp());
        tester.startPage(StaleLinkPage.class);
        final var stalePage = tester.getLastRenderedPage();
        final String listenerUrl = tester.urlFor((Link<?>) stalePage.get("stale"));
        stalePage.remove("stale");

        // Control: without the listener the very same request throws — which is what makes the
        // test above a guardrail rather than a smoke test (it would fail if the listener were
        // dropped from the application).
        assertThrows(ComponentNotFoundException.class, () -> tester.executeUrl(listenerUrl));
    }
}
