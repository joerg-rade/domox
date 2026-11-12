package domox.dom.nlp.vocab;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The "blocked verbs" vocabulary — lemmas that must never be treated as an action verb.
 * <p>
 * Owns the registry that previously lived as a static field on
 * {@code TypedDependencyPredicates}; populated from config by
 * {@code domox.dom.rules.BlockedVerbCatalog}.  Registration and lookup are case-insensitive.
 */
public final class BlockedVerbVocabulary {

    private static final Set<String> BLOCKED_VERBS = new HashSet<>();

    private BlockedVerbVocabulary() {
    }

    /** Reset to the empty set (for testing purposes). */
    public static void reset() {
        BLOCKED_VERBS.clear();
    }

    public static void register(final Collection<String> verbs) {
        if (verbs != null) {
            for (final String verb : verbs) {
                if (verb != null) {
                    BLOCKED_VERBS.add(verb.toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    /** Case-insensitive membership test. */
    public static boolean contains(final String lemma) {
        return lemma != null && BLOCKED_VERBS.contains(lemma.toLowerCase(Locale.ROOT));
    }
}
