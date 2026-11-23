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
import org.apache.causeway.applib.value.Blob;
import org.apache.causeway.extensions.d3.applib.annotations.D3Graph;
import org.apache.causeway.extensions.pdfjs.applib.annotations.PdfJsViewer;

/**
 * View model returned by the diagram actions of {@link DomainModels}: the assembled model's class
 * diagram, <b>rendered</b> by Kroki, plus the PlantUML source it was rendered from.
 *
 * <p><b>Why a view model rather than a {@code String} result.</b>  The actions used to return the
 * PlantUML text, and a plain {@code String} result is displayed as text — the whole diagram arrived
 * as one long line, because the viewer renders an action result through its COMPACT label fragment
 * (see {@code static/css/domox.css}).  A rendered picture needs a <em>property</em>: {@link PdfJsViewer}
 * is a property facet and the viewer's pdf.js panel keys on it, so the diagram has to be handed over
 * as a view model.  {@link domox.dom.requirements.UmlDiagram} is the same pattern for the ontology
 * export.</p>
 *
 * <p><b>The source is kept.</b>  {@link #getPlantUml()} still carries the PlantUML, because it is the
 * artifact the assembly pass measures (class/attribute/operation/edge counts) and the one a reviewer
 * may want to paste into PlantUML tooling.  Rendering it and hiding it would throw that away.</p>
 *
 * <p><b>Two renderers, one content.</b>  {@link #getD3Graph()} draws the <em>same</em> diagram with the
 * D3 panel: {@link DomainModels} assembles the boxes and connectors once (as a
 * {@link ClassDiagram.Graph}) and hands them to both, so the PDF and the D3 picture cannot disagree
 * about what the model contains.  The D3 path needs no Kroki, which is why the diagram stays visible
 * when the renderer is down.</p>
 *
 * <p><b>Lives next to {@link DomainModels}</b> (rather than in {@code domox.dom.crc.diagram}) for the
 * same reason {@code UmlDiagram} lives next to {@code OntologyDiagrams}: the factory and the
 * render/fail hooks are then package-private, so they cannot be mistaken for members of the view
 * model's own metamodel.</p>
 */
@DomainObject(nature = Nature.VIEW_MODEL)
@Named(DomainModule.NAMESPACE + ".UmlClassDiagram")
@DomainObjectLayout(cssClassFa = "project-diagram")
@NoArgsConstructor
public class UmlClassDiagram {

    /** The whole model, flat — {@link DomainModels#generateUmlDiagram(DomainModel)}. */
    public static final String KIND_MODEL = "model";

    /** One sub-domain — {@link DomainModels#generateSubDomainUmlDiagram(DomainModel, String)}. */
    public static final String KIND_SUB_DOMAIN = "sub-domain";

    /** The sub-domains as nodes — {@link DomainModels#generateSubDomainOverviewDiagram(DomainModel)}. */
    public static final String KIND_SUB_DOMAIN_OVERVIEW = "sub-domain-overview";

    /**
     * Drawn when there is no graph to lay out (a detached instance, or a model with no included class):
     * a well-formed but empty payload, so the panel renders an empty canvas rather than failing on a
     * {@code null}.
     */
    private static final String EMPTY_GRAPH = "{\"layout\":\"uml\",\"nodes\":[],\"links\":[],\"legend\":[]}";

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

    /** The rendered diagram; {@code null} while it has not been produced (or when rendering failed). */
    private Blob diagram;

    /** The PlantUML source the diagram was rendered from — kept even when rendering failed. */
    private String plantUml;

    /**
     * The diagram as a {@link ClassDiagram.Graph} — the same boxes and connectors, assembled once by
     * {@link DomainModels} and handed to whichever renderer draws them.
     *
     * <p>Not a metamodel member (no accessor): it is the <em>input</em> to {@link #getD3Graph()}, which
     * lays it out and serialises it.  Kept rather than recomputed because the D3 payload is then
     * derived from the same snapshot the PDF was, so the two pictures of one page cannot disagree.</p>
     */
    private ClassDiagram.Graph graph;

    /** Why there is no diagram, when it could not be rendered; hidden otherwise. */
    private String error;

    @Title
    public String title() {
        final String model = domainModel != null ? domainModel.title() : "Domain model";
        return "UML class diagram: " + model + " — " + describeKind();
    }

    /**
     * The diagram as Kroki rendered it (PlantUML → SVG → PDF), shown inline by the viewer's pdf.js
     * panel — the property the whole change exists for.
     */
    @PdfJsViewer
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(
            named = "UML Class Diagram",
            describedAs = "The diagram as Kroki renders it (PlantUML -> SVG -> PDF)",
            sequence = "1")
    public Blob getDiagram() {
        ensureDerived();
        return diagram;
    }

    /**
     * The PlantUML the diagram was rendered from — the assembly pass measures this text, and it is
     * what a reviewer copies into PlantUML tooling when the picture is not enough.
     */
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(
            named = "PlantUML Source",
            describedAs = "The PlantUML source the diagram was rendered from",
            sequence = "2",
            multiLine = 20)
    public String getPlantUml() {
        ensureDerived();
        return plantUml;
    }

    /**
     * The same diagram as a D3 graph — the boxes and connectors the PDF shows, laid out offline and
     * serialised as the {@code {"layout":"uml",…}} payload the D3 panel draws statically.
     *
     * <p><b>Why a second renderer.</b>  The PDF path needs Kroki; this one does not, so the diagram is
     * still visible when the renderer is down, and it is drawn at a size the browser decides rather than
     * at the fixed page size a PDF has.  {@code @D3Graph} is a <em>property</em> facet, so the graph has
     * to be exposed as a property here rather than returned by an action.</p>
     *
     * <p>The layout is {@link UmlDiagramLayout}'s job, not this view model's: it assigns the bands, sizes
     * and widens the boxes, routes every connector orthogonally and emits the JSON.  It is a pure
     * function of the graph, so the same graph always produces the same picture.</p>
     */
    @D3Graph
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(
            named = "UML Class Diagram / D3",
            describedAs = "The same class diagram, laid out offline and drawn by the browser",
            sequence = "3")
    public String getD3Graph() {
        ensureDerived();
        return graph == null ? EMPTY_GRAPH : UmlDiagramLayout.render(graph.getTitle(), graph.getNodes(), graph.getEdges());
    }

    /** Why the picture is missing, when it could not be rendered; hidden otherwise. */
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(
            named = "Message",
            describedAs = "Set when the diagram could not be rendered (e.g. Kroki unavailable)",
            sequence = "3",
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

    /** Changing the memento key invalidates the cached diagram, so it is re-derived on the next render. */
    public void setKind(final String kind) {
        this.kind = kind;
        this.diagram = null;
        this.plantUml = null;
        this.graph = null;
        this.error = null;
    }

    /**
     * Re-derives the diagram on first access, if the caller did not already supply it (or a failure).
     *
     * <p>Needed because a view model reloaded from a bookmark — a page reload, or a link back to the
     * page — has only the memento's properties ({@link #domainModel}, {@link #kind},
     * {@link #subDomainName}); the rendered bytes are not part of a memento.  Without this the page
     * would show an empty diagram and no reason.</p>
     *
     * <p>{@link #plantUml} is part of the "already derived" test as well as {@link #diagram}, because the
     * two renderers are asked for independently: a page whose PDF failed still draws the D3 picture, and
     * the first of the two properties to be read must do the work for both.</p>
     */
    private void ensureDerived() {
        if (diagram == null && plantUml == null && domainModels != null && domainModel != null) {
            final UmlClassDiagram rederived = domainModels.umlClassDiagram(domainModel, kind, subDomainName);
            this.diagram = rederived.diagram;
            this.plantUml = rederived.plantUml;
            this.graph = rederived.graph;
            this.error = rederived.error;
        }
    }

    /** What this diagram shows, in words — the second half of {@link #title()}. */
    private String describeKind() {
        final String which = kind == null ? KIND_MODEL : kind;
        return switch (which) {
            case KIND_SUB_DOMAIN -> "sub-domain " + (subDomainName == null ? "(unnamed)" : subDomainName);
            case KIND_SUB_DOMAIN_OVERVIEW -> "sub-domain overview";
            default -> "whole model";
        };
    }

    /**
     * Factory used by {@link DomainModels}; the caller then supplies the source and either the
     * rendered picture or the failure reason via {@link #withSource}/{@link #rendered}/{@link #failed}.
     */
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

    /** Records the PlantUML source, whether or not it can be rendered. */
    UmlClassDiagram withSource(final String plantUml) {
        this.plantUml = plantUml;
        return this;
    }

    /**
     * Records the diagram's content as a graph, for the D3 renderer.
     *
     * <p>Applied <em>before</em> {@link #rendered}/{@link #failed}, so the D3 picture is available even
     * when the PDF render failed — it needs no Kroki.</p>
     */
    UmlClassDiagram withGraph(final ClassDiagram.Graph graph) {
        this.graph = graph;
        return this;
    }

    /** Records a successfully rendered diagram. */
    UmlClassDiagram rendered(final Blob diagram) {
        this.diagram = diagram;
        this.error = null;
        return this;
    }

    /** Records that the diagram could not be rendered; the source (if any) is kept. */
    UmlClassDiagram failed(final String reason) {
        this.diagram = null;
        this.error = reason;
        return this;
    }
}
