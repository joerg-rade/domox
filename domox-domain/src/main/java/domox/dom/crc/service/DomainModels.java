package domox.dom.crc.service;
import domox.DomainModule;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ModelContent;
import domox.dom.crc.model.ReviewRationale;
import domox.port.DomainConstants;
import generate.ClassDiagram;
import generate.JavaCode;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.MemberSupport;
import org.apache.causeway.applib.annotation.Optionality;
import org.apache.causeway.applib.annotation.Parameter;
import org.apache.causeway.applib.annotation.ParameterLayout;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.value.Blob;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The assembled domain model: listing/creating a {@link DomainModel}, rendering it as a class
 * diagram, and exporting that diagram's PlantUML source.
 *
 * <p><b>Every diagram is review-aware.</b>  The export renders only candidates the review approved,
 * and only classes that carry approved content — see {@link ClassDiagram} and
 * {@link #rejectEmptyClasses(DomainModel, String)}.</p>
 *
 * <p><b>A diagram is a picture, and the picture is the browser's.</b>  Each diagram action returns a
 * {@link UmlClassDiagram} view model whose diagram is the D3 panel's — the boxes and connectors the
 * domain assembles, laid out by {@code UmlDiagramLayout}.  Nothing is sent to an external renderer, so
 * the page has no third-party dependency and no failure mode that replaces the diagram with a message.</p>
 *
 * <p><b>The PlantUML is a download.</b>  {@link #downloadPlantUml(DomainModel, String, String)} serves the
 * same three diagrams as text: it is the artifact the assembly pass measures (class/attribute/operation/edge
 * counts) and what a reviewer pastes into PlantUML tooling, and a file is the right shape for it — a page
 * is for the picture.</p>
 */
@DomainService
@Named(DomainModule.NAMESPACE + ".DomainModels")
@Priority(PriorityPrecedence.EARLY)
public class DomainModels {

    private final RepositoryService repositoryService;
    private final Reviews reviews;

    @Inject
    public DomainModels(RepositoryService repositoryService, Reviews reviews) {
        this.repositoryService = repositoryService;
        this.reviews = reviews;
    }

    @ActionLayout(sequence = "1")
    public List<DomainModel> listAll() {
        return repositoryService.allInstances(DomainModel.class);
    }

    @ActionLayout(sequence = "2")
    public DomainModel create() {
        final DomainModel obj = new DomainModel();
        repositoryService.persist(obj);
        return obj;
    }

    /**
     * The assembled model's class diagram — the whole-model diagram.
     *
     * <p>Review-aware: only approved classes that carry approved content are drawn, and only the
     * approved members and material edges between them appear.  See {@link ClassDiagram#graph}.</p>
     */
    public UmlClassDiagram generateUmlDiagram(DomainModel domainModel) {
        return umlClassDiagram(domainModel, UmlClassDiagram.KIND_MODEL, null);
    }

    /**
     * One sub-domain's diagram — the "one diagram per sub-domain" of the assembly pass.
     *
     * <p>Draws the approved classes assigned to {@code subDomainName} and the material edges wholly
     * inside that sub-domain; relations that leave it are shown by
     * {@link #generateSubDomainOverviewDiagram(DomainModel)} instead.</p>
     *
     * @param subDomainName the sub-domain's name, matched case-insensitively; the choices come from
     *                      {@link #choices1GenerateSubDomainUmlDiagram(DomainModel)}
     */
    @ActionLayout(
            sequence = "3",
            describedAs = "The diagram of one sub-domain: its approved classes and the relations inside it")
    public UmlClassDiagram generateSubDomainUmlDiagram(DomainModel domainModel, String subDomainName) {
        return umlClassDiagram(domainModel, UmlClassDiagram.KIND_SUB_DOMAIN, subDomainName);
    }

    /** The sub-domains that have a diagram to draw — the choices for the action above. */
    @MemberSupport
    public List<String> choices1GenerateSubDomainUmlDiagram(DomainModel domainModel) {
        return new ClassDiagram().subDomainNames(domainModel);
    }

    /**
     * The overview diagram: the sub-domains as nodes, their cross-sub-domain relations as edges.
     *
     * <p>Each edge is labelled with the number of collaborations it aggregates.  A class with no
     * sub-domain is drawn as one {@code Unassigned} node rather than dropped, so an incomplete
     * partition is visible rather than silent.  See {@link ClassDiagram#graphOverview}.</p>
     */
    @ActionLayout(
            sequence = "4",
            describedAs = "How the sub-domains relate: one node per sub-domain, one edge per relation between them")
    public UmlClassDiagram generateSubDomainOverviewDiagram(DomainModel domainModel) {
        return umlClassDiagram(domainModel, UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW, null);
    }

    /**
     * The PlantUML source of one of the three diagrams, as a file — the text export.
     *
     * <p><b>Why a download rather than a page member.</b>  The diagram page used to carry this text on a
     * tab of its own (a 20-line textarea holding a whole model's source — 472 lines for the 970-class
     * model — which is why it was a tab and not a row).  The page now shows the picture only: a page is
     * for the picture, and a file is the right shape for a source.  The artifact itself still matters —
     * it is what the assembly pass measures (class/attribute/operation/edge counts) and what a reviewer
     * pastes into PlantUML tooling — so it is served rather than dropped.</p>
     *
     * <p>Review-aware like the pictures: {@link ClassDiagram} assembles it from the same class list and
     * the same collaboration list, so the text and the diagram cannot disagree about what the model
     * contains.</p>
     *
     * @param kind          one of the {@code UmlClassDiagram.KIND_*} constants; blank reads as the whole
     *                      model, so the common case needs no parameter
     * @param subDomainName the sub-domain, for {@code KIND_SUB_DOMAIN} only; ignored otherwise
     */
    @ActionLayout(
            sequence = "5",
            describedAs = "Download the PlantUML source of one of the diagrams (the whole model by default)")
    public Blob downloadPlantUml(
            final DomainModel domainModel,
            @Parameter(optionality = Optionality.OPTIONAL)
            @ParameterLayout(
                    named = "Diagram",
                    describedAs = "Which diagram: model (the default), sub-domain, or sub-domain-overview")
            final String kind,
            @Parameter(optionality = Optionality.OPTIONAL)
            @ParameterLayout(
                    named = "Sub-Domain",
                    describedAs = "The sub-domain, for the sub-domain diagram only; leave blank otherwise")
            final String subDomainName) {
        final String which = kind == null || kind.isBlank() ? UmlClassDiagram.KIND_MODEL : kind;
        final String source = plantUmlOf(domainModel, which, subDomainName);
        return new Blob(plantUmlFileNameOf(which, subDomainName), DomainConstants.PLANT_UML_MIME_TYPE,
                source.getBytes(StandardCharsets.UTF_8));
    }

    /** The diagram kinds {@link #downloadPlantUml} can export — the choices for its {@code kind} parameter. */
    @MemberSupport
    public List<String> choices1DownloadPlantUml(final DomainModel domainModel) {
        return List.of(UmlClassDiagram.KIND_MODEL, UmlClassDiagram.KIND_SUB_DOMAIN,
                UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW);
    }

    /**
     * Builds one class diagram's view model — the three diagram actions' entry point.
     *
     * <p>This is also the re-derivation entry point for a view model rebuilt from its memento (see
     * {@link UmlClassDiagram}), so the diagram survives a page reload.</p>
     *
     * @param kind          one of the {@code UmlClassDiagram.KIND_*} constants
     * @param subDomainName the sub-domain, for {@code KIND_SUB_DOMAIN} only; ignored otherwise
     */
    @Programmatic
    public UmlClassDiagram umlClassDiagram(
            final DomainModel domainModel,
            final String kind,
            final String subDomainName) {
        return UmlClassDiagram.of(domainModel, kind, subDomainName)
                .withGraph(graphOf(domainModel, kind, subDomainName));
    }

    /** Assembles the PlantUML for one kind of diagram — the text {@link #downloadPlantUml} serves. */
    private String plantUmlOf(final DomainModel domainModel, final String kind, final String subDomainName) {
        final ClassDiagram classDiagram = new ClassDiagram();
        final String which = kind == null ? UmlClassDiagram.KIND_MODEL : kind;
        return switch (which) {
            case UmlClassDiagram.KIND_SUB_DOMAIN -> classDiagram.buildForSubDomain(domainModel, subDomainName);
            case UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW -> classDiagram.buildSubDomainOverview(domainModel);
            default -> classDiagram.build(domainModel);
        };
    }

    /**
     * Assembles one diagram as a {@link ClassDiagram.Graph} — what the D3 panel draws.
     *
     * <p>It reads the same class list and the same collaboration list as the PlantUML text, so the
     * picture and the download cannot disagree about <em>what</em> the model contains; they differ only
     * in how it is drawn, {@code UmlDiagramLayout#render} laying the picture out.</p>
     */
    private ClassDiagram.Graph graphOf(
            final DomainModel domainModel, final String kind, final String subDomainName) {
        final ClassDiagram classDiagram = new ClassDiagram();
        final String which = kind == null ? UmlClassDiagram.KIND_MODEL : kind;
        return switch (which) {
            case UmlClassDiagram.KIND_SUB_DOMAIN -> classDiagram.graphForSubDomain(domainModel, subDomainName);
            case UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW -> classDiagram.graphOverview(domainModel);
            default -> classDiagram.graph(domainModel);
        };
    }

    /** The download name of one diagram's PlantUML — the kind and the sub-domain, so two exports differ. */
    private String plantUmlFileNameOf(final String kind, final String subDomainName) {
        final String which = kind == null ? UmlClassDiagram.KIND_MODEL : kind;
        return switch (which) {
            case UmlClassDiagram.KIND_SUB_DOMAIN ->
                    "domain-model-" + (subDomainName == null ? "sub-domain" : subDomainName) + ".puml";
            case UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW -> "domain-model-sub-domains.puml";
            default -> "domain-model.puml";
        };
    }

    /**
     * Rejects every approved class of the model that the assembled model does not contain — no
     * approved attribute and no approved responsibility of its own, and no material relation touching
     * it on either end ({@link ModelContent#emptyClasses(DomainModel)}).
     *
     * <p><b>Why reject rather than merely omit.</b>  The export already leaves such a class out of
     * every diagram ({@link ModelContent#emptyClasses(DomainModel)}), but leaving the review saying
     * {@code APPROVED} would make the review and the model disagree: the pipeline would report a
     * class the model does not contain, and the next re-measure would count it.  Rejecting it keeps
     * "approved" and "in the model" the same statement.</p>
     *
     * <p>The rationale is fixed at {@link ReviewRationale#INSUFFICIENT_INFORMATION}: the class itself
     * may well be a real concept of the business, but the extraction produced nothing to model it
     * with, which is exactly what that category means.  (It is <em>not</em> {@code NOT_RELEVANT} —
     * that would claim the concept is irrelevant.)</p>
     *
     * <p>Idempotent: a class already rejected is not approved, so a re-run finds nothing to do.
     * Blank {@code reason}s are the caller's problem — the MCP boundary refuses them.</p>
     *
     * @param reason why the sweep is being run, stored on every candidate it rejects
     * @return the classes that were rejected, in model order
     */
    @Programmatic
    public List<ClassCdd> rejectEmptyClasses(DomainModel domainModel, String reason) {
        final List<ClassCdd> rejected = new ArrayList<>();
        for (final ClassCdd clazz : ModelContent.emptyClasses(domainModel)) {
            reviews.rejectAsAgent(clazz, ReviewRationale.INSUFFICIENT_INFORMATION, reason);
            rejected.add(clazz);
        }
        return rejected;
    }

    /**
     * {@link #rejectEmptyClasses(DomainModel, String)} over <em>every</em> model — what the assembly
     * pass runs, because the pass is driven from the MCP tool surface, which addresses candidates by
     * id and has no convenient way to name a model.
     *
     * @return every class rejected across all models, in model order
     */
    @Programmatic
    public List<ClassCdd> rejectEmptyClassesInAllModels(String reason) {
        final List<ClassCdd> rejected = new ArrayList<>();
        for (final DomainModel model : listAll()) {
            rejected.addAll(rejectEmptyClasses(model, reason));
        }
        return rejected;
    }

    public String generateJavaCode(ClassCdd clazz) {
        return new JavaCode(clazz).getCode();
    }

}