package domox.webapp.config;

import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.IRequestMapper;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.handler.resource.ResourceReferenceRequestHandler;
import org.apache.wicket.request.resource.ResourceReference;
import org.apache.wicket.util.string.Strings;

import de.agilecoders.wicket.webjars.request.resource.IWebjarsResourceReference;

/**
 * Fixes the "Could not resolve class [wicket]" error and the double-wicket URL
 * issue for webjar resources (like PDF.js) that lack the {@code _ROOT_} placeholder
 * in their resource path.
 *
 * <p><b>Problem:</b> The Causeway {@code AlternativeResourceRequestMapper} returns
 * {@code null} from {@link #mapHandler(IRequestHandler)} for webjar resources that
 * don't contain {@code _ROOT_} in their URL (e.g. PDF.js, D3.js).  Wicket's default
 * {@code ResourceReferenceMapper} then generates a URL like
 * {@code /wicket/resource/className/webjars/...} that goes through Wicket's resource
 * serving, but the version-hashed URL confuses the mapper chain on subsequent requests,
 * causing {@code ClassNotFoundException: wicket} and a spurious WARN log.</p>
 *
 * <p><b>Fix:</b> This mapper is mounted via {@link WebjarFixerInitializer} and
 * intercepts {@link #mapHandler(IRequestHandler)} for webjar resources.  It generates
 * an absolute {@code /webjars/...} URL that Spring Boot serves directly from its
 * {@code META-INF/resources/webjars/} static resource handler, completely bypassing
 * Wicket's resource serving.  For {@link #mapRequest(Request)} and non-webjar resources
 * it falls through to the existing mapper chain by returning {@code null}.</p>
 */
public class WebjarResourceMapperFix implements IRequestMapper {

    @Override
    public int getCompatibilityScore(Request request) {
        // Return 0 (neutral) — we never compete for request parsing.
        // The existing mappers (ResourceReferenceMapper score 1) always win.
        return 0;
    }

    @Override
    public IRequestHandler mapRequest(Request request) {
        // We do NOT handle incoming requests.  Defer to the existing mappers.
        return null;
    }

    @Override
    public Url mapHandler(IRequestHandler handler) {
        // Only intercept webjar resource references to fix URL generation.
        if (handler instanceof ResourceReferenceRequestHandler refHandler) {
            ResourceReference ref = refHandler.getResourceReference();
            if (ref instanceof IWebjarsResourceReference) {
                String resourceName = ref.getName();
                // ref.getName() returns the full resource path, e.g.
                // "/webjars/pdfjs-dist/5.4.624/build/pdf.min.mjs"
                // (with leading slash, from prependWebjarsPathIfMissing).
                // Generate an absolute URL that Spring Boot's webjars
                // resource handler can serve directly.
                if (!Strings.isEmpty(resourceName)) {
                    // Ensure the URL starts with "/" for an absolute path
                    String webjarUrl = resourceName.startsWith("/")
                            ? resourceName
                            : "/" + resourceName;
                    return Url.parse(webjarUrl);
                }
            }
        }
        // Non-webjar: return null so the next mapper in the chain is tried.
        return null;
    }
}
