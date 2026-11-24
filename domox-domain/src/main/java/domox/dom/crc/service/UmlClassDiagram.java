package domox.dom.crc.service;
import domox.DomainModule;
import domox.dom.crc.model.DomainModel;
import domox.dom.rdf.UmlDiagramLayout;
import generate.ClassDiagram;
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
 * View model returned by the diagram actions of {@link DomainModels}: the assembled model's class
 * diagram, drawn by the browser.
 *
 * <p><b>One picture, drawn by the D3 panel.</b>  {@link #getD3Graph()} <em>is</em> the diagram:
 * {@link DomainModels} assembles the boxes and connectors once (as a {@link ClassDiagram.Graph}) and this
 * view model lays them out as the {@code {"layout":"uml","force":true,…}} payload the D3 panel draws and
 * the reader can drag.  It needs no external renderer, so the page has neither a third-party dependency
 * nor a failure mode that replaces the picture with an error message.</p>
 *
 * <p><b>The PlantUML is a download, not a tab.</b>  It used to be a property here — and before that an
 * action result, where a plain {@code String} is rendered through the viewer's COMPACT label fragment, so
 * the whole diagram arrived as one long line (see {@code static/css/domox.css}).  It is now
 * {@link DomainModels#downloadPlantUml(domox.dom.crc.model.DomainModel, String, String)}: the text is what
 * the assembly pass measures and what a reviewer pastes into PlantUML tooling, but a page is for the
 * picture and a file is for the source.</p>
 *
 * <p><b>Why a view model at all.</b>  {@code @D3Graph} is a <em>property</em> facet, so a diagram that is a
 * property of the model — rather than a {@code String} returned by an action — needs an object to hang it
 * on.  {@link domox.dom.requirements.UmlDiagram} is the same pattern for the ontology export.</p>
 *
 * <p><b>Lives next to {@link DomainModels}</b> (rather than in {@code domox.dom.crc.diagram}) for the
 * same reason {@code UmlDiagram} lives next to {@code OntologyDiagrams}: the factory is then
 * package-private, so it cannot be mistaken for a member of the view model's own metamodel.</p>
 */
@DomainObject(nature = Nature.VIEW_MODEL)
@Named(DomainModule.NAMESPACE + ".UmlClassDiagram")
@DomainObjectLayout(cssClassFa = "project-diagram")
@NoArgsConstructor
public class UmlClassDiagram {

    /**
     * The whole model, flat — {@link DomainModels#generateUmlDiagram(DomainModel)}.
     *
     * <p>This is the <em>memento value</em> only.  What the kind does — which assembler call builds it, what
     * its label says and what its download is called — is {@link DiagramKind#MODEL}'s, so a fourth kind is
     * one constant there rather than five switches here and in two other classes.</p>
     */
    public static final String KIND_MODEL = DiagramKind.MODEL.key();

    /**
     * One sub-domain — {@link DomainModels#generateSubDomainUmlDiagram(DomainModel, String)}; see
     * {@link DiagramKind#SUB_DOMAIN}.
     */
    public static final String KIND_SUB_DOMAIN = DiagramKind.SUB_DOMAIN.key();

    /**
     * The sub-domains as nodes — {@link DomainModels#generateSubDomainOverviewDiagram(DomainModel)}; see
     * {@link DiagramKind#SUB_DOMAIN_OVERVIEW}.
     */
    public static final String KIND_SUB_DOMAIN_OVERVIEW = DiagramKind.SUB_DOMAIN_OVERVIEW.key();

    /**
     * The model this diagram was drawn from.
     *
     * <p>Kept as a metamodel property (rather than a plain field) so the view-model memento
     * round-trips through a bookmark — Causeway mementoes the <em>settable</em> properties, and an
     * entity reference is encoded as its bookmark.  Hidden everywhere: {@link #title()} surfaces it,
     * and the diagram itself is the point of the page.</p>
     */
    @Getter
    @Setter
    @PropertyLayout(named = "Model", hidden = Where.EVERYWHERE)
    private DomainModel domainModel;

    /**
     * Which of the three diagrams this is — one of the {@code KIND_*} constants; part of the memento.
     * <p>
     * No Lombok {@code @Setter}: {@link #setKind(String)} is written by hand so it can invalidate the
     * cached picture.
     */
    @Getter
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(named = "Diagram", hidden = Where.EVERYWHERE)
    private String kind;

    /** The sub-domain of {@link #KIND_SUB_DOMAIN}; {@code null} for the other two kinds; part of the memento. */
    @Getter
    @Setter
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(named = "Sub-Domain", hidden = Where.EVERYWHERE)
    private String subDomainName;

    /**
     * Injected {@link DomainModels} service, used only as the <em>fallback</em> that re-derives the
     * diagram when the view model was rebuilt from its memento (a page reload) rather than created by
     * the action — the picture is normally computed by the action and handed over eagerly.
     *
     * <p>Hidden from the metamodel: an injected collaborator is not a property (see
     * {@code CODING_STANDARDS.md} §2).</p>
     */
    @Inject
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private DomainModels domainModels;

    /**
     * The diagram as a {@link ClassDiagram.Graph} — the boxes and connectors, assembled once by
     * {@link DomainModels}.
     *
     * <p>Not a metamodel member (no accessor): it is the <em>input</em> to {@link #getD3Graph()}, which
     * lays it out and serialises it.  Kept rather than recomputed because the D3 payload is then
     * derived from the snapshot the action built, so a page reload redraws the same picture.</p>
     */
    private ClassDiagram.Graph graph;

    @Title
    public String title() {
        final String model = domainModel != null ? domainModel.title() : "Domain model";
        return "UML class diagram: " + model + " — " + describeKind();
    }

    /**
     * The diagram: the boxes and connectors, laid out offline and serialised as the
     * {@code {"layout":"uml","force":true,…}} payload the D3 panel draws and the reader can drag.
     *
     * <p>The layout is {@link UmlDiagramLayout}'s job, not this view model's: it assigns the bands, sizes
     * the boxes, orders each inheritance row and emits the JSON.  It is a pure function of the graph, so
     * the same graph always produces the same picture.</p>
     */
    @D3Graph
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(
            named = "Diagram",
            describedAs = "The class diagram, laid out offline and drawn by the browser",
            sequence = "1")
    public String getD3Graph() {
        ensureDerived();
        return graph == null
                ? UmlDiagramLayout.EMPTY_GRAPH
                : UmlDiagramLayout.render(graph.getTitle(), graph.getNodes(), graph.getEdges());
    }

    /**
     * The diagram's label — <b>which</b> view of the model this is, in words.
     *
     * <p>This is where the sub-domain a diagram belongs to is shown, because a Causeway <em>tab</em> label
     * cannot be: the tab name comes from {@code UmlClassDiagram.layout.xml}, and one layout serves every
     * instance of the view model, so it cannot vary per diagram ({@code BSTab} carries nothing but a name,
     * and {@code TabGroupPanel.tabsFor} labels each tab from it).  A member-support method, by contrast, is
     * re-read for the instance on the page, so the panel says "Sub-domain: Inventory" rather than leaving
     * the reader to infer it from the object title.</p>
     */
    @MemberSupport
    public String namedD3Graph() {
        return DiagramKind.of(kind).label(subDomainName);
    }

    /** Changing the memento key invalidates the cached diagram, so it is re-derived on the next render. */
    public void setKind(final String kind) {
        this.kind = kind;
        this.graph = null;
    }

    /**
     * Re-derives the diagram on first access, if the caller did not already supply it.
     *
     * <p>Needed because a view model reloaded from a bookmark — a page reload, or a link back to the
     * page — has only the memento's properties ({@link #domainModel}, {@link #kind},
     * {@link #subDomainName}); the graph is not part of a memento.  Without this the page would show an
     * empty diagram.</p>
     */
    private void ensureDerived() {
        if (graph == null && domainModels != null && domainModel != null) {
            this.graph = domainModels.umlClassDiagram(domainModel, kind, subDomainName).graph;
        }
    }

    /** What this diagram shows, in words — the second half of {@link #title()}. */
    private String describeKind() {
        return DiagramKind.of(kind).describe(subDomainName);
    }

    /** Factory used by {@link DomainModels}; the caller then supplies the content via {@link #withGraph}. */
    static UmlClassDiagram of(
            final DomainModel domainModel,
            final String kind,
            final String subDomainName) {
        final UmlClassDiagram viewModel = new UmlClassDiagram();
        viewModel.domainModel = domainModel;
        viewModel.kind = kind;
        viewModel.subDomainName = subDomainName;
        return viewModel;
    }

    /** Records the diagram's content as a graph — what {@link #getD3Graph()} lays out and serialises. */
    UmlClassDiagram withGraph(final ClassDiagram.Graph graph) {
        this.graph = graph;
        return this;
    }
}
