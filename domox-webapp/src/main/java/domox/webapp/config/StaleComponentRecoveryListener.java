package domox.webapp.config;

import org.apache.wicket.core.request.handler.ComponentNotFoundException;
import org.apache.wicket.core.request.handler.IPageRequestHandler;
import org.apache.wicket.core.request.handler.PageProvider;
import org.apache.wicket.core.request.handler.RenderPageRequestHandler;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.cycle.IRequestCycleListener;
import org.apache.wicket.request.cycle.RequestCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recovers from <em>stale links</em> — a request that targets a component which no
 * longer exists in the page instance the request resolved to.
 *
 * <p><b>Symptom (without this listener):</b></p>
 * <pre>
 * org.apache.wicket.core.request.handler.ComponentNotFoundException:
 *   Component 'theme:domainObjectContainer:domainObject:rows:2:rowContents:1:col:tabGroups:1:
 *   panel:tabPanel:rows:1:rowContents:1:col:collections:1:collectionGroup:collection:
 *   collectionContents:collectionContents-1:table:bottomToolbars:toolbars:2:span:navigator:
 *   navigation:1:pageLink' has been removed from page.
 * </pre>
 *
 * <p><b>When it happens.</b> Wicket links (pagination, sort headers, page-size chooser, tabs)
 * are stateful listeners bound to a component path <em>as rendered</em>. If the page is
 * re-rendered with a different component tree before the click arrives, the path no longer
 * resolves. In this application the usual triggers are:</p>
 * <ul>
 *   <li>the <b>page-size chooser</b> was used (e.g. 25 → <i>All</i>): the navigator then has
 *       fewer pages, so a previously rendered "page 2" link is stale;</li>
 *   <li>an <b>AJAX table refresh</b> replaced the table between render and click (double
 *       click, or a click on a link from the pre-refresh DOM);</li>
 *   <li>the <b>application was restarted / redeployed</b> while the tab was open, or the
 *       page was rendered by an older build (e.g. after a layout change);</li>
 *   <li>a <b>second tab</b> or the browser back button restored an older DOM.</li>
 * </ul>
 *
 * <p><b>Recovery.</b> Instead of failing the request, re-render the page that the request
 * addressed: the page class and its page parameters are read from the request handler
 * (which does not require the page instance to exist) and a fresh page is rendered. For an
 * AJAX request Wicket turns that into a client-side redirect, so the reviewer simply lands
 * on a freshly rendered page with working links.</p>
 *
 * <p><b>Trade-off (deliberate, keep it visible).</b> This also hides a genuine
 * {@code ComponentNotFoundException} — i.e. a real programming error in a Wicket component
 * tree — so the event is logged at <b>WARN</b> with the Wicket message. If such a warning
 * appears in a reproducible scenario, treat it as a bug, not as noise.</p>
 *
 * <p>Everything else is left alone: {@link #onException(RequestCycle, Exception)} returns
 * {@code null} for any other exception, deferring to Wicket's default error handling.</p>
 *
 * <p>Registered by {@link StaleLinkRecoveryInitializer}.</p>
 */
public class StaleComponentRecoveryListener implements IRequestCycleListener {

    private static final Logger log = LoggerFactory.getLogger(StaleComponentRecoveryListener.class);

    @Override
    public IRequestHandler onException(final RequestCycle cycle, final Exception ex) {
        return recover(ex, cycle.getActiveRequestHandler());
    }

    /**
     * Decides how to recover from {@code ex}.  Package-private (rather than private) so the
     * decision can be unit-tested without a running Wicket application — see
     * {@code StaleComponentRecoveryListenerTest}.
     *
     * @param ex     the exception that aborted the request
     * @param active the request handler that was active, or {@code null} if unknown
     * @return a handler that re-renders the addressed page, or {@code null} to let Wicket
     *         apply its default error handling
     */
    static IRequestHandler recover(final Exception ex, final IRequestHandler active) {
        if (!isComponentNotFound(ex)) {
            return null;    // not our concern — Wicket's default handling applies
        }
        if (!(active instanceof IPageRequestHandler pageHandler)) {
            log.warn("Stale link: no page context available for recovery — falling back to Wicket's "
                    + "default error handling. Wicket said: {}", ex.getMessage());
            return null;
        }
        try {
            // Read class + parameters from the handler: this does NOT need the (stale) page instance.
            final var pageClass = pageHandler.getPageClass();
            final var pageParameters = pageHandler.getPageParameters();
            log.warn("Stale link: a component is missing from page '{}' — re-rendering the page instead of "
                    + "failing. (Usual cause: a link was clicked after the page was re-rendered with a different "
                    + "structure — page-size change, AJAX table refresh, restart/redeploy, or an older tab.) "
                    + "Wicket said: {}", pageClass.getSimpleName(), ex.getMessage());
            // RenderPageRequestHandler(IPageProvider) defaults to RedirectPolicy.AUTO_REDIRECT;
            // PageProvider(pageClass, pageParameters) creates a FRESH page instance.
            return new RenderPageRequestHandler(new PageProvider(pageClass, pageParameters));
        } catch (final RuntimeException recoveryFailed) {
            log.warn("Stale link: recovery failed ({}: {}) — falling back to Wicket's default error handling",
                    recoveryFailed.getClass().getSimpleName(), recoveryFailed.getMessage());
            return null;
        }
    }

    /**
     * Walks the cause chain — Wicket wraps listener failures in
     * {@code WicketRuntimeException}s, so the interesting exception is rarely the outermost one.
     */
    private static boolean isComponentNotFound(final Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ComponentNotFoundException) {
                return true;
            }
            if (t.getCause() == t) {
                break;  // self-referencing cause: stop instead of looping forever
            }
        }
        return false;
    }
}
