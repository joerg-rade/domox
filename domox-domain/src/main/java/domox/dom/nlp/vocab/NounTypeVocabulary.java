package domox.dom.nlp.vocab;

import domox.dom.rules.NounType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The noun-type lexicon — lemma → {@link NounType} — used by the noun-type predicates.
 * <p>
 * Owns the registry that previously lived as a static field on
 * {@code TypedDependencyPredicates}; populated from config by
 * {@code domox.dom.rules.NounTypeLexicon}.  Keys are stored and looked up lower-cased.
 */
public final class NounTypeVocabulary {

    private static final Map<String, NounType> LEXICON = new HashMap<>();

    private NounTypeVocabulary() {
    }

    /** Reset to the empty lexicon (for testing purposes). */
    public static void reset() {
        LEXICON.clear();
    }

    public static void register(final String lemma, final NounType type) {
        if (lemma != null && type != null) {
            LEXICON.put(lemma.toLowerCase(Locale.ROOT), type);
        }
    }

    /** The {@link NounType} of {@code lemma}, or {@code null} when it is not in the lexicon. */
    public static NounType typeOf(final String lemma) {
        return lemma == null ? null : LEXICON.get(lemma.toLowerCase(Locale.ROOT));
    }
}
