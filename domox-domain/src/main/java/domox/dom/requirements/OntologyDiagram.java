package domox.dom.requirements;

import domox.DomainModule;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.MemberSupport;
import org.apache.causeway.applib.annotation.Nature;
import org.apache.causeway.applib.annotation.Optionality;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.annotation.Title;
import org.apache.causeway.applib.annotation.Where;
import org.apache.causeway.extensions.d3.applib.annotations.D3Graph;

/**
 * View model returned by {@link OntologyDiagrams#searchOntologyAsD3Graph(String)}: it carries the search
 * term as ordinary state and exposes the rendered ontology as a {@link D3Graph} <b>property</b>.
 *
 * <p><b>Why a view model rather than a {@code String} result.</b>  {@code @D3Graph} is a
 * property/domain-object facet, so an action returning a plain {@code String} is displayed as text
 * — the Wicket viewer only draws a canvas for a property.  Returning this object is the Causeway
 * idiom for "an action whose result is itself a rendered object".</p>
 *
 * <p>The diagram is <em>derived</em>: it is regenerated on every access from the configured ontology
 * source — the local Fuseki SPARQL endpoint ({@code domox.rdf.endpoint-url}) in the running app, or
 * the bundled classpath ontology when no endpoint is configured — so editing
 * {@link #getSearchTerm() the search term} re-renders the graph.</p>
 */
@DomainObject(nature = Nature.VIEW_MODEL)
@Named(DomainModule.NAMESPACE + ".OntologyDiagram")
@DomainObjectLayout(cssClassFa = "project-diagram")
@NoArgsConstructor
public class OntologyDiagram {

    /** Rendered when the collaborating service has not been injected (e.g. a detached instance). */
    private static final String EMPTY_GRAPH = "{\"nodes\":[],\"links\":[],\"legend\":[]}";

    /**
     * Injected {@link OntologyDiagrams} service that does the SPARQL work.
     * <p>
     * Hidden from the metamodel: an injected collaborator is <em>not</em> a property, and exposing
     * it would trip the metamodel veto (see {@code CODING_STANDARDS.md} §1).  It is only a
     * <em>fallback</em>: the graph is normally computed by the action and handed over eagerly, so
     * the view model renders even if this field is never injected.
     */
    @Inject
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private OntologyDiagrams ontologyDiagrams;

    /**
     * The term the ontology is filtered by; blank means "the whole ontology".
     * <p>
     * <b>Hidden everywhere</b> — it is already surfaced in {@link #title()} ("Ontology: …"), so a
     * dedicated "Search Term" property would be redundant.  It is deliberately kept as a metamodel
     * property (rather than made {@link org.apache.causeway.applib.annotation.Programmatic}) so the
     * view model's memento still round-trips through a bookmark.
     */
    @Getter
    @PropertyLayout(
            named = "Search Term",
            sequence = "1",
            hidden = Where.EVERYWHERE)
    private String searchTerm;

    /**
     * The rendered graph JSON, computed by {@link OntologyDiagrams} when the action creates the view model.
     * <p>
     * Deliberately not a metamodel member (no accessor): the graph is exposed only through
     * {@link #getDiagram()}, and this field is an implementation detail / cache.
     */
    private String diagramJson;

    /**
     * Reason the graph could not be produced — {@code null} while everything is fine.
     * <p>
     * Set by {@link OntologyDiagrams#searchOntologyAsD3Graph(String)} when the configured source cannot be
     * queried (an unreachable or overloaded SPARQL endpoint, a timeout, …), so the viewer shows a
     * short explanation instead of a stack trace.
     */
    private String error;

    @Title
    public String title() {
        final String base = "Ontology: " + (searchTerm == null || searchTerm.isBlank() ? "(all)" : searchTerm);
        return error == null ? base : base + " — ⚠ source unavailable";
    }

    /**
     * Why the graph is empty, when the source could not be queried; hidden otherwise.
     */
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(
            named = "Message",
            describedAs = "Set when the ontology source could not be queried",
            sequence = "1",
            hidden = Where.ALL_TABLES)
    public String getMessage() {
        ensureDerived();
        return error;
    }

    /** Hides {@link #getMessage()} unless there is something to report. */
    @MemberSupport
    public boolean hideMessage() {
        ensureDerived();
        return error == null;
    }

    /**
     * Changing the search term invalidates the cached diagram, so the graph is re-derived on the
     * next render.
     */
    public void setSearchTerm(final String searchTerm) {
        this.searchTerm = searchTerm;
        this.diagramJson = null;
        this.error = null;
    }

    /**
     * The configured RDF/OWL ontology, filtered by {@link #getSearchTerm()}, as an interactive D3
     * force-layout graph.
     */
    @D3Graph
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(
            named = "Ontology Diagram / D3",
            describedAs = "The RDF/OWL ontology, filtered by the search term, as an interactive D3 graph",
            sequence = "2")
    public String getDiagram() {
        ensureDerived();
        return diagramJson != null ? diagramJson : EMPTY_GRAPH;
    }

    /**
     * Derives the graph on first access, if the caller did not already supply it (or a failure).
     * <p>
     * Needed because a view model reloaded from a bookmark — or reached via a plain property
     * access, where the viewer may evaluate members in any order — has neither the cached graph nor
     * the failure, and {@link #getMessage()}/{@link #hideMessage()} must see the reason to report it.
     */
    private void ensureDerived() {
        if (diagramJson == null && error == null && ontologyDiagrams != null) {
            final OntologyDiagram rederived = ontologyDiagrams.ontologyDiagram(searchTerm);
            this.diagramJson = rederived.diagramJson;
            this.error = rederived.error;
        }
    }

    /** Records a successfully rendered graph. */
    void render(final String diagramJson) {
        this.diagramJson = diagramJson;
        this.error = null;
    }

    /** Records that the source could not be queried; the graph renders empty. */
    void fail(final String reason) {
        this.diagramJson = null;
        this.error = reason;
    }

    /**
     * Factory used by {@link OntologyDiagrams#searchOntologyAsD3Graph(String)}; the caller then supplies the
     * rendered graph (or the failure reason) via {@link #render}/{@link #fail}.
     */
    static OntologyDiagram of(final String searchTerm) {
        final OntologyDiagram viewModel = new OntologyDiagram();
        viewModel.searchTerm = searchTerm;
        return viewModel;
    }
}
