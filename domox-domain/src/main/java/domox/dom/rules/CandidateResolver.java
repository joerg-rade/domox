package domox.dom.rules;

import domox.dom.crc.*;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.Programmatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Late-binding candidate resolution (Option C).
 * <p>
 * After Phase 1 (TDR rules create {@link RuleMatch} records) and Phase 2
 * (candidates are materialised from those matches), this resolver runs to
 * determine whether a noun that appears in both entity-like and attribute-like
 * positions should be kept as a {@link ClassCdd}, a {@link PropertyCdd}, or
 * both.
 * </p>
 *
 * <h3>Heuristics</h3>
 * <ul>
 *   <li><b>Entity evidence</b>: {@code nsubj} of an action verb, {@code compound}
 *       governor, {@code amod} target with descriptive adjective.</li>
 *   <li><b>Attribute evidence</b>: {@code compound} dependent,
 *       {@code nmod:of} dependent, or governor in a possessive
 *       construction.</li>
 *   <li><b>Archetype override</b>: a {@link ClassCdd} whose archetype is
 *       {@link ClassType#MOMENT_INTERVAL}, {@link ClassType#ROLE}, or
 *       {@link ClassType#PARTY_PLACE_THING} is strongly preferred as entity.</li>
 * </ul>
 */
@DomainService
@Named(CandidateResolver.LOGICAL_NAME)
public class CandidateResolver {

    static final String LOGICAL_NAME = "domox.dom.rules.CandidateResolver";

    private static final Logger log = LoggerFactory.getLogger(CandidateResolver.class);

    @Inject
    private PropertyCandidates propertyCandidates;

    @Inject
    private BasicAttributeCatalog basicAttributeCatalog;

    /**
     * Post-processes the candidates produced by the current analysis pass,
     * resolving ambiguous nouns that could be either entities or attributes.
     * <p>
     * This method:
     * <ol>
     *   <li>For each {@link ClassCdd} whose candidate name is not in the
     *       basic-attributes whitelist, analyses the underlying
     *       {@link RuleMatch} records for attribute-like dependency evidence.</li>
     *   <li>If attribute evidence dominates, creates an additional
     *       {@link PropertyCdd} candidate for the same noun, bound to the
     *       inferred owning class.</li>
     *   <li>Both candidates remain in the graph — the user can review and
     *       accept/reject via the existing Review workflow.</li>
     * </ol>
     *
     * @param domainModel the owning domain model for any newly created candidates
     * @param candidates  the candidates created by the current analysis pass
     *                    (this document's RuleMatches); only these classes are
     *                    examined for attribute-like evidence
     */
    @Programmatic
    public void resolve(DomainModel domainModel, List<Candidate> candidates) {
        // Collect only the candidates materialised by the current analysis pass.
        // A class whose evidence did not change in this pass need not be re-scored
        // (its rule matches are unchanged), so re-iterating the entire model on
        // every document made total load time quadratic.  Existing properties are
        // still indexed across the whole model so a noun already resolved to a
        // PropertyCdd is never created twice.
        List<ClassCdd> allClasses = candidates.stream()
                .filter(c -> c instanceof ClassCdd)
                .map(c -> (ClassCdd) c)
                .filter(cc -> cc.domainModel == domainModel)
                .toList();
        List<PropertyCdd> allProperties = propertyCandidates.listAll().stream()
                .filter(pc -> pc.classCdd != null && pc.classCdd.domainModel == domainModel)
                .toList();

        log.info("Resolving {} class candidates and {} property candidates for domain model {} (current analysis pass)",
                allClasses.size(), allProperties.size(), domainModel);

        // Index existing properties by lowercased name
        Set<String> existingPropertyNames = allProperties.stream()
                .map(pc -> pc.getCandidateName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        for (ClassCdd classCdd : allClasses) {
            String noun = classCdd.getCandidateName();
            if (noun == null || noun.isEmpty()) continue;

            String lowerNoun = noun.toLowerCase(Locale.ROOT);

            // Skip if this noun is marked as a basic attribute — unambiguously a property
            if (basicAttributeCatalog.contains(lowerNoun)) continue;

            // Skip if a PropertyCdd with the same name already exists
            if (existingPropertyNames.contains(lowerNoun)) continue;

            // Analyse supporting RuleMatch records for attribute-like behaviour
            List<RuleMatch> ruleMatches = classCdd.getRuleMatches();
            if (ruleMatches == null || ruleMatches.isEmpty()) continue;

            EvidenceScore score = scoreEvidence(ruleMatches, lowerNoun);

            log.debug("Noun '{}': entityScore={}, attributeScore={}, dominant={}",
                    noun, score.entityScore, score.attributeScore, score.dominant());

            if (score.dominant() == EvidenceDominant.ATTRIBUTE) {
                String ownerClass = inferOwningClass(ruleMatches, lowerNoun);
                if (ownerClass != null) {
                    String propName = lowerNoun;
                    String type = inferPropertyType(lowerNoun);
                    try {
                        PropertyCdd propertyCdd = propertyCandidates.findOrCreate(
                                ownerClass, propName, type, domainModel);
                        if (propertyCdd != null) {
                            for (RuleMatch match : ruleMatches) {
                                propertyCdd.addMatchingRule(match);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Could not create PropertyCdd for '{}': {}", noun, e.getMessage());
                    }
                } else {
                    log.debug("No owning class inferred for '{}' — skipping PropertyCdd creation", noun);
                }
            }
        }
    }

    // --- Evidence scoring -------------------------------------------------

    /**
     * Counts entity-like vs attribute-like evidence for the given noun
     * from its supporting RuleMatch records.
     */
    EvidenceScore scoreEvidence(List<RuleMatch> matches, String lowerNoun) {
        int entityScore = 0;
        int attributeScore = 0;

        for (RuleMatch match : matches) {
            TypedDependency td = match.getTypedDependency();
            if (td == null) continue;

            TdType type = td.getType();
            String aLemma = td.getA();
            String bLemma = td.getB();

            if (type == null) continue;

            // --- Entity evidence ---

            // nsubj(VERB, X) where X is the noun — X performs an action, strong entity signal
            if ((type == TdType.NSUBJ || type == TdType.NSUBJPASS || type == TdType.NSUBJ_XSUBJ)
                    && lowerNoun.equals(bLemma)) {
                entityScore += isActionVerb(aLemma) ? 3 : 1;
            }

            // compound(A, B) where A is the noun — A is the head noun, entity-like
            if (type == TdType.COMPOUND && lowerNoun.equals(aLemma)) {
                entityScore += 2;
            }

            // obj(A, B) where B is the noun and A is action verb — entity
            if (type == TdType.OBJ && lowerNoun.equals(bLemma) && isActionVerb(aLemma)) {
                entityScore += 1;
            }

            // --- Attribute evidence ---

            // compound(A, B) where B is the noun — B modifies A, strong attribute signal
            if (type == TdType.COMPOUND && lowerNoun.equals(bLemma)) {
                attributeScore += 3;
            }

            // nmod:of(A, B) where B is the noun — "something of noun", strong attribute signal
            if (type == TdType.NMOD_OF && lowerNoun.equals(bLemma)) {
                attributeScore += 3;
            }

            // nmod:of(A, B) where A is the noun — "noun of something", weak attribute signal
            if (type == TdType.NMOD_OF && lowerNoun.equals(aLemma)) {
                attributeScore += 1;
            }

            // nmod:poss(A, B) where A is the noun and B is a pronoun — possessed
            if (type == TdType.NMOD_POSS && lowerNoun.equals(aLemma)) {
                attributeScore += 2;
            }

            // obj/dep(A, B) where B is the noun and A is a basic attribute
            if ((type == TdType.OBJ || type == TdType.DEP)
                    && lowerNoun.equals(bLemma)
                    && basicAttributeCatalog.contains(aLemma)) {
                attributeScore += 2;
            }
        }

        return new EvidenceScore(entityScore, attributeScore);
    }

    /**
     * Determines the preferred owning class for a property from the match context.
     */
    String inferOwningClass(List<RuleMatch> matches, String lowerNoun) {
        // First: check if any RuleMatch has a related ClassCdd name set
        for (RuleMatch match : matches) {
            if (match.getRelatedCandidateName() != null
                    && "ClassCdd".equals(match.getRelatedCandidateType())) {
                return match.getRelatedCandidateName();
            }
        }

        // Second: infer from the dependency structure
        for (RuleMatch match : matches) {
            TypedDependency td = match.getTypedDependency();
            if (td == null) continue;

            TdType type = td.getType();
            String aLemma = td.getA();

            if (type == null) continue;

            // compound(A, B) with B = noun → A is the owning class
            if (type == TdType.COMPOUND && lowerNoun.equals(td.getB())
                    && aLemma != null && !aLemma.isEmpty()) {
                return capitalize(aLemma);
            }

            // nmod:of(A, B) with B = noun → A is the owning class
            if (type == TdType.NMOD_OF && lowerNoun.equals(td.getB())
                    && aLemma != null && !aLemma.isEmpty()) {
                return capitalize(aLemma);
            }

            // nmod:poss(A, B) with A = noun → B (the possessor) is the owning class
            if (type == TdType.NMOD_POSS && lowerNoun.equals(aLemma)
                    && td.getB() != null && !td.getB().isEmpty()) {
                return capitalize(td.getB());
            }
        }

        // Fallback: use the noun itself as the owning class
        return capitalize(lowerNoun);
    }

    /**
     * Infers the Java property type from the property name.
     */
    static String inferPropertyType(String propertyName) {
        if (propertyName == null) return "String";
        String lower = propertyName.toLowerCase(Locale.ROOT);
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

    // --- Helpers -----------------------------------------------------------

    /**
     * A minimal set of common copular / auxiliary verbs that signal state
     * rather than action.  Excluded from action-verb scoring.
     */
    private static final Set<String> BE_HAVE_DO = Set.of(
            "be", "am", "is", "are", "was", "were", "been", "being",
            "have", "has", "had", "having",
            "do", "does", "did", "doing");

    static boolean isActionVerb(String lemma) {
        if (lemma == null) return false;
        return !BE_HAVE_DO.contains(lemma.toLowerCase(Locale.ROOT));
    }

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    // --- Internal types ----------------------------------------------------

    enum EvidenceDominant {
        ENTITY, ATTRIBUTE, BALANCED
    }

    record EvidenceScore(int entityScore, int attributeScore) {
        EvidenceDominant dominant() {
            if (entityScore > attributeScore) return EvidenceDominant.ENTITY;
            if (attributeScore > entityScore) return EvidenceDominant.ATTRIBUTE;
            return EvidenceDominant.BALANCED;
        }
    }
}