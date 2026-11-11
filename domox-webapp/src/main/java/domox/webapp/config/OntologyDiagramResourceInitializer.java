package domox.webapp.config;

import domox.dom.requirements.OntologyDiagrams;
import org.apache.causeway.viewer.wicket.model.causeway.WicketApplicationInitializer;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.resource.AbstractResource;
import org.apache.wicket.request.resource.ResourceReference;
import org.apache.wicket.util.string.StringValue;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Mounts the two endpoints the D3 renderer uses to drill down from a graph node into the search for
 * that node's label:
 * <ul>
 *   <li>a <b>resource</b> at {@code /ontology-diagram?term=…} returning the rendered D3 JSON — fetched
 *       by the page to redraw the graph <em>in place</em>;</li>
 *   <li>a <b>page</b> at {@code /ontology-drilldown?term=…} that redirects to the full Causeway object
 *       page ({@link OntologyDrilldownPage}) — opened in a new tab on Ctrl/Cmd-click.</li>
 * </ul>
 *
 * <p><b>Why not just link to the Causeway page.</b>  The D3 renderer cannot build the URL of a
 * Causeway view-model page itself: a view model is addressed as
 * {@code /wicket/object/<objectType>:<id>} where the {@code id} is a <em>signed</em> memento only the
 * framework can mint (a RestfulObjects oid pasted into that URL fails with <i>"Digital verification
 * failed"</i>).  Nor can the browser call the RestfulObjects API, which authenticates per request (a
 * fetch from the page gets a 401).  These two mounts run inside the viewer's own session instead.</p>
 *
 * <p>Registered the same way as {@link WebjarFixerInitializer}: Causeway's
 * {@code CausewayWicketApplication} injects {@code List<WicketApplicationInitializer>} and applies
 * each during its own {@code init()}.</p>
 *
 * <p>The resource's response is the same JSON shape the {@code @D3Graph} property carries, so the
 * client can redraw the canvas without any server round-trip through Wicket's component tree.</p>
 */
@Component
public class OntologyDiagramResourceInitializer implements WicketApplicationInitializer {

    /** Mount path of the JSON resource, relative to the viewer's Wicket base (e.g. {@code /wicket}). */
    static final String MOUNT_PATH = "/ontology-diagram";

    /** Mount path of the full-object-page redirect used by Ctrl/Cmd-click. */
    static final String DRILLDOWN_PATH = "/ontology-drilldown";

    /** Query parameter carrying the term to search for. */
    static final String TERM_PARAM = "term";

    private final OntologyDiagrams ontologyDiagrams;

    public OntologyDiagramResourceInitializer(final OntologyDiagrams ontologyDiagrams) {
        this.ontologyDiagrams = ontologyDiagrams;
    }

    @Override
    public void init(final WebApplication application) {
        application.mountPage(DRILLDOWN_PATH, OntologyDrilldownPage.class);

        application.mountResource(MOUNT_PATH, new ResourceReference("domoxOntologyDiagram") {
            @Override
            public AbstractResource getResource() {
                return new AbstractResource() {
                    @Override
                    protected ResourceResponse newResourceResponse(final Attributes attributes) {
                        final StringValue term = attributes.getRequest().getQueryParameters()
                                .getParameterValue(TERM_PARAM);
                        // Never throws: a failing endpoint degrades to an empty (but well-formed) graph.
                        final String json = ontologyDiagrams.ontologyAsD3Graph(term.toString(""));

                        final ResourceResponse response = new ResourceResponse();
                        response.setContentType("application/json");
                        response.setCacheDuration(Duration.ZERO);
                        response.setWriteCallback(new WriteCallback() {
                            @Override
                            public void writeData(final Attributes attributes) {
                                attributes.getResponse().write(json);
                            }
                        });
                        return response;
                    }
                };
            }
        });
    }
}
