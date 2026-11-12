package domox.dom.nlp.vocab;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The synonym-marker vocabulary — the predicate verbs of a defining construction such as
 * "X, also known as Y" (see SYNONYMS.md §1).
 * <p>
 * Owns the registry that previously lived as a static field on
 * {@code TypedDependencyPredicates}; populated from config by
 * {@code domox.dom.rules.SynonymCatalog}.  Registration and lookup are case-insensitive.
 */
public final class SynonymVocabulary {

    private static final Set<String> SYNONYM_MARKERS = new HashSet<>();

    private SynonymVocabulary() {
    }

    /** Reset to the empty set (for testing purposes). */
    public static void reset() {
        SYNONYM_MARKERS.clear();
    }

    public static void register(final Collection<String> markers) {
        if (markers != null) {
            for (final String marker : markers) {
                if (marker != null) {
                    SYNONYM_MARKERS.add(marker.toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    /** Case-insensitive membership test. */
    public static boolean contains(final String lemma) {
        return lemma != null && SYNONYM_MARKERS.contains(lemma.toLowerCase(Locale.ROOT));
    }
}
