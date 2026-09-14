package domox.dom.rules;

import domox.DomainModule;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.SentenceRepository;
import domox.dom.nlp.TypedDependency;
import domox.dom.crc.Candidate;
import domox.dom.crc.ClassCandidates;
import domox.dom.crc.ClassCdd;
import domox.dom.crc.DomainModel;
import domox.dom.crc.PropertyCandidates;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DomainService
@Named(DomainModule.NAMESPACE + ".RuleMatches")
@Priority(PriorityPrecedence.EARLY)
@DomainServiceLayout(menuBar = DomainServiceLayout.MenuBar.PRIMARY)
public class RuleMatches {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final RuleMatchRepository ruleMatchRepository;
    private final SentenceRepository sentenceRepository;
    private final ClassCandidates classCandidates;
    private final PropertyCandidates propertyCandidates;

    @Inject
    public RuleMatches(
            RepositoryService repositoryService,
            FactoryService factoryService,
            RuleMatchRepository ruleMatchRepository,
            SentenceRepository sentenceRepository,
            ClassCandidates classCandidates,
            PropertyCandidates propertyCandidates) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.ruleMatchRepository = ruleMatchRepository;
        this.sentenceRepository = sentenceRepository;
        this.classCandidates = classCandidates;
        this.propertyCandidates = propertyCandidates;
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
        final RuleMatch obj = factoryService.detachedEntity(RuleMatch.class);
        obj.setTypedDependency(typedDependency);
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
     * Phase 2: Creates actual Candidate objects from all RuleMatch records.
     * After calling this, the processed RuleMatch records can be removed or marked as processed.
     */
    @Programmatic
    public List<Candidate> createCandidatesFromMatches() {
        return createCandidatesFrom(ruleMatchRepository.findAll());
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
        List<Candidate> candidates = new ArrayList<>();
        if (matches == null) {
            return candidates;
        }
        final Map<String, Candidate> createdClassCandidates = new HashMap<>();
        for (RuleMatch match : matches) {
            if (match == null) {
                continue;
            }
            // For classes (entities), only one candidate per name is created;
            // additional RuleMatches register on the existing candidate.
            if ("ClassCdd".equals(match.getCandidateType())) {
                final Candidate existing = createdClassCandidates.get(match.getCandidateName());
                if (existing != null) {
                    existing.addMatchingRule(match);
                    continue;
                }
            }
            final Candidate candidate = createCandidateFromMatch(match);
            if (candidate != null) {
                candidate.addMatchingRule(match);
                if (candidate instanceof ClassCdd) {
                    createdClassCandidates.put(candidate.getCandidateName(), candidate);
                }
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    @Programmatic
    public List<Candidate> createCandidatesFrom(final List<RuleMatch> matches, final DomainModel domainModel) {
        List<Candidate> candidates = new ArrayList<>();
        if (matches == null) {
            return candidates;
        }
        final Map<String, Candidate> createdClassCandidates = new HashMap<>();
        for (RuleMatch match : matches) {
            if (match == null) {
                continue;
            }
            // For classes (entities), only one candidate per name is created;
            // additional RuleMatches register on the existing candidate.
            if ("ClassCdd".equals(match.getCandidateType())) {
                final Candidate existing = createdClassCandidates.get(match.getCandidateName());
                if (existing != null) {
                    existing.addMatchingRule(match);
                    continue;
                }
            }
            final Candidate candidate = createCandidateFromMatch(match, domainModel);
            if (candidate != null) {
                candidate.addMatchingRule(match);
                if (candidate instanceof ClassCdd) {
                    createdClassCandidates.put(candidate.getCandidateName(), candidate);
                }
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    /**
     * Creates a single Candidate from a RuleMatch record.
     * Dispatches based on candidateType.
     */
    @Programmatic
    Candidate createCandidateFromMatch(RuleMatch match) {
        return createCandidateFromMatch(match, null);
    }

    @Programmatic
    Candidate createCandidateFromMatch(RuleMatch match, DomainModel domainModel) {
        String candidateType = match.getCandidateType();
        String candidateName = match.getCandidateName();
        String relatedCandidateName = match.getRelatedCandidateName();

        if ("ClassCdd".equals(candidateType)) {
            ClassCdd classCdd = classCandidates.findOrCreate(candidateName, domainModel);
            classCdd.setCandidateName(candidateName);
            return classCdd;
        } else if ("PropertyCdd".equals(candidateType)) {
            // The related candidate name should be the owning class name
            String className = relatedCandidateName != null ? relatedCandidateName : "Unknown";
            String type = inferType(candidateName);
            return propertyCandidates.findOrCreate(className, candidateName, type, domainModel);
        }
        return null;
    }

    /**
     * Infers the property type from the property name.
     */
    @Programmatic
    String inferType(String propertyName) {
        if (propertyName == null) return "String";
        String lower = propertyName.toLowerCase();
        if (lower.contains("count") || lower.contains("number") || lower.contains("age")) {
            return "int";
        } else if (lower.contains("price") || lower.contains("amount")) {
            return "double";
        } else if (lower.contains("active") || lower.contains("valid") || lower.contains("enabled")) {
            return "boolean";
        } else if (lower.contains("date") || lower.contains("time")) {
            return "LocalDateTime";
        }
        return "String";
    }

    //Actions for UI
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