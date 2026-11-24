package domox.dom.crc.service;
import domox.DomainModule;
import domox.dom.crc.model.DomainModel;
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
import org.apache.causeway.applib.annotation.Nature;
import org.apache.causeway.applib.annotation.Optionality;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.annotation.Title;
import org.apache.causeway.applib.annotation.Where;
import org.apache.causeway.extensions.d3.applib.annotations.D3Graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * View model returned by {@link DomainModels#showUmlDiagrams(DomainModel)}: <b>every</b> class diagram of
 * the model at once, one per tab — the sub-domain overview, one diagram per sub-domain, and the whole
 * model.
 *
 * <p><b>One page instead of one page per sub-domain.</b>  The three single-diagram actions of
 * {@link DomainModels} each show one diagram, so reading a ten-sub-domain model meant opening ten pages.
 * This view model is the same content as <em>one</em> page whose tabs are the sub-domains: the reader
 * switches tabs instead of going back to the menu.</p>
 *
 * <p><b>The tab labels are the sub-domain names, and that is why this page has a Wicket panel of its
 * own.</b>  A Causeway tab label comes from the layout XML ({@code BSTab} carries nothing but a name, and
 * {@code TabGroupPanel} labels each tab from it), and one layout serves every instance of a view model —
 * so the sub-domains, which are <em>data</em>, cannot become layout tabs.  The names this view model
 * exposes ({@link #tabNames()}) are rendered by {@code domox.webapp.config.UmlDiagramTabsPanel}, which
 * builds one {@code AjaxBootstrapTabbedPanel} tab per name.  For the same reason
 * {@link UmlClassDiagram} carries its sub-domain in a <em>property label</em> rather than a tab.</p>
 *
 * <p><b>Each diagram is assembled when its tab is shown, not when the page is.</b>  Assembling one
 * diagram reads the whole model, and the whole model is 970 classes: eager assembly of a ten-sub-domain
 * set measured ~2 s <em>per diagram</em>, so the page would take ~18 s to appear.  {@link #payloadOf}
 * therefore assembles on demand and caches the result, and the panel asks for a payload from inside
 * {@code ITab#getPanel} — which Wicket calls for the shown tab only.</p>
 */
@DomainObject(nature = Nature.VIEW_MODEL)
@Named(DomainModule.NAMESPACE + ".UmlDiagramSet")
@DomainObjectLayout(cssClassFa = "project-diagram")
@NoArgsConstructor
public class UmlDiagramSet {

    /**
     * Label of the overview tab — the sub-domains as nodes, the map that orients the reader, and
     * therefore the tab the page opens on.
     */
    public static final String TAB_SUB_DOMAIN_OVERVIEW = "Sub-domain overview";

    /** Label of the last tab: the whole model, flat — the firehose, kept for completeness. */
    public static final String TAB_WHOLE_MODEL = "Whole model";

    /**
     * Drawn when there is no model to draw (a detached instance): a well-formed but empty payload, so the
     * panel renders an empty canvas rather than failing on a {@code null}.  Same payload
     * {@link UmlClassDiagram} falls back to.
     */
    private static final String EMPTY_GRAPH = "{\"layout\":\"uml\",\"nodes\":[],\"links\":[],\"legend\":[]}";

    /**
     * The model every tab is drawn from.
     *
     * <p>Kept as a metamodel property (rather than a plain field) so the view-model memento round-trips
     * through a bookmark — the same shape as {@link UmlClassDiagram#domainModel}, and for the same reason:
     * Causeway mementoes the <em>settable</em> properties.</p>
     */
    @Getter
    @Setter
    @PropertyLayout(named = "Model", hidden = Where.EVERYWHERE)
    private DomainModel domainModel;

    /**
     * The assembler, used to build one tab's diagram on demand.
     *
     * <p>Handed over by the action (see {@link #of(DomainModel, DomainModels)}) <em>and</em> injected, so
     * the view model works both on the request that created it and after a reload rebuilt it from its
     * memento.  Hidden from the metamodel: an injected collaborator is not a property (see
     * {@code CODING_STANDARDS.md} §2).</p>
     */
    @Inject
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private DomainModels domainModels;

    /**
     * The tab labels, in the order the page shows them — derived once, from the model.
     *
     * <p>Not a metamodel member: it is the <em>input</em> to the panel, which renders one tab per entry.
     * The order is the reader's order: the map first (the overview), then the sub-domains in name order,
     * then the whole model last — the one diagram nobody reads top to bottom.</p>
     */
    private List<String> tabNames;

    /**
     * One payload per tab label — the cache behind {@link #payloadOf(String)}.
     *
     * <p>Kept because a tab can be re-rendered (a page re-render recreates the shown tab's panel) and
     * assembling a diagram reads the whole model; the payload is a pure function of the model and the
     * review decisions, so caching it for the life of the page cannot go stale.</p>
     */
    private final Map<String, String> payloads = new HashMap<>();

    @Title
    public String title() {
        final String model = domainModel != null ? domainModel.title() : "Domain model";
        return "UML class diagrams: " + model;
    }

    /**
     * The tabs the page shows: the sub-domain overview, one per sub-domain (in name order), then the
     * whole model.
     *
     * <p>The sub-domains are the ones that would produce a non-empty diagram
     * ({@link ClassDiagram#subDomainNames(DomainModel)}), so no tab is empty; classes with no sub-domain
     * appear as the {@code Unassigned} node of the overview instead.  A detached instance (no model) has
     * no sub-domain to name, so it offers the two labels it can always draw.</p>
     */
    @Programmatic
    public List<String> tabNames() {
        if (tabNames == null) {
            final List<String> names = new ArrayList<>();
            names.add(TAB_SUB_DOMAIN_OVERVIEW);
            if (domainModel != null) {
                names.addAll(new ClassDiagram().subDomainNames(domainModel));
            }
            names.add(TAB_WHOLE_MODEL);
            this.tabNames = names;
        }
        return tabNames;
    }

    /**
     * The D3 payload of one tab — the {@code {"layout":"uml","force":true,…}} JSON the D3 panel draws.
     *
     * <p>Assembled on first request and cached (see {@link #payloads}): the page asks for one tab's payload
     * when that tab is shown, so opening the page costs one diagram, not all of them.</p>
     *
     * @param tabName one of {@link #tabNames()}; any other name is read as a sub-domain name
     */
    @Programmatic
    public String payloadOf(final String tabName) {
        return payloads.computeIfAbsent(tabName, this::assemble);
    }

    /**
     * The whole model's diagram as a property — what the page shows if its tab panel is not used.
     *
     * <p>The tab panel renders this object on the Wicket viewer
     * ({@code domox.webapp.config.UmlDiagramTabsPanelComponentFactory} applies exclusively for this type),
     * so on that path this property is never read.  It is here so the view model is not <em>only</em> a
     * carrier for a custom panel: the fallback grid shows the whole-model diagram, and the RestfulObjects
     * API can read the payload.  It is the same payload as the last tab, so the two cannot disagree.</p>
     */
    @D3Graph
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(
            named = "Diagram",
            describedAs = "The whole model's class diagram; the page shows every diagram as a tab instead",
            sequence = "1")
    public String getWholeModelGraph() {
        return payloadOf(TAB_WHOLE_MODEL);
    }

    /** The diagram one tab stands for — which kind of diagram, and of which sub-domain. */
    private String assemble(final String tabName) {
        if (domainModel == null) {
            return EMPTY_GRAPH;
        }
        if (TAB_SUB_DOMAIN_OVERVIEW.equals(tabName)) {
            return payloadOfKind(UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW, null);
        }
        if (TAB_WHOLE_MODEL.equals(tabName)) {
            return payloadOfKind(UmlClassDiagram.KIND_MODEL, null);
        }
        return payloadOfKind(UmlClassDiagram.KIND_SUB_DOMAIN, tabName);
    }

    /**
     * One diagram's payload, through the same {@link UmlClassDiagram} view model the single-diagram actions
     * return — so the picture on a tab and the picture on that action's own page are the same picture.
     */
    private String payloadOfKind(final String kind, final String subDomainName) {
        return domainModels.umlClassDiagram(domainModel, kind, subDomainName).getD3Graph();
    }

    /** Factory used by {@link DomainModels}; the assembler is handed over rather than only injected. */
    static UmlDiagramSet of(final DomainModel domainModel, final DomainModels domainModels) {
        final UmlDiagramSet viewModel = new UmlDiagramSet();
        viewModel.domainModel = domainModel;
        viewModel.domainModels = domainModels;
        return viewModel;
    }
}
