package domox.webapp.config;

import org.apache.wicket.core.request.handler.ComponentNotFoundException;
import org.apache.wicket.core.request.handler.IPageRequestHandler;
import org.apache.wicket.core.request.handler.RenderPageRequestHandler;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.IRequestCycle;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.component.IRequestablePage;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Guardrail for {@link StaleComponentRecoveryListener}: a click on a <em>stale link</em> (a link
 * whose component is no longer part of the page) must re-render the page instead of failing the
 * request — see the listener's javadoc for the symptom and the triggers.
 *
 * <p>Lives in the same package so the package-private
 * {@link StaleComponentRecoveryListener#recover(Exception, IRequestHandler)} decision can be
 * exercised without a running Wicket application (no {@code wicket-tester}, no Mockito — the
 * webapp module declares neither).</p>
 */
class StaleComponentRecoveryListenerTest {

    /** A real component path, as reported by Wicket when a navigator page link goes stale. */
    private static final String WICKET_MESSAGE =
            "Component 'theme:domainObjectContainer:domainObject:rows:2:rowContents:1:col:tabGroups:1:"
            + "panel:tabPanel:rows:1:rowContents:1:col:collections:1:collectionGroup:collection:"
            + "collectionContents:collectionContents-1:table:bottomToolbars:toolbars:2:span:navigator:"
            + "navigation:1:pageLink' has been removed from page.";

    @Test
    void reRendersTheAddressedPage_whenTheComponentIsGone() {
        final IRequestHandler recovered = StaleComponentRecoveryListener.recover(
                new ComponentNotFoundException(WICKET_MESSAGE), pageHandler());

        assertInstanceOf(RenderPageRequestHandler.class, recovered,
                "a stale link must re-render the page instead of failing the request");
    }

    @Test
    void reRenders_whenTheComponentNotFoundIsWrapped() {
        final Exception wrapped = new IllegalStateException("listener failed",
                new ComponentNotFoundException(WICKET_MESSAGE));

        assertInstanceOf(RenderPageRequestHandler.class,
                StaleComponentRecoveryListener.recover(wrapped, pageHandler()),
                "Wicket wraps listener failures, so the cause chain must be inspected");
    }

    @Test
    void leavesEveryOtherExceptionToWicketsDefaultHandling() {
        assertNull(StaleComponentRecoveryListener.recover(new IllegalStateException("boom"), pageHandler()),
                "only ComponentNotFoundException may be swallowed; everything else must stay visible");
    }

    @Test
    void givesUp_whenThereIsNoPageContextToReRender() {
        assertNull(StaleComponentRecoveryListener.recover(
                        new ComponentNotFoundException(WICKET_MESSAGE), null),
                "with no active handler there is no page to re-render");

        assertNull(StaleComponentRecoveryListener.recover(
                        new ComponentNotFoundException(WICKET_MESSAGE),
                        new IRequestHandler() {
                            @Override
                            public void respond(final IRequestCycle requestCycle) {
                                // never called
                            }
                        }),
                "a non-page handler carries no page class/parameters");
    }

    // -- helper

    /** Minimal {@link IPageRequestHandler}; constructing it needs no Wicket application. */
    private static IPageRequestHandler pageHandler() {
        return new IPageRequestHandler() {
            @Override
            public Class<? extends IRequestablePage> getPageClass() {
                return WebPage.class;
            }

            @Override
            public PageParameters getPageParameters() {
                return new PageParameters();
            }

            @Override
            public IRequestablePage getPage() {
                return null;    // the stale page instance is deliberately not available
            }

            @Override
            public Integer getPageId() {
                return 42;
            }

            @Override
            public boolean isPageInstanceCreated() {
                return false;
            }

            @Override
            public Integer getRenderCount() {
                return 1;
            }

            @Override
            public void respond(final IRequestCycle requestCycle) {
                // never called
            }
        };
    }
}
