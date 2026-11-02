package domox.webapp.unittests;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.link.Link;

/**
 * Minimal page with a single listener link, used by {@link StaleComponentRecoveryEndToEndTest} to
 * construct a <em>stale link</em>: the page instance keeps its id, but the link component is
 * removed from the tree — which is exactly what Wicket sees when a link rendered by an earlier
 * render is clicked (page-size change, AJAX table refresh, restart/redeploy, older tab).
 *
 * <p>The markup lives next to this class as {@code StaleLinkPage.html} and reaches the test
 * classpath because {@code domox-webapp} lists {@code src/test/java} as a test-resource root
 * (excluding {@code *.java}).</p>
 */
public class StaleLinkPage extends WebPage {

    public StaleLinkPage() {
        add(new Link<Void>("stale") {
            @Override
            public void onClick() {
                // nothing to do — the tests only care that the listener exists (and later doesn't)
            }
        });
    }
}
