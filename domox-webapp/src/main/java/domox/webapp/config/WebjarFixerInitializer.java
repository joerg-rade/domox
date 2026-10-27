package domox.webapp.config;

import org.apache.causeway.viewer.wicket.model.causeway.WicketApplicationInitializer;
import org.apache.wicket.protocol.http.WebApplication;
import org.springframework.stereotype.Component;

/**
 * Spring-managed {@link WicketApplicationInitializer} that mounts a
 * {@link WebjarResourceMapperFix} to correct webjar resource URL generation.
 *
 * <p>This initializer runs after the Causeway {@code WebjarsInitWkt} has mounted
 * its {@code AlternativeResourceRequestMapper}.  It mounts a fix mapper that
 * generates absolute {@code /webjars/...} URLs for ALL webjar resources (not just
 * those with the {@code _ROOT_} placeholder), bypassing Wicket's resource serving
 * and going directly to Spring Boot's static resource handler.</p>
 *
 * <p>The fix prevents the "Could not resolve class [wicket]" warning and the
 * double-{@code /wicket/wicket/} URL prefix for resources like PDF.js.</p>
 *
 * <p><b>How it works:</b></p>
 * <ul>
 *   <li>{@link WebjarResourceMapperFix#getCompatibilityScore(Request)} returns
 *       0 (neutral), so existing mappers with score 1 win for request parsing.</li>
 *   <li>{@link WebjarResourceMapperFix#mapRequest(Request)} returns null,
 *       deferring all request parsing to existing mappers.</li>
 *   <li>{@link WebjarResourceMapperFix#mapHandler(IRequestHandler)} intercepts
 *       webjar resource references and generates absolute {@code /webjars/...} URLs.
 *       For non-webjar resources it returns null, deferring to existing mappers.</li>
 *   <li>The {@code CompoundRequestMapper.mapHandler()} iterates mappers in
 *       registration order (first added = first tried).  Since the
 *       {@code AlternativeResourceRequestMapper} is mounted first (by
 *       {@code WebjarsInitWkt}), it is tried first.  For resources without
 *       {@code _ROOT_} (like PDF.js), it returns null, so the next mapper
 *       (our fix) is tried.</li>
 * </ul>
 */
@Component
public class WebjarFixerInitializer implements WicketApplicationInitializer {

    @Override
    public void init(WebApplication application) {
        // Mount our fix mapper.  It will be tried FIRST for mapHandler() calls
        // (most recently mounted = highest priority).  For webjar resources it
        // returns a clean ../webjars/... URL; for everything else it returns
        // null and the existing mappers handle it.
        application.mount(new WebjarResourceMapperFix());
    }
}
