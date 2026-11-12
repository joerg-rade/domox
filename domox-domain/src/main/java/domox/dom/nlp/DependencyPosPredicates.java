package domox.dom.nlp;

import java.util.Arrays;
import java.util.Set;

/**
 * <b>Part-of-speech predicates on the two tokens of a {@link TypedDependency}</b> — {@code A} is the
 * governor (head), {@code B} the dependent.
 * <p>
 * Carved out of {@code TypedDependencyPredicates}, which had grown into a grab-bag of three unrelated
 * kinds of predicate (POS-of-A/B, dependency type, and vocabulary lookup) plus a pass-through layer
 * for the six vocabulary registries.  This is the POS half; {@link DependencyTypePredicates} answers
 * "what relation is this?" and {@link DependencyVocabularyPredicates} answers "does a vocabulary know
 * this word?".
 * <p>
 * A dependency whose POS is unset answers {@code false} — the generator leaves such a token
 * unfiltered rather than guessing a tag.
 */
public final class DependencyPosPredicates {

    private static final Set<PartOfSpeechType> VERB_TYPES = Set.of(
            PartOfSpeechType.VB, PartOfSpeechType.VBG, PartOfSpeechType.VBN,
            PartOfSpeechType.VBP, PartOfSpeechType.VBZ);

    private static final Set<PartOfSpeechType> NOUN_TYPES = Set.of(
            PartOfSpeechType.NN, PartOfSpeechType.NNP,
            PartOfSpeechType.NNS, PartOfSpeechType.NFP);

    private static final PartOfSpeechType[] ADJECTIVE_TYPES = {
            PartOfSpeechType.JJ};

    private DependencyPosPredicates() {
    }

    /** Whether the governor (A) is a verb. */
    public static boolean isVerbA(final TypedDependency td) {
        return td.getGovernorPos() != null && VERB_TYPES.contains(td.getGovernorPos());
    }

    /** Whether the governor (A) is a noun. */
    public static boolean isNounA(final TypedDependency td) {
        return td.getGovernorPos() != null && NOUN_TYPES.contains(td.getGovernorPos());
    }

    /** Whether the dependent (B) is a noun. */
    public static boolean isNounB(final TypedDependency td) {
        return td.getDependentPos() != null && NOUN_TYPES.contains(td.getDependentPos());
    }

    /** Whether the dependent (B) is an adjective. */
    public static boolean isAdjectiveB(final TypedDependency td) {
        return td.getDependentPos() != null && Arrays.asList(ADJECTIVE_TYPES).contains(td.getDependentPos());
    }
}
