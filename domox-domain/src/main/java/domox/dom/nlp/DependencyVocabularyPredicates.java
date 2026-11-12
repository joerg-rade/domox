package domox.dom.nlp;

import domox.dom.nlp.vocab.ActionVocabulary;
import domox.dom.nlp.vocab.BasicAttributeVocabulary;
import domox.dom.nlp.vocab.BlockedVerbVocabulary;
import domox.dom.nlp.vocab.GeneralizationVocabulary;
import domox.dom.nlp.vocab.NounTypeVocabulary;
import domox.dom.nlp.vocab.SynonymVocabulary;
import domox.dom.rules.NounType;

/**
 * <b>Predicates answered by a vocabulary</b> — "does a registered word list know this lemma?".
 * <p>
 * Carved out of {@code TypedDependencyPredicates}, which had grown into a grab-bag of three unrelated
 * kinds of predicate (POS-of-A/B, dependency type, and vocabulary lookup) plus a pass-through layer
 * for the six vocabulary registries.  This is the vocabulary half; {@link DependencyPosPredicates}
 * answers "what part of speech is this?" and {@link DependencyTypePredicates} answers "what relation
 * is this?".
 * <p>
 * The vocabularies themselves live in {@link domox.dom.nlp.vocab} and are the single owners of their
 * word lists — register and reset a list there directly, not through this class.
 */
public final class DependencyVocabularyPredicates {

    private DependencyVocabularyPredicates() {
    }

    /** Whether {@code verb} is a blocked verb (one whose matches are never persisted as candidates). */
    public static boolean isBlockedVerb(final String verb) {
        return BlockedVerbVocabulary.contains(verb);
    }

    /** Whether the governor (A) is a basic attribute name. */
    public static boolean isBasicAttributeA(final TypedDependency td) {
        return BasicAttributeVocabulary.contains(td.getA());
    }

    /** Whether the dependent (B) is a basic attribute name. */
    public static boolean isBasicAttributeB(final TypedDependency td) {
        return BasicAttributeVocabulary.contains(td.getB());
    }

    /**
     * Returns the {@link NounType} of the dependent (B) lemma of the given
     * typed dependency, or {@code null} if the lemma is not in the lexicon.
     */
    public static NounType nounTypeB(final TypedDependency td) {
        return td == null ? null : NounTypeVocabulary.typeOf(td.getB());
    }

    /**
     * Returns the {@link NounType} of the governor (A) lemma of the given
     * typed dependency, or {@code null} if the lemma is not in the lexicon.
     */
    public static NounType nounTypeA(final TypedDependency td) {
        return td == null ? null : NounTypeVocabulary.typeOf(td.getA());
    }

    /** Convenience: true iff the dependent (B) is a GENERIC_ATTRIBUTE. */
    public static boolean isGenericAttributeB(final TypedDependency td) {
        return nounTypeB(td) == NounType.GENERIC_ATTRIBUTE;
    }

    /** Convenience: true iff the dependent (B) is a ROLE_NOUN. */
    public static boolean isRoleNounB(final TypedDependency td) {
        return nounTypeB(td) == NounType.ROLE_NOUN;
    }

    /** Convenience: true iff the governor (A) is a ROLE_NOUN. */
    public static boolean isRoleNounA(final TypedDependency td) {
        return nounTypeA(td) == NounType.ROLE_NOUN;
    }

    /** Convenience: true iff the dependent (B) is a DOMAIN_ENTITY. */
    public static boolean isDomainEntityB(final TypedDependency td) {
        return nounTypeB(td) == NounType.DOMAIN_ENTITY;
    }

    public static boolean isActionVerbA(final TypedDependency td) {
        return ActionVocabulary.containsActionVerb(td.getA());
    }

    public static boolean isActionVerbB(final TypedDependency td) {
        return ActionVocabulary.containsActionVerb(td.getB());
    }

    public static boolean isServiceNounA(final TypedDependency td) {
        return ActionVocabulary.containsServiceNoun(td.getA());
    }

    public static boolean isServiceNounB(final TypedDependency td) {
        return ActionVocabulary.containsServiceNoun(td.getB());
    }
    /**
     * Returns true if the dependent of this dependency is a form of the copular verb "be".
     */
    public static boolean isBeVerbDependent(final TypedDependency td) {
        return GeneralizationVocabulary.containsBeVerb(td.getB());
    }

    /**
     * Returns true if the dependent B of this dependency is an indefinite article ("a" or "an").
     */
    public static boolean isIndefiniteArticleDependent(final TypedDependency td) {
        return GeneralizationVocabulary.containsIndefiniteArticle(td.getB());
    }

    /**
     * Returns true if the given string contains any registered kind/type/sort term.
     */
    public static boolean containsKindTypeOrSort(final String lemma) {
        return GeneralizationVocabulary.containsKindTypeOrSort(lemma);
    }

    /**
     * Returns true if the dependent B of this dependency contains a kind/type/sort term.
     */
    public static boolean isKindTypeOrSortDependent(final TypedDependency td) {
        return containsKindTypeOrSort(td.getB());
    }

    /**
     * Returns true if the governor A of this dependency contains a kind/type/sort term.
     */
    public static boolean isKindTypeOrSortGovernor(final TypedDependency td) {
        return containsKindTypeOrSort(td.getA());
    }

    /**
     * Returns true if the given adjective lemma is a stop adjective (evaluative/generic).
     */
    public static boolean isStopAdjective(final String lemma) {
        return GeneralizationVocabulary.containsStopAdjective(lemma);
    }

    /**
     * Returns true if the given lemma is a synonym-marker verb — the predicate
     * of a defining construction such as "X, also known as Y", "X, termed Y",
     * or "X, referred to as Y" (see SYNONYMS.md §1).
     */
    public static boolean isSynonymMarker(final String lemma) {
        return SynonymVocabulary.contains(lemma);
    }
}
