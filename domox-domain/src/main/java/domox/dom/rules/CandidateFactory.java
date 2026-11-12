package domox.dom.rules;

import domox.DomainModule;
import domox.dom.crc.model.AssociationType;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.service.AttributeCandidates;
import domox.dom.crc.service.ClassCandidates;
import domox.dom.crc.service.CollaboratorCandidates;
import domox.dom.crc.service.ResponsibilityCandidates;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 2 of the analysis pipeline: turns persisted {@link RuleMatch} records into the
 * {@link Candidate} entities the UI shows.
 * <p>
 * This concern used to live inside {@link RuleMatches} — a repository/record-creation service that
 * had accreted a full candidate factory (~170 lines: the per-type dispatch over
 * {@code ClassCdd}/{@code GeneralizationCdd}/{@code SynonymCdd}/{@code ActionCdd}/{@code PropertyCdd},
 * the blocked-noun guard, and the property-type inference).  Splitting it out leaves
 * {@code RuleMatches} owning only the {@code RuleMatch} lifecycle (persist, de-duplicate, query),
 * and gives the mapping from a rule match to a candidate a home of its own.
 * <p>
 * The persisted {@code candidateType} strings still carry the historical names
 * ({@code ClassCdd}, {@code PropertyCdd}, {@code ActionCdd}, {@code AssociationCdd},
 * {@code GeneralizationCdd}, {@code SynonymCdd}) — the Java classes were renamed to CRC vocabulary
 * but the DB values were deliberately left alone.
 */
@DomainService
@Named(DomainModule.NAMESPACE + ".CandidateFactory")
@Priority(PriorityPrecedence.EARLY)
public class CandidateFactory {

    private final ClassCandidates classCandidates;
    private final AttributeCandidates attributeCandidates;
    private final ResponsibilityCandidates responsibilityCandidates;
    private final CollaboratorCandidates collaboratorCandidates;
    private final NlpProperties nlpProperties;

    @Inject
    public CandidateFactory(
            final ClassCandidates classCandidates,
            final AttributeCandidates attributeCandidates,
            final ResponsibilityCandidates responsibilityCandidates,
            final CollaboratorCandidates collaboratorCandidates,
            final NlpProperties nlpProperties) {
        this.classCandidates = classCandidates;
        this.attributeCandidates = attributeCandidates;
        this.responsibilityCandidates = responsibilityCandidates;
        this.collaboratorCandidates = collaboratorCandidates;
        this.nlpProperties = nlpProperties;
    }

    /**
     * Creates actual Candidate objects from the given RuleMatch records.
     * In the case of classes (entities), only one class with the same name is created.
     *
     * @param matches the RuleMatch records to process
     * @return the created (or re-used) Candidate objects
     */
    @Programmatic
    public List<Candidate> createCandidatesFrom(final List<RuleMatch> matches) {
        return createCandidatesFrom(matches, null);
    }

    /**
     * Creates actual Candidate objects from the given RuleMatch records, optionally bound to a
     * {@link DomainModel}.  In the case of classes (entities), only one class with the same name is
     * created.
     *
     * @param matches     the RuleMatch records to process
     * @param domainModel the model the created candidates belong to, or {@code null}
     * @return the created (or re-used) Candidate objects
     */
    @Programmatic
    public List<Candidate> createCandidatesFrom(final List<RuleMatch> matches, final DomainModel domainModel) {
        final List<Candidate> candidates = new ArrayList<>();
        if (matches == null) {
            return candidates;
        }
        final Map<String, Candidate> createdClassCandidates = new HashMap<>();
        for (final RuleMatch match : matches) {
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
    Candidate createCandidateFromMatch(final RuleMatch match, final DomainModel domainModel) {
        final String candidateType = match.getCandidateType();
        final String candidateName = match.getCandidateName();
        final String relatedCandidateName = match.getRelatedCandidateName();

        if ("ClassCdd".equals(candidateType)) {
            if (relatedCandidateName != null) {
                // Association between two ClassCdd entities (produced by TDR14–TDR23)
                if (isBlockedUseCaseNoun(candidateName) || isBlockedUseCaseNoun(relatedCandidateName)) {
                    return null;
                }
                final ClassCdd source = classCandidates.findOrCreate(candidateName, domainModel);
                source.setCandidateName(candidateName);
                final ClassCdd target = classCandidates.findOrCreate(relatedCandidateName, domainModel);
                target.setCandidateName(relatedCandidateName);
                final String assocName = candidateName + "_" + relatedCandidateName;
                return collaboratorCandidates.findOrCreate(assocName, source, target, domainModel);
            }
            if (isBlockedUseCaseNoun(candidateName)) {
                return null;
            }
            final ClassCdd classCdd = classCandidates.findOrCreate(candidateName, domainModel);
            classCdd.setCandidateName(candidateName);
            return classCdd;
        } else if ("GeneralizationCdd".equals(candidateType)) {
            // Generalization (is-a) between two ClassCdd entities (produced by TDR38–TDR40)
            // candidateName = child (subclass), relatedCandidateName = parent (superclass)
            if (relatedCandidateName == null) {
                return null;
            }
            if (isBlockedUseCaseNoun(candidateName) || isBlockedUseCaseNoun(relatedCandidateName)) {
                return null;
            }
            final ClassCdd child = classCandidates.findOrCreate(candidateName, domainModel);
            child.setCandidateName(candidateName);
            final ClassCdd parent = classCandidates.findOrCreate(relatedCandidateName, domainModel);
            parent.setCandidateName(relatedCandidateName);
            final String assocName = candidateName + "_" + relatedCandidateName;
            return collaboratorCandidates.findOrCreate(assocName, child, parent, domainModel, AssociationType.GENERALIZATION);
        } else if ("SynonymCdd".equals(candidateType)) {
            // Synonym (==) between two ClassCdd entities (produced by TDR41).
            // candidateName = head term, relatedCandidateName = synonym partner term.
            if (relatedCandidateName == null) {
                return null;
            }
            if (isBlockedUseCaseNoun(candidateName) || isBlockedUseCaseNoun(relatedCandidateName)) {
                return null;
            }
            final ClassCdd head = classCandidates.findOrCreate(candidateName, domainModel);
            head.setCandidateName(candidateName);
            final ClassCdd partner = classCandidates.findOrCreate(relatedCandidateName, domainModel);
            partner.setCandidateName(relatedCandidateName);
            final String assocName = candidateName + "_" + relatedCandidateName;
            return collaboratorCandidates.findOrCreate(assocName, head, partner, domainModel, AssociationType.SYNONYM);
        } else if ("ActionCdd".equals(candidateType)) {
            // The related candidate name should be the owning class name;
            // a blocked use-case noun (e.g. "System") must not become the owner
            final ClassCdd classCdd = (relatedCandidateName != null && !isBlockedUseCaseNoun(relatedCandidateName))
                    ? classCandidates.findOrCreate(relatedCandidateName, domainModel)
                    : null;
            return responsibilityCandidates.findOrCreate(candidateName, classCdd, domainModel);
        } else if ("PropertyCdd".equals(candidateType)) {
            // A blocked use-case noun as the owning class (e.g. "Condition") must not
            // produce an orphan property; skip entirely.
            if (relatedCandidateName != null && isBlockedUseCaseNoun(relatedCandidateName)) {
                return null;
            }
            // The related candidate name should be the owning class name
            final String className = relatedCandidateName != null ? relatedCandidateName : "Unknown";
            final String type = inferType(candidateName);
            return attributeCandidates.findOrCreate(className, candidateName, type, domainModel);
        }
        // Unknown candidate types are ignored.
        return null;
    }

    /** Returns {@code true} when {@code candidateName} is a configured blocked use-case noun. */
    @Programmatic
    boolean isBlockedUseCaseNoun(final String candidateName) {
        return UseCaseBlockedNouns.isBlocked(nlpProperties, candidateName);
    }

    /** Infers the property type from the property name. */
    @Programmatic
    String inferType(final String propertyName) {
        if (propertyName == null) return "String";
        final String lower = propertyName.toLowerCase();
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
}
