package domox.dom.nlp.vocab;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The generalization vocabularies — the copular "be" verbs, indefinite articles, kind/type/sort
 * terms and stop adjectives — all populated from config by
 * {@code domox.dom.rules.GeneralizationCatalog}.
 * <p>
 * Owns the four registries that previously lived as static fields on
 * {@code TypedDependencyPredicates}.  They are grouped here because they share one registrar and
 * one lifecycle (see {@link #resetAll()}).  All lookups are case-insensitive.
 */
public final class GeneralizationVocabulary {

    private static final Set<String> BE_VERBS = new HashSet<>();
    private static final Set<String> INDEFINITE_ARTICLES = new HashSet<>();
    private static final Set<String> KIND_TYPE_SORT_TERMS = new HashSet<>();
    private static final Set<String> STOP_ADJECTIVES = new HashSet<>();

    private GeneralizationVocabulary() {
    }

    /** Reset all four generalization vocabularies (for testing purposes). */
    public static void resetAll() {
        resetBeVerbs();
        resetIndefiniteArticles();
        resetKindTypeSortTerms();
        resetStopAdjectives();
    }

    // ---- be verbs ----

    public static void resetBeVerbs() {
        BE_VERBS.clear();
    }

    public static void registerBeVerbs(final Collection<String> verbs) {
        addLowerCased(BE_VERBS, verbs);
    }

    /** True when {@code lemma} is a registered form of the copular verb "be". */
    public static boolean containsBeVerb(final String lemma) {
        return contains(BE_VERBS, lemma);
    }

    // ---- indefinite articles ----

    public static void resetIndefiniteArticles() {
        INDEFINITE_ARTICLES.clear();
    }

    public static void registerIndefiniteArticles(final Collection<String> articles) {
        addLowerCased(INDEFINITE_ARTICLES, articles);
    }

    /** True when {@code lemma} is a registered indefinite article ("a" / "an"). */
    public static boolean containsIndefiniteArticle(final String lemma) {
        return contains(INDEFINITE_ARTICLES, lemma);
    }

    // ---- kind / type / sort terms ----

    public static void resetKindTypeSortTerms() {
        KIND_TYPE_SORT_TERMS.clear();
    }

    public static void registerKindTypeSortTerms(final Collection<String> terms) {
        addLowerCased(KIND_TYPE_SORT_TERMS, terms);
    }

    /** True when {@code lemma} <em>contains</em> any registered kind/type/sort term. */
    public static boolean containsKindTypeOrSort(final String lemma) {
        if (lemma == null) return false;
        final String lower = lemma.toLowerCase(Locale.ROOT);
        for (final String term : KIND_TYPE_SORT_TERMS) {
            if (lower.contains(term)) {
                return true;
            }
        }
        return false;
    }

    // ---- stop adjectives ----

    public static void resetStopAdjectives() {
        STOP_ADJECTIVES.clear();
    }

    public static void registerStopAdjectives(final Collection<String> adjectives) {
        addLowerCased(STOP_ADJECTIVES, adjectives);
    }

    /** True when {@code lemma} is a registered stop adjective (evaluative/generic). */
    public static boolean containsStopAdjective(final String lemma) {
        return contains(STOP_ADJECTIVES, lemma);
    }

    // ---- shared helpers ----

    private static void addLowerCased(final Set<String> target, final Collection<String> values) {
        if (values != null) {
            for (final String value : values) {
                if (value != null) {
                    target.add(value.toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    private static boolean contains(final Set<String> target, final String lemma) {
        return lemma != null && target.contains(lemma.toLowerCase(Locale.ROOT));
    }
}
