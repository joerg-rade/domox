package domox.dom.requirements;

import domox.DomainModule;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.DomainModel;
import domox.dom.rules.RuleMatches;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.ArrayList;
import java.util.List;

/**
 * The <b>corpus-deletion</b> service: removes a {@link Corpus} and unwinds everything it owns.
 *
 * <p><b>Why it is its own service.</b>  Deleting a corpus is not a plain row delete — it is a
 * <em>cascade policy</em>: the order in which the corpus, its documents and the candidates of its
 * shared {@link DomainModel} are removed is load-bearing, and it changes when the mapping or the
 * deletion strategy changes — not when the corpus entity does.  Keeping it apart leaves
 * {@link Corpora} a pure aggregate-CRUD service.</p>
 *
 * <p><b>Why the order matters.</b>  Every document of a corpus points at its shared {@code DomainModel}
 * via the {@code domain_model_id} FK, so removing the corpus does not cascade-delete the candidates on
 * its own.  EclipseLink can issue the {@code DELETE} on the {@code DomainModel} before the
 * still-referencing children are gone, tripping the DB FK constraints (e.g.
 * {@code FK_ResponsibilityCdd_DOMAINMODEL_ID}).  So the candidates are removed explicitly, child-first
 * (associations → responsibilities → attributes → classes), before the documents, the corpus, and
 * finally the rule-match records.</p>
 */
@Named(DomainModule.NAMESPACE + ".CorpusDeletion")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class CorpusDeletion {

    private final RepositoryService repositoryService;
    private final RuleMatches ruleMatches;

    @Inject
    public CorpusDeletion(RepositoryService repositoryService,
                          RuleMatches ruleMatches) {
        this.repositoryService = repositoryService;
        this.ruleMatches = ruleMatches;
    }

    @Action()
    @ActionLayout(sequence = "1", cssClassFa = "trash")
    public void deleteCorpus(
            @ParameterLayout(named = "Corpus") final Corpus corpus) {
        // Every document of a corpus points at its shared DomainModel via the
        // domain_model_id FK, so removing the corpus no longer cascade-deletes the
        // candidates on its own.
        //
        // FIRST explicitly remove every candidate owned by the shared model,
        // child-first (associations -> actions/properties -> classes).  The orphan
        // rows (e.g. ResponsibilityCdd with classCdd = null) live only in the model's own
        // collections; relying solely on the model's @OneToMany(cascade = ALL) to
        // cascade-delete those lazy collections is unreliable — EclipseLink can
        // issue the DELETE on the DomainModel before the still-referencing children
        // are gone, tripping the DB FK constraints
        // (e.g. FK_ResponsibilityCdd_DOMAINMODEL_ID).  Registering each candidate for
        // removal explicitly — in child-before-parent order — guarantees the FK
        // constraints cannot fire.
        final DomainModel domainModel = corpus.getDomainModel();
        if (domainModel != null) {
            removeCandidates(domainModel.getCollaboratorList());
            removeCandidates(domainModel.getResponsibilityList());
            removeCandidates(domainModel.getAttributeList());
            removeCandidates(domainModel.getClassList());
            corpus.setDomainModel(null);
        }
        // Then drop this corpus's documents (releasing their FK into the shared
        // model), remove the now-empty corpus, and finally purge any remaining
        // rule-match records.
        for (final Document document : corpus.getDocuments()) {
            repositoryService.remove(document);
        }
        repositoryService.remove(corpus);
        ruleMatches.deleteAll();
    }

    /**
     * Registers every element of {@code candidates} for removal with the
     * {@link RepositoryService}, snapshotting the list first so the iteration is
     * unaffected by EclipseLink clearing managed collections during the delete.
     */
    private void removeCandidates(final List<? extends Candidate> candidates) {
        for (final Candidate candidate : new ArrayList<>(candidates)) {
            repositoryService.remove(candidate);
        }
    }

    /**
     * Selectable corpora offered as choices for {@link #deleteCorpus(Corpus)}.
     */
    @MemberSupport
    public List<Corpus> choices0DeleteCorpus() {
        return repositoryService.allInstances(Corpus.class);
    }
}
