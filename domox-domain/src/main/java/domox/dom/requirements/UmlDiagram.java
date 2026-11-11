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
 * View model returned by {@link OntologyDiagrams#openUmlDiagram(String)}: it carries the diagram's name and
 * exposes the reconstructed picture as a {@link D3Graph} <b>property</b>.
 *
 * <p><b>Static, not force-directed.</b>  {@link OntologyDiagram} searches a concept and lays the
 * result out with a {@code d3-force} simulation.  A UML export, by contrast, already <em>stores the
 * picture</em> — each {@code ClassView}'s rectangle and each {@code RelationView}'s polyline (see
 * {@link domox.dom.rdf.UmlLayoutDiagrams}) — so the graph carries its own coordinates and the client
 * draws it as-is, which is what makes it look like the model's own diagram.</p>
 *
 * <p>Why a view model rather than a {@code String} result: {@code @D3Graph} is a property facet, so an
 * action returning a plain {@code String} is displayed as text instead of being drawn as a graph.</p>
 */
@DomainObject(nature = Nature.VIEW_MODEL)
@Named(DomainModule.NAMESPACE + ".UmlDiagram")
@DomainObjectLayout(cssClassFa = "project-diagram")
@NoArgsConstructor
public class UmlDiagram {

    /** Rendered when the collaborating service has not been injected (e.g. a detached instance). */
    private static final String EMPTY_GRAPH = "{\"layout\":\"uml\",\"nodes\":[],\"links\":[],\"legend\":[]}";

    /**
     * Injected {@link OntologyDiagrams} service that reads the export.
     * <p>
     * Hidden from the metamodel: an injected collaborator is <em>not</em> a property, and exposing it
     * would trip the metamodel veto (see {@code CODING_STANDARDS.md} §1).  It is only a
     * <em>fallback</em> — the picture is normally computed by the action and handed over eagerly, so
     * the view model renders even if this field is never injected.
     */
    @Inject
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private OntologyDiagrams ontologyDiagrams;

    /**
     * The diagram this view model shows, as named by the export.
     * <p>
     * Kept as a metamodel property (rather than {@code @Programmatic}) so the memento still round-trips
     * through a bookmark, but <b>hidden everywhere</b> — {@link #title()} already surfaces it.
     */
    @Getter
    @PropertyLayout(
            named = "Diagram",
            sequence = "1",
            hidden = Where.EVERYWHERE)
    private String diagramName;

    /**
     * The reconstructed picture JSON, computed by {@link OntologyDiagrams} when the action creates the view
     * model.  Not a metamodel member (no accessor): the graph is exposed only through
     * {@link #getDiagram()}, and this field is an implementation detail / cache.
     */
    private String diagramJson;

    /**
     * Reason the picture could not be produced — {@code null} while everything is fine.  Set when the
     * configured source cannot be queried, or when the export ships no diagram of that name, so the
     * viewer shows a short explanation instead of a stack trace.
     */
    private String error;

    @Title
    public String title() {
        final String base = "UML diagram: "
                + (diagramName == null || diagramName.isBlank() ? "(richest)" : diagramName);
        return error == null ? base : base + " — ⚠ source unavailable";
    }

    /** Why the picture is empty, when it could not be produced; hidden otherwise. */
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(
            named = "Message",
            describedAs = "Set when the UML diagram could not be reconstructed",
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

    /** Changing the diagram invalidates the cached picture, so it is re-derived on the next render. */
    public void setDiagramName(final String diagramName) {
        this.diagramName = diagramName;
        this.diagramJson = null;
        this.error = null;
    }

    /**
     * The reconstructed UML class diagram as a <b>static</b> D3 graph: the export's own box
     * rectangles and connector polylines, translated into a padded canvas.
     */
    @D3Graph
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(
            named = "UML Class Diagram / D3",
            describedAs = "The UML class diagram, drawn at the coordinates the model stores",
            sequence = "2")
    public String getDiagram() {
        ensureDerived();
        return diagramJson != null ? diagramJson : EMPTY_GRAPH;
    }

    /**
     * Derives the picture on first access, if the caller did not already supply it (or a failure).
     * <p>
     * Needed because a view model reloaded from a bookmark — or reached via a plain property access,
     * where the viewer may evaluate members in any order — has neither the cached picture nor the
     * failure, and {@link #getMessage()}/{@link #hideMessage()} must see the reason to report it.
     */
    private void ensureDerived() {
        if (diagramJson == null && error == null && ontologyDiagrams != null) {
            final UmlDiagram rederived = ontologyDiagrams.umlDiagram(diagramName);
            this.diagramJson = rederived.diagramJson;
            this.error = rederived.error;
        }
    }

    /** Records a successfully reconstructed diagram. */
    void render(final String diagramJson) {
        this.diagramJson = diagramJson;
        this.error = null;
    }

    /** Records that the diagram could not be reconstructed; the graph renders empty. */
    void fail(final String reason) {
        this.diagramJson = null;
        this.error = reason;
    }

    /**
     * Factory used by {@link OntologyDiagrams#openUmlDiagram(String)}; the caller then supplies the picture (or
     * the failure reason) via {@link #render}/{@link #fail}.
     */
    static UmlDiagram of(final String diagramName) {
        final UmlDiagram viewModel = new UmlDiagram();
        viewModel.diagramName = diagramName;
        return viewModel;
    }
}
