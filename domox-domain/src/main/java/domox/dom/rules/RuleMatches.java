package domox.dom.rules;

import domox.DomainModule;
import domox.dom.nlp.TypedDependency;
import domox.dom.uml.Candidate;
import domox.dom.uml.ClassCandidates;
import domox.dom.uml.ClassCdd;
import domox.dom.uml.PropertyCandidates;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.DomainServiceLayout;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.ArrayList;
import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".RuleMatches")
@Priority(PriorityPrecedence.EARLY)
@DomainServiceLayout(menuBar = DomainServiceLayout.MenuBar.PRIMARY)
public class RuleMatches {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final RuleMatchRepository ruleMatchRepository;
    private final ClassCandidates classCandidates;
    private final PropertyCandidates propertyCandidates;

    @Inject
    public RuleMatches(
            RepositoryService repositoryService,
            FactoryService factoryService,
            RuleMatchRepository ruleMatchRepository,
            ClassCandidates classCandidates,
            PropertyCandidates propertyCandidates) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.ruleMatchRepository = ruleMatchRepository;
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
        List<Candidate> candidates = new ArrayList<>();
        List<RuleMatch> matches = ruleMatchRepository.findAll();

        for (RuleMatch match : matches) {
            Candidate candidate = createCandidateFromMatch(match);
            if (candidate != null) {
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
        String candidateType = match.getCandidateType();
        String candidateName = match.getCandidateName();
        String relatedCandidateName = match.getRelatedCandidateName();

        if ("ClassCdd".equals(candidateType)) {
            ClassCdd classCdd = classCandidates.findOrCreate(candidateName);
            classCdd.setName(candidateName);
            return classCdd;
        } else if ("PropertyCdd".equals(candidateType)) {
            // The related candidate name should be the owning class name
            String className = relatedCandidateName != null ? relatedCandidateName : "Unknown";
            String type = inferType(candidateName);
            return propertyCandidates.findOrCreate(className, candidateName, type);
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

    public List<RuleMatch> listAll() {
        return ruleMatchRepository.findAll();
    }

    public List<RuleMatch> findByRuleClassName(String ruleClassName) {
        return ruleMatchRepository.findByRuleClassName(ruleClassName);
    }
}