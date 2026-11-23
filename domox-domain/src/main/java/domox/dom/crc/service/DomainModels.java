package domox.dom.crc.service;
import domox.DomainModule;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ModelContent;
import domox.dom.crc.model.ReviewRationale;
import generate.ClassDiagram;
import generate.JavaCode;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.MemberSupport;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.ArrayList;
import java.util.List;

/**
 * The assembled domain model: listing/creating a {@link DomainModel} and rendering it as PlantUML.
 *
 * <p><b>Every diagram is review-aware.</b>  The export renders only candidates the review approved,
 * and only classes that carry approved content — see {@link ClassDiagram} and
 * {@link #rejectEmptyClasses(DomainModel, String)}.</p>
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
     * The assembled model as PlantUML — the whole-model diagram.
     *
     * <p>Review-aware: only approved classes that carry approved content are drawn, and only the
     * approved members and material edges between them appear.  See {@link ClassDiagram#build}.</p>
     */
    public String generateUmlDiagram(DomainModel domainModel) {
        return new ClassDiagram().build(domainModel);
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
    public String generateSubDomainUmlDiagram(DomainModel domainModel, String subDomainName) {
        return new ClassDiagram().buildForSubDomain(domainModel, subDomainName);
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
     * partition is visible rather than silent.  See {@link ClassDiagram#buildSubDomainOverview}.</p>
     */
    @ActionLayout(
            sequence = "4",
            describedAs = "How the sub-domains relate: one node per sub-domain, one edge per relation between them")
    public String generateSubDomainOverviewDiagram(DomainModel domainModel) {
        return new ClassDiagram().buildSubDomainOverview(domainModel);
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