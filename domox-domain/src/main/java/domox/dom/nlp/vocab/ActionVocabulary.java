package domox.dom.nlp.vocab;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The action vocabulary: action verbs and service nouns, both populated from config by
 * {@code domox.dom.rules.ActionVocabularyCatalog}.
 * <p>
 * Owns the two registries that previously lived as static fields on
 * {@code TypedDependencyPredicates}.  Registration and lookup are case-insensitive.
 */
public final class ActionVocabulary {

    private static final Set<String> ACTION_VERBS = new HashSet<>();
    private static final Set<String> SERVICE_NOUNS = new HashSet<>();

    private ActionVocabulary() {
    }

    /** Reset both sets to empty (for testing purposes). */
    public static void reset() {
        ACTION_VERBS.clear();
        SERVICE_NOUNS.clear();
    }

    public static void registerActionVerbs(final Collection<String> verbs) {
        addLowerCased(ACTION_VERBS, verbs);
    }

    public static void registerServiceNouns(final Collection<String> nouns) {
        addLowerCased(SERVICE_NOUNS, nouns);
    }

    /** Case-insensitive membership test against the action-verb set. */
    public static boolean containsActionVerb(final String lemma) {
        return contains(ACTION_VERBS, lemma);
    }

    /** Case-insensitive membership test against the service-noun set. */
    public static boolean containsServiceNoun(final String lemma) {
        return contains(SERVICE_NOUNS, lemma);
    }

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
