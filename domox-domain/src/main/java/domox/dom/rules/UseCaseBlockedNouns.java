package domox.dom.rules;

import java.util.List;

/**
 * The <em>blocked use-case noun</em> predicate: a use-case document meta-noun (template header,
 * narrative scaffolding, or generic result term) that must never become a {@code ClassCdd} entity.
 * <p>
 * It is consulted from two places that must agree — the {@code RuleMatch} persistence guard in
 * {@link RuleMatches} (so a blocked noun is never even recorded) and the Phase-2 candidate factory
 * ({@link CandidateFactory}, so a blocked noun never becomes a candidate through any other route).
 * It used to be a private method on {@code RuleMatches}; it lives here so both callers share one
 * implementation instead of two copies that could drift apart.
 * <p>
 * The blocked nouns are configured as singular lemmas under
 * {@code domox.nlp.use-case-blocked-nouns}.  Candidate names are compared case-insensitively; a
 * simple plural fallback is applied for nouns that reach Phase 2 without lemmatization.
 */
final class UseCaseBlockedNouns {

    private UseCaseBlockedNouns() {
    }

    /** Returns {@code true} when {@code candidateName} is a configured blocked use-case noun. */
    static boolean isBlocked(final NlpProperties nlpProperties, final String candidateName) {
        if (candidateName == null) {
            return false;
        }
        final List<String> blocked = nlpProperties.getUseCaseBlockedNouns();
        if (blocked == null) {
            return false;
        }
        final String lower = candidateName.toLowerCase().trim();
        if (blocked.contains(lower)) {
            return true;
        }
        // Defensive fallback for un-lemmatized plurals ("Conditions" → "condition")
        if (lower.endsWith("s") && !lower.endsWith("ss")) {
            return blocked.contains(lower.substring(0, lower.length() - 1));
        }
        return false;
    }
}
