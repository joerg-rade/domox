package domox.dom.rules.lexicon;

import domox.dom.rules.CandidateFactory;
import domox.dom.rules.NlpProperties;
import domox.dom.rules.RuleMatch;
import domox.dom.rules.RuleMatches;
import domox.dom.rules.tdr.TDR25;
import domox.dom.rules.tdr.TDR26;

import java.util.List;

/**
 * The <em>use-case document-noun</em> predicates: nouns that belong to the use-case document itself
 * rather than to the domain it describes.  Two lists, two guards, one home:
 * <ul>
 *   <li><b>{@code domox.nlp.use-case-blocked-nouns}</b> — a meta-noun (template header, narrative
 *       scaffolding, generic result term) that must never become a {@code ClassCdd}.  Consulted from
 *       two places that must agree: the {@code RuleMatch} persistence guard in {@link RuleMatches}
 *       (so a blocked noun is never even recorded) and the Phase-2 candidate factory
 *       ({@link CandidateFactory}).</li>
 *   <li><b>{@code domox.nlp.use-case-metadata-nouns}</b> — a document-reference or time-unit noun
 *       ({@code "sentence 10"}, {@code "Use Case ID UC - 09"}, {@code "within 2 seconds"}) that must
 *       never carry a <em>multiplicity</em>.  Consulted by the {@link TDR25}/{@link TDR26} scope
 *       guard, so the match is not recorded at all.  Separate from the blocked list because a noun
 *       can be a legitimate class and still not be a multiplicity ({@code day}, {@code time}).</li>
 * </ul>
 * Both predicates share one implementation (a case-insensitive lookup with a plural fallback) instead
 * of two copies that could drift apart.  The lookup lowercases <em>both</em> sides: the configured
 * list contains {@code UC}, and comparing it verbatim against a lowercased candidate name silently
 * matched nothing.
 */
public final class UseCaseBlockedNouns {

    private UseCaseBlockedNouns() {
    }

    /** Returns {@code true} when {@code candidateName} is a configured blocked use-case noun. */
    public static boolean isBlocked(final NlpProperties nlpProperties, final String candidateName) {
        return isIn(nlpProperties != null ? nlpProperties.getUseCaseBlockedNouns() : null, candidateName);
    }

    /**
     * Returns {@code true} when {@code noun} is a configured use-case metadata noun, i.e. a noun that
     * must never be the governor of a multiplicity match.
     */
    public static boolean isMetadataNoun(final NlpProperties nlpProperties, final String noun) {
        return isIn(nlpProperties != null ? nlpProperties.getUseCaseMetadataNouns() : null, noun);
    }

    /** Case-insensitive membership with a plural fallback for un-lemmatized candidates. */
    private static boolean isIn(final List<String> nouns, final String candidateName) {
        if (nouns == null || candidateName == null) {
            return false;
        }
        final String lower = candidateName.toLowerCase().trim();
        if (containsIgnoreCase(nouns, lower)) {
            return true;
        }
        // Defensive fallback for un-lemmatized plurals ("Conditions" → "condition")
        if (lower.endsWith("s") && !lower.endsWith("ss")) {
            return containsIgnoreCase(nouns, lower.substring(0, lower.length() - 1));
        }
        return false;
    }

    private static boolean containsIgnoreCase(final List<String> nouns, final String lowerCandidate) {
        for (final String noun : nouns) {
            if (noun != null && noun.trim().toLowerCase().equals(lowerCandidate)) {
                return true;
            }
        }
        return false;
    }
}
