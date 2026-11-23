package domox.dom.crc.service;
import domox.DomainModule;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ModelContent;
import domox.dom.crc.model.ReviewRationale;
import domox.port.DiagramPort;
import domox.port.DomainConstants;
import generate.ClassDiagram;
import generate.JavaCode;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.MemberSupport;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.value.Blob;

import java.util.ArrayList;
import java.util.List;

/**
 * The assembled domain model: listing/creating a {@link DomainModel} and rendering it as a class
 * diagram.
 *
 * <p><b>Every diagram is review-aware.</b>  The export renders only candidates the review approved,
 * and only classes that carry approved content — see {@link ClassDiagram} and
 * {@link #rejectEmptyClasses(DomainModel, String)}.</p>
 *
 * <p><b>The diagrams are rendered, not returned as source.</b>  Each action returns a
 * {@link UmlClassDiagram} view model: the PlantUML the {@link ClassDiagram} assembles, rendered by
 * Kroki (SVG) and transcoded to PDF, which the viewer shows through its pdf.js panel.  A plain
 * {@code String} result would be displayed as text — as one long line — because an action result is
 * rendered through the viewer's COMPACT label fragment.  The PlantUML itself is still carried by the
 * view model ({@link UmlClassDiagram#getPlantUml()}), because the assembly pass measures it.</p>
 */
@Slf4j
@DomainService
@Named(DomainModule.NAMESPACE + ".DomainModels")
@Priority(PriorityPrecedence.EARLY)
public class DomainModels {

    private final RepositoryService repositoryService;
    private final Reviews reviews;
    private final DiagramPort diagramPort;

    @Inject
    public DomainModels(RepositoryService repositoryService, Reviews reviews, DiagramPort diagramPort) {
        this.repositoryService = repositoryService;
        this.reviews = reviews;
        this.diagramPort = diagramPort;
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
     * The assembled model's class diagram — the whole-model diagram, rendered.
     *
     * <p>Review-aware: only approved classes that carry approved content are drawn, and only the
     * approved members and material edges between them appear.  See {@link ClassDiagram#build}.</p>
     */
    public UmlClassDiagram generateUmlDiagram(DomainModel domainModel) {
        return umlClassDiagram(domainModel, UmlClassDiagram.KIND_MODEL, null);
    }

    /**
     * One sub-domain's diagram — the "one diagram per sub-domain" of the assembly pass, rendered.
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
     * The overview diagram, rendered: the sub-domains as nodes, their cross-sub-domain relations as
     * edges.
     *
     * <p>Each edge is labelled with the number of collaborations it aggregates.  A class with no
     * sub-domain is drawn as one {@code Unassigned} node rather than dropped, so an incomplete
     * partition is visible rather than silent.  See {@link ClassDiagram#buildSubDomainOverview}.</p>
     */
    @ActionLayout(
            sequence = "4",
            describedAs = "How the sub-domains relate: one node per sub-domain, one edge per relation between them")
    public UmlClassDiagram generateSubDomainOverviewDiagram(DomainModel domainModel) {
        return umlClassDiagram(domainModel, UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW, null);
    }

    /**
     * Builds one class diagram and renders it, wrapped in the {@link UmlClassDiagram} view model the
     * three actions return.
     *
     * <p>The PlantUML is assembled here — review-aware, by {@link ClassDiagram} — and handed to the
     * {@link DiagramPort}, which owns only the rendering (Kroki → SVG → PDF).  A rendering failure
     * (Kroki down, PlantUML rejected) is recorded <em>on the view model</em> rather than thrown: the
     * PlantUML is still delivered, and the page says why the picture is missing.  This is also the
     * re-derivation entry point for a view model rebuilt from its memento (see
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
        final String plantUml = plantUmlOf(domainModel, kind, subDomainName);
        final UmlClassDiagram diagram = UmlClassDiagram.of(domainModel, kind, subDomainName).withSource(plantUml);
        try {
            final byte[] pdf = diagramPort.buildPlantUmlDiagram(plantUml);
            return diagram.rendered(new Blob(fileNameOf(kind, subDomainName), DomainConstants.PDF_MIME_TYPE, pdf));
        } catch (Exception e) {
            // Catch Exception (not just RuntimeException): the adapter's HTTP client throws checked
            // errors (Fuel's FuelError), so an unreachable Kroki must not fail the action.
            log.warn("Failed to render the {} class diagram: {}", kind, e.getMessage());
            log.debug("Failure rendering the {} class diagram", kind, e);
            return diagram.failed("The diagram could not be rendered (" + e.getMessage()
                    + ") — is Kroki running? The PlantUML source is below.");
        }
    }

    /** Assembles the PlantUML for one kind of diagram. */
    private String plantUmlOf(final DomainModel domainModel, final String kind, final String subDomainName) {
        final ClassDiagram classDiagram = new ClassDiagram();
        final String which = kind == null ? UmlClassDiagram.KIND_MODEL : kind;
        return switch (which) {
            case UmlClassDiagram.KIND_SUB_DOMAIN -> classDiagram.buildForSubDomain(domainModel, subDomainName);
            case UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW -> classDiagram.buildSubDomainOverview(domainModel);
            default -> classDiagram.build(domainModel);
        };
    }

    /** The download name of a rendered diagram — the kind and the sub-domain, so two PDFs differ. */
    private String fileNameOf(final String kind, final String subDomainName) {
        final String which = kind == null ? UmlClassDiagram.KIND_MODEL : kind;
        return switch (which) {
            case UmlClassDiagram.KIND_SUB_DOMAIN ->
                    "domain-model-" + (subDomainName == null ? "sub-domain" : subDomainName) + ".pdf";
            case UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW -> "domain-model-sub-domains.pdf";
            default -> "domain-model.pdf";
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