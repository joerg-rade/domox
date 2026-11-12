package domox.dom.rules;

import domox.DomainModule;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.SentenceRepository;
import domox.dom.nlp.TypedDependency;
import domox.dom.requirements.Document;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.DomainModel;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.Action;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.DomainServiceLayout;
import org.apache.causeway.applib.annotation.MemberSupport;
import org.apache.causeway.applib.annotation.ParameterLayout;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.annotation.SemanticsOf;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.List;

/**
 * The {@link RuleMatch} lifecycle: Phase 1 creates and de-duplicates a match record when a TDR rule
 * fires, and the read actions expose the accumulated matches in the UI.
 * <p>
 * The Phase-2 entry points ({@code createCandidatesFrom*}) are thin facades over
 * {@link CandidateFactory}, which owns the match-to-candidate mapping — that concern used to be part
 * of this class.
 */
@DomainService
@Named(DomainModule.NAMESPACE + ".RuleMatches")
@Priority(PriorityPrecedence.EARLY)
@DomainServiceLayout(menuBar = DomainServiceLayout.MenuBar.PRIMARY)
public class RuleMatches {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final RuleMatchRepository ruleMatchRepository;
    private final SentenceRepository sentenceRepository;
    private final NlpProperties nlpProperties;
    private final CandidateFactory candidateFactory;

    @Inject
    public RuleMatches(
            RepositoryService repositoryService,
            FactoryService factoryService,
            RuleMatchRepository ruleMatchRepository,
            SentenceRepository sentenceRepository,
            NlpProperties nlpProperties,
            CandidateFactory candidateFactory) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.ruleMatchRepository = ruleMatchRepository;
        this.sentenceRepository = sentenceRepository;
        this.nlpProperties = nlpProperties;
        this.candidateFactory = candidateFactory;
    }

    /**
     * Phase 1: Creates a RuleMatch record when a TDR rule matches a dependency.
     */
    @Programmatic
    public RuleMatch create(
            TypedDependency typedDependency,
            String ruleClassName,
            String candidateType,
            String candidateName, Object o, Object object, String result) {
        return create(typedDependency, ruleClassName, candidateType, candidateName, null, null, result);
    }

    /**
     * Phase 1: Creates a RuleMatch record with optional related candidate info.
     */
    @Programmatic
    public RuleMatch create(
            TypedDependency typedDependency,
            String ruleClassName,
            String candidateType,
            String candidateName,
            String relatedCandidateType,
            String relatedCandidateName,
            String description) {
        // Prevent persisting RuleMatch records for blocked use-case nouns
        // (e.g. "Actor", "Condition", "System") so they never appear as candidate
        // names, regardless of which TDR rule produced the match.
        if (candidateName != null
                && ("ClassCdd".equals(candidateType) || "GeneralizationCdd".equals(candidateType)
                || "SynonymCdd".equals(candidateType))
                && isBlockedUseCaseNoun(candidateName)) {
            return null;
        }
        // Deduplicate: if a RuleMatch with the same candidate + rule signature
        // (candidateName, candidateType, ruleClassName, description,
        // relatedCandidateName, relatedCandidateType) already exists, do not
        // persist another one — return the existing match instead.  This stops
        // the same TDR rule firing repeatedly for the same candidate from
        // producing duplicate rows in the UI.
        final RuleMatch existing = findExistingMatch(
                ruleClassName, candidateType, candidateName,
                relatedCandidateType, relatedCandidateName, description);
        if (existing != null) {
            return existing;
        }
        final RuleMatch obj = factoryService.detachedEntity(RuleMatch.class);
        obj.setTypedDependency(typedDependency);
        if (typedDependency != null) {
            typedDependency.getRuleMatches().add(obj);
        }
        obj.setRuleClassName(ruleClassName);
        obj.setCandidateType(candidateType);
        obj.setCandidateName(candidateName);
        obj.setRelatedCandidateType(relatedCandidateType);
        obj.setRelatedCandidateName(relatedCandidateName);
        obj.setDescription(description);
        repositoryService.persist(obj);
        return obj;
    }

    /**
     * Returns an existing RuleMatch with the same candidate + rule signature
     * as the incoming {@code create(...)} arguments, or {@code null} when none
     * exists.  The signature comprises {@code ruleClassName}, {@code candidateType},
     * {@code candidateName}, {@code relatedCandidateType},
     * {@code relatedCandidateName}, and {@code description}.
     */
    @Programmatic
    RuleMatch findExistingMatch(
            String ruleClassName,
            String candidateType,
            String candidateName,
            String relatedCandidateType,
            String relatedCandidateName,
            String description) {
        final List<RuleMatch> matches = ruleMatchRepository
                .findByCandidateNameAndCandidateTypeAndRuleClassNameAndDescriptionAndRelatedCandidateNameAndRelatedCandidateType(
                        candidateName, candidateType, ruleClassName,
                        description, relatedCandidateName, relatedCandidateType);
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        return matches.getFirst();
    }

    /**
     * Phase 2: Creates actual Candidate objects from all RuleMatch records.
     * After calling this, the processed RuleMatch records can be removed or marked as processed.
     * <p>
     * A thin facade over {@link CandidateFactory}: this service owns the {@link RuleMatch}
     * lifecycle, the factory owns the match-to-candidate mapping.
     */
    @Programmatic
    public List<Candidate> createCandidatesFromMatches() {
        return candidateFactory.createCandidatesFrom(ruleMatchRepository.findAll());
    }

    /**
     * Phase 2: Creates actual Candidate objects from the given RuleMatch records.
     * In the case of classes (entities), only one class with the same name is created.
     *
     * @param matches the RuleMatch records to process
     * @return the created (or re-used) Candidate objects
     */
    @Programmatic
    public List<Candidate> createCandidatesFrom(final List<RuleMatch> matches) {
        return candidateFactory.createCandidatesFrom(matches);
    }

    /**
     * Phase 2: Creates actual Candidate objects from the given RuleMatch records, bound to the
     * given {@link DomainModel}.
     *
     * @param matches     the RuleMatch records to process
     * @param domainModel the model the created candidates belong to
     * @return the created (or re-used) Candidate objects
     */
    @Programmatic
    public List<Candidate> createCandidatesFrom(final List<RuleMatch> matches, final DomainModel domainModel) {
        return candidateFactory.createCandidatesFrom(matches, domainModel);
    }

    /**
     * Returns {@code true} when {@code candidateName} is a use-case document meta-noun (template
     * header, narrative scaffolding, or generic result term) that must never become a candidate.
     * <p>
     * Delegates to {@link UseCaseBlockedNouns} so this persistence guard and the Phase-2
     * {@link CandidateFactory} agree on exactly which nouns are blocked.
     */
    @Programmatic
    boolean isBlockedUseCaseNoun(String candidateName) {
        return UseCaseBlockedNouns.isBlocked(nlpProperties, candidateName);
    }

    /**
     * Returns the {@link RuleMatch} records produced for the given document —
     * i.e. those whose typed dependency belongs to one of the document's
     * sentences.  Scoping the Phase-2 read to the current document avoids
     * re-scanning every match accumulated by the whole corpus on each analysis
     * pass (the previous {@code listAll()} re-processed the full corpus set per
     * document, making total load time quadratic in the document count).
     */
    @Programmatic
    public List<RuleMatch> listAllForDocument(final Document document) {
        return ruleMatchRepository.findByTypedDependency_SentenceIn(document.getSentences());
    }

    //Actions for UI
    @ActionLayout(sequence = "1")
    public List<RuleMatch> listAll() {
        return ruleMatchRepository.findAll();
    }

    public List<RuleMatch> findByRuleClassName(String ruleClassName) {
        return ruleMatchRepository.findByRuleClassName(ruleClassName);
    }

    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout(sequence = "2.5", named = "List By Sentence")
    public List<RuleMatch> listBySentence(
            @ParameterLayout(named = "Sentence")
            final Sentence sentence) {
        return ruleMatchRepository.findByTypedDependency_Sentence(sentence);
    }

    // Provide choices for the 'sentence' parameter
    @MemberSupport
    public List<Sentence> choices0ListBySentence() {
        return sentenceRepository.findAll();
    }

    public void deleteAll() {
        ruleMatchRepository.deleteAll();
    }
}