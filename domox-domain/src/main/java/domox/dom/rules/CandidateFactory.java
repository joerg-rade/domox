package domox.dom.rules;

import domox.DomainModule;
import domox.dom.crc.model.AssociationType;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.service.AttributeCandidates;
import domox.dom.crc.service.ClassCandidates;
import domox.dom.crc.service.CollaboratorCandidates;
import domox.dom.crc.service.ResponsibilityCandidates;
import domox.dom.nlp.Sentence;
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
        // Multiplicities are stated *within one sentence* ("a customer may have many pets"), so the
        // matches are grouped by sentence up front: an association then looks up its own sentence's
        // multiplicity matches instead of re-querying the repository per match.
        final Map<Long, List<RuleMatch>> matchesBySentence = matchesBySentence(matches);
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
            final Candidate candidate =
                    createCandidateFromMatch(match, sentenceMatchesOf(matchesBySentence, match), domainModel);
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
     * Creates a single Candidate from a RuleMatch record with no knowledge of the other matches of
     * its sentence — the shape used by unit tests and by any caller holding one match.
     * <p>
     * An association created through this overload carries no multiplicity: there is no sibling
     * evidence to read, and guessing one would assert a count the text never stated.
     */
    @Programmatic
    Candidate createCandidateFromMatch(final RuleMatch match, final DomainModel domainModel) {
        return createCandidateFromMatch(match, List.of(), domainModel);
    }

    /**
     * Creates a single Candidate from a RuleMatch record.
     * Dispatches based on candidateType.
     *
     * @param sentenceMatches every match whose typed dependency belongs to the same sentence as
     *                        {@code match} — the multiplicity evidence for an association
     */
    @Programmatic
    Candidate createCandidateFromMatch(
            final RuleMatch match, final List<RuleMatch> sentenceMatches, final DomainModel domainModel) {
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
                final CollaboratorCdd association =
                        collaboratorCandidates.findOrCreate(assocName, source, target, domainModel);
                applyMultiplicities(association, source, target, sentenceMatches);
                return association;
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
            final ResponsibilityCdd responsibility =
                    responsibilityCandidates.findOrCreate(candidateName, classCdd, domainModel);
            addParameters(responsibility, match, domainModel);
            return responsibility;
        } else if ("PropertyCdd".equals(candidateType)) {
            // A property match must name its owning class: a match with no owner used to be attached to
            // a class literally called "Unknown" (the fallback below), which produced a phantom class
            // with 12 attributes on it.  TDR12's "both sides are basic attributes" branch — "size color",
            // "description description" — is exactly such a match, and there is no owner to invent.
            if (relatedCandidateName == null || isBlockedUseCaseNoun(relatedCandidateName)) {
                return null;
            }
            return attributeCandidates.findOrCreate(
                    relatedCandidateName, candidateName, inferType(candidateName), domainModel);
        }
        // Unknown candidate types are ignored.
        return null;
    }

    /**
     * Records the operation's parameters, read from the sentence the operation was extracted from.
     * <p>
     * The type of a parameter is the model's own class of that name when there is one — the sentence
     * object usually is an entity ("browse a {@code Pet}" → {@code browse(pet: Pet)}) — and otherwise
     * the built-in type inferred from the name ({@link #inferType}).  A parameter whose name never
     * became a class is a value, not a reference.
     */
    private void addParameters(
            final ResponsibilityCdd responsibility, final RuleMatch match, final DomainModel domainModel) {
        for (final String noun
                : ResponsibilityParameterExtraction.objectNounsOf(match.getTypedDependency(), match.getCandidateName())) {
            responsibilityCandidates.addParameter(responsibility, noun, parameterType(noun, domainModel));
        }
    }

    /** The type of a parameter: the model's class of that name, else the inferred built-in type. */
    private String parameterType(final String name, final DomainModel domainModel) {
        final ClassCdd type = classCandidates.findByCandidateName(
                capitalizeFirstLetter(name), domainModel);
        return type != null ? type.getCandidateName() : inferType(name);
    }

    /** {@code pet} → {@code Pet} — candidate names are capitalised, the parser's lemmas are not. */
    private static String capitalizeFirstLetter(final String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    /** Returns {@code true} when {@code candidateName} is a configured blocked use-case noun. */
    @Programmatic
    boolean isBlockedUseCaseNoun(final String candidateName) {
        return UseCaseBlockedNouns.isBlocked(nlpProperties, candidateName);
    }

    /**
     * Copies the multiplicities the sentence stated onto the two ends of the association.
     * <p>
     * Both columns are {@code NOT NULL}, so {@code ""} is the "absent" value: an end the sentence says
     * nothing about is left untouched — including an end an earlier match already filled, because one
     * association name can be produced by several sentences of the same corpus.
     */
    private static void applyMultiplicities(
            final CollaboratorCdd association,
            final ClassCdd source,
            final ClassCdd target,
            final List<RuleMatch> sentenceMatches) {
        final String sourceCardinality =
                MultiplicityEvidence.cardinalityFor(sentenceMatches, source.getCandidateName());
        if (!sourceCardinality.isEmpty()) {
            association.setSourceCardinality(sourceCardinality);
        }
        final String targetCardinality =
                MultiplicityEvidence.cardinalityFor(sentenceMatches, target.getCandidateName());
        if (!targetCardinality.isEmpty()) {
            association.setTargetCardinality(targetCardinality);
        }
    }

    /**
     * Groups the matches by the id of the sentence their typed dependency belongs to.  A match without
     * a sentence (or with an unsaved one) is left out — it can contribute no sentence-scoped evidence.
     */
    private static Map<Long, List<RuleMatch>> matchesBySentence(final List<RuleMatch> matches) {
        final Map<Long, List<RuleMatch>> bySentence = new HashMap<>();
        for (final RuleMatch match : matches) {
            final Long sentenceId = sentenceIdOf(match);
            if (sentenceId != null) {
                bySentence.computeIfAbsent(sentenceId, key -> new ArrayList<>()).add(match);
            }
        }
        return bySentence;
    }

    /** The matches of {@code match}'s own sentence, or an empty list when it has none. */
    private static List<RuleMatch> sentenceMatchesOf(
            final Map<Long, List<RuleMatch>> matchesBySentence, final RuleMatch match) {
        final Long sentenceId = sentenceIdOf(match);
        final List<RuleMatch> matches = sentenceId != null ? matchesBySentence.get(sentenceId) : null;
        return matches != null ? matches : List.of();
    }

    /** The id of the sentence {@code match}'s typed dependency belongs to, or {@code null}. */
    private static Long sentenceIdOf(final RuleMatch match) {
        if (match == null || match.getTypedDependency() == null) {
            return null;
        }
        final Sentence sentence = match.getTypedDependency().getSentence();
        return sentence != null ? sentence.getId() : null;
    }

    /** Infers the property type from the property name. */
    @Programmatic
    String inferType(final String propertyName) {
        if (propertyName == null) return "String";
        final String lower = propertyName.toLowerCase();
        if (lower.contains("count") || lower.contains("number") || lower.contains("age")) {
            return "int";
        } else if (lower.contains("price") || lower.contains("amount")
                || lower.contains("cost") || lower.contains("fee")) {
            return "double";
        } else if (lower.contains("active") || lower.contains("valid") || lower.contains("enabled")) {
            return "boolean";
        } else if (lower.contains("date") || lower.contains("time")) {
            return "LocalDateTime";
        }
        return "String";
    }
}
