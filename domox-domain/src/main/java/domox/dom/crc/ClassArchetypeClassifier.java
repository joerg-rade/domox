package domox.dom.crc;

import domox.DomainModule;
import domox.dom.nlp.TdType;
import domox.dom.rules.NlpProperties;
import domox.dom.rules.RuleMatch;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import jakarta.annotation.Priority;

import java.util.List;
import java.util.Set;

/**
 * Post-extraction classifier that suggests a {@link ClassType} (Coad archetype)
 * for each {@link ClassCdd} based on heuristics derived from:
 * <ul>
 *   <li>The {@link TdType dependencies} that triggered the entity extraction</li>
 *   <li>Properties and actions already attached</li>
 *   <li>Name-based keyword patterns</li>
 * </ul>
 *
 * <p>Reference: Coad, Lefebvre, and De Luca (1999),
 * <em>Java Modeling in Color with UML</em> (ISBN 0-13-011510-X).</p>
 *
 * <p>The four archetypes are:
 * <ul>
 *   <li><b>MOMENT_INTERVAL</b> (pink) — transactions, events, time-bound activities</li>
 *   <li><b>ROLE</b> (yellow) — ways a party/place/thing participates</li>
 *   <li><b>DESCRIPTION</b> (blue) — catalog-like labels / classifiers</li>
 *   <li><b>PARTY_PLACE_THING</b> (green) — tangible, uniquely identifiable entities</li>
 * </ul>
 */
@DomainService
@Named(DomainModule.NAMESPACE + ".ClassArchetypeClassifier")
@Priority(PriorityPrecedence.LATE)
public class ClassArchetypeClassifier {

    private final NlpProperties nlpProperties;

    public ClassArchetypeClassifier(NlpProperties nlpProperties) {
        this.nlpProperties = nlpProperties;
    }

    // ---- TdType sets that correlate with archetypes ----

    /**
     * Dependencies that often introduce moment-interval entities.
     */
    private static final Set<TdType> MOMENT_INTERVAL_TD_TYPES = Set.of(
            TdType.NSUBJ, TdType.OBJ
    );

    /**
     * Dependencies that often introduce role entities.
     */
    private static final Set<TdType> ROLE_TD_TYPES = Set.of(
            TdType.NMOD_POSS
    );

    /**
     * Dependencies that often introduce description entities.
     */
    private static final Set<TdType> DESCRIPTION_TD_TYPES = Set.of(
            TdType.AMOD
    );

    // ================================================================
    // Public API
    // ================================================================

    /**
     * Suggests the most likely {@link ClassType} for the given {@link ClassCdd}.
     *
     * @param candidate the entity candidate to classify
     * @return the suggested archetype (never null)
     */
    @Programmatic
    public ClassType suggestArchetype(ClassCdd candidate) {
        if (candidate == null) {
            return ClassType.PARTY_PLACE_THING;
        }

        double scoreMI = scoreMomentInterval(candidate);
        double scoreRole = scoreRole(candidate);
        double scoreDesc = scoreDescription(candidate);
        double scorePPT = scorePartyPlaceThing(candidate);

        // Highest score wins
        if (scoreMI >= scoreRole && scoreMI >= scoreDesc && scoreMI >= scorePPT) {
            return ClassType.MOMENT_INTERVAL;
        }
        if (scoreRole >= scoreDesc && scoreRole >= scorePPT) {
            return ClassType.ROLE;
        }
        if (scoreDesc >= scorePPT) {
            return ClassType.DESCRIPTION;
        }
        return ClassType.PARTY_PLACE_THING;
    }

    // ================================================================
    // Scoring heuristics
    // ================================================================

    private double scoreMomentInterval(ClassCdd candidate) {
        double score = 0.0;
        String name = candidate.getCandidateName();
        if (name != null) {
            score += countKeywordHits(name, nlpProperties.getArchetypeMomentIntervalKeywords()) * 3.0;
        }
        // Has temporal properties (date, time, duration)?
        for (PropertyCdd prop : candidate.getPropertyList()) {
            String propName = prop.getCandidateName();
            if (propName != null) {
                String lc = propName.toLowerCase();
                if (lc.contains("date") || lc.contains("time") || lc.contains("duration")
                        || lc.contains("timestamp") || lc.contains("period")) {
                    score += 1.5;
                }
            }
        }
        // Actions suggest activity
        int actionCount = candidate.getActionList().size();
        score += actionCount * 0.5;
        // Extraction from nsubj/dobj patterns
        score += countTdTypes(candidate, MOMENT_INTERVAL_TD_TYPES) * 0.5;
        return score;
    }

    private double scoreRole(ClassCdd candidate) {
        double score = 0.0;
        String name = candidate.getCandidateName();
        if (name != null) {
            score += countKeywordHits(name, nlpProperties.getArchetypeRoleKeywords()) * 3.0;
            // Role names are often singular
            if (!name.toLowerCase().endsWith("s")) {
                score += 0.5;
            }
        }
        // Extraction from nmod:poss patterns (e.g., "customer's order")
        score += countTdTypes(candidate, ROLE_TD_TYPES) * 1.0;
        // Roles typically have few properties and actions
        int totalProps = candidate.getPropertyList().size();
        int totalActions = candidate.getActionList().size();
        if (totalProps <= 3) score += 0.5;
        if (totalActions <= 2) score += 0.5;
        return score;
    }

    private double scoreDescription(ClassCdd candidate) {
        double score = 0.0;
        String name = candidate.getCandidateName();
        if (name != null) {
            score += countKeywordHits(name, nlpProperties.getArchetypeDescriptionKeywords()) * 3.0;
        }
        // Extraction from amod patterns (e.g., "full {name}" → attribute)
        score += countTdTypes(candidate, DESCRIPTION_TD_TYPES) * 1.5;
        // Descriptions have many properties (attributes) but few actions
        int propCount = candidate.getPropertyList().size();
        int actionCount = candidate.getActionList().size();
        if (propCount > actionCount + 2) {
            score += 1.0;
        }
        // Descriptions often appear as targets of associations (type-of relationships)
        int assocCount = candidate.getAssociationList().size();
        if (assocCount > 0) {
            score += 0.5;
        }
        return score;
    }

    private double scorePartyPlaceThing(ClassCdd candidate) {
        double score = 2.0; // default baseline — most entities are green
        String name = candidate.getCandidateName();
        if (name == null) {
            return score;
        }
        String lower = name.toLowerCase();
        // Physical things, places, organizations
        if (lower.contains("place") || lower.contains("location") || lower.contains("address")
                || lower.contains("building") || lower.contains("room") || lower.contains("site")
                || lower.contains("warehouse") || lower.contains("store") || lower.contains("office")) {
            score += 2.0;
        }
        if (lower.contains("company") || lower.contains("organization") || lower.contains("department")
                || lower.contains("team") || lower.contains("division") || lower.contains("branch")) {
            score += 2.0;
        }
        if (lower.contains("product") || lower.contains("item") || lower.contains("equipment")
                || lower.contains("device") || lower.contains("machine") || lower.contains("tool")) {
            score += 2.0;
        }
        // Party/Place/Thing candidates typically have a moderate number of properties
        int propCount = candidate.getPropertyList().size();
        if (propCount >= 2 && propCount <= 8) {
            score += 1.0;
        }
        return score;
    }

    // =================================================
    // Helpers
    // =================================================

    /**
     * Counts how many of the given keywords appear (case-insensitively) as
     * substrings of the candidate name. Config lists are matched verbatim,
     * mirroring the previous static-set behavior.
     */
    private long countKeywordHits(String name, List<String> keywords) {
        if (name == null || keywords == null) {
            return 0;
        }
        String lower = name.toLowerCase();
        return keywords.stream()
                .filter(kw -> kw != null && lower.contains(kw.toLowerCase()))
                .count();
    }

    /**
     * Counts how many rule matches of the candidate were triggered by
     * a {@link TdType} in the given set.
     */
    private long countTdTypes(ClassCdd candidate, Set<TdType> tdTypes) {
        return candidate.getRuleMatches().stream()
                .map(RuleMatch::getTypedDependency)
                .filter(td -> td != null && td.getType() != null && tdTypes.contains(td.getType()))
                .count();
    }
}
