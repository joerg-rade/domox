package domox.dom.nlp;

import domox.dom.nlp.vocab.ActionVocabulary;
import domox.dom.nlp.vocab.BasicAttributeVocabulary;
import domox.dom.nlp.vocab.BlockedVerbVocabulary;
import domox.dom.nlp.vocab.GeneralizationVocabulary;
import domox.dom.nlp.vocab.NounTypeVocabulary;
import domox.dom.nlp.vocab.SynonymVocabulary;
import domox.dom.rules.NounType;

import java.util.*;

public final class TypedDependencyPredicates {

    private static final Set<PartOfSpeechType> VERB_TYPES = Set.of(
            PartOfSpeechType.VB, PartOfSpeechType.VBG, PartOfSpeechType.VBN,
            PartOfSpeechType.VBP, PartOfSpeechType.VBZ);

    private static final Set<PartOfSpeechType> NOUN_TYPES = Set.of(
            PartOfSpeechType.NN, PartOfSpeechType.NNP,
            PartOfSpeechType.NNS, PartOfSpeechType.NFP);

    private static final PartOfSpeechType[] ADJECTIVE_TYPES = {
            PartOfSpeechType.JJ};

    public static boolean isNsubj(TypedDependency td) {
        return td.getType() == TdType.NSUBJ;
    }

    public static boolean isNsubjPass(TypedDependency td) {
        return td.getType() == TdType.NSUBJPASS;
    }

    public static boolean isVerbA(TypedDependency td) {
        return td.getGovernorPos() != null && VERB_TYPES.contains(td.getGovernorPos());
    }

    //region BLOCKED_VERBS (registry owned by BlockedVerbVocabulary)
    /** Reset to empty set (for testing purposes). */
    public static void resetBlockedVerbs() {
        BlockedVerbVocabulary.reset();
    }

    public static void registerBlockedVerbs(Collection<String> verbs) {
        BlockedVerbVocabulary.register(verbs);
    }

    public static boolean isBlockedVerb(String verb) {
        return BlockedVerbVocabulary.contains(verb);
    }
    // endregion

    public static boolean isNounB(TypedDependency td) {
        return td.getDependentPos() != null && NOUN_TYPES.contains(td.getDependentPos());
    }

    public static boolean isCompound(TypedDependency td) {
        return td.getType() == TdType.COMPOUND;
    }

    //region BASIC_ATTRIB (registry owned by BasicAttributeVocabulary)
    /** Reset to empty set (for testing purposes). */
    public static void resetBasicAttributes() {
        BasicAttributeVocabulary.reset();
    }

    public static void registerBasicAttributes(Collection<String> attributes) {
        BasicAttributeVocabulary.register(attributes);
    }

    public static boolean isBasicAttributeA(TypedDependency td) {
        return BasicAttributeVocabulary.contains(td.getA());
    }

    public static boolean isBasicAttributeB(TypedDependency td) {
        return BasicAttributeVocabulary.contains(td.getB());
    }
    // endregion BASIC_ATTRIB

    //region NOUN_TYPE (registry owned by NounTypeVocabulary)
    public static void resetNounTypeLexicon() {
        NounTypeVocabulary.reset();
    }

    public static void registerNounType(String lemma, NounType type) {
        NounTypeVocabulary.register(lemma, type);
    }

    /**
     * Returns the {@link NounType} of the dependent (B) lemma of the given
     * typed dependency, or {@code null} if the lemma is not in the lexicon.
     */
    public static NounType nounTypeB(TypedDependency td) {
        return td == null ? null : NounTypeVocabulary.typeOf(td.getB());
    }

    /**
     * Returns the {@link NounType} of the governor (A) lemma of the given
     * typed dependency, or {@code null} if the lemma is not in the lexicon.
     */
    public static NounType nounTypeA(TypedDependency td) {
        return td == null ? null : NounTypeVocabulary.typeOf(td.getA());
    }

    /**
     * Convenience: true iff the dependent (B) is a GENERIC_ATTRIBUTE.
     */
    public static boolean isGenericAttributeB(TypedDependency td) {
        return nounTypeB(td) == NounType.GENERIC_ATTRIBUTE;
    }

    /**
     * Convenience: true iff the dependent (B) is a ROLE_NOUN.
     */
    public static boolean isRoleNounB(TypedDependency td) {
        return nounTypeB(td) == NounType.ROLE_NOUN;
    }

    /**
     * Convenience: true iff the governor (A) is a ROLE_NOUN.
     */
    public static boolean isRoleNounA(TypedDependency td) {
        return nounTypeA(td) == NounType.ROLE_NOUN;
    }

    /**
     * Convenience: true iff the dependent (B) is a DOMAIN_ENTITY.
     */
    public static boolean isDomainEntityB(TypedDependency td) {
        return nounTypeB(td) == NounType.DOMAIN_ENTITY;
    }

    // endregion NOUN_TYPE

    //region ACTION_VERBS & SERVICE_NOUNS (registries owned by ActionVocabulary)
    /** Reset to empty sets (for testing purposes). */
    public static void resetActionVocabularies() {
        ActionVocabulary.reset();
    }

    public static void registerActionVerbs(Collection<String> verbs) {
        ActionVocabulary.registerActionVerbs(verbs);
    }

    public static void registerServiceNouns(Collection<String> nouns) {
        ActionVocabulary.registerServiceNouns(nouns);
    }

    public static boolean isActionVerbA(TypedDependency td) {
        return ActionVocabulary.containsActionVerb(td.getA());
    }

    public static boolean isActionVerbB(TypedDependency td) {
        return ActionVocabulary.containsActionVerb(td.getB());
    }

    public static boolean isServiceNounA(TypedDependency td) {
        return ActionVocabulary.containsServiceNoun(td.getA());
    }

    public static boolean isServiceNounB(TypedDependency td) {
        return ActionVocabulary.containsServiceNoun(td.getB());
    }
    // endregion

    public static boolean isNounA(TypedDependency td) {
        return td.getGovernorPos() != null && NOUN_TYPES.contains(td.getGovernorPos());
    }

    public static boolean isAdjectiveB(TypedDependency td) {
        return td.getDependentPos() != null && Arrays.asList(ADJECTIVE_TYPES).contains(td.getDependentPos());
    }

    public static boolean dobj(TypedDependency td) {
        // Direct object: in Stanford UD, this is 'obj'
        return td.getType().equals(TdType.OBJ);
    }


    public static boolean iobj(TypedDependency td) {
        // Indirect object: check for indirect object patterns in obl types
        return td.getType().equals(TdType.OBL);
    }


    public static boolean pobj(TypedDependency td) {
        // Prepositional object: check for obl types
        return td.getType().toString().startsWith("OBL");
    }


    public static boolean amod(TypedDependency td) {
        return td.getType().equals(TdType.AMOD);
    }


    public static boolean advmod(TypedDependency td) {
        return td.getType().equals(TdType.ADVMOD);
    }


    public static boolean nmodOf(TypedDependency td) {
        return td.getType().equals(TdType.NMOD_OF);
    }


    public static boolean nmodIn(TypedDependency td) {
        // in: use obl:in or similar
        return td.getType().equals(TdType.OBL_IN) || td.getType().equals(TdType.NMOD);
    }


    public static boolean nmodTo(TypedDependency td) {
        // to: use obl:to or similar
        return td.getType().equals(TdType.OBL_TO);
    }


    public static boolean nmodFor(TypedDependency td) {
        return td.getType().equals(TdType.NMOD_FOR) || td.getType().equals(TdType.OBL_FOR);
    }


    public static boolean nmodFrom(TypedDependency td) {
        // from: check for obl types
        return td.getType().toString().contains("FROM") || td.getType().toString().contains("from");
    }


    public static boolean nmodAs(TypedDependency td) {
        // as: check obl types or nmod
        return td.getType().toString().contains("AS") || td.getType().toString().contains("as");
    }


    public static boolean nmodBy(TypedDependency td) {
        return td.getType().equals(TdType.OBL_BY) || td.getType().equals(TdType.NMOD);
    }


    public static boolean nmodAgent(TypedDependency td) {
        // agent: typically obl:agent or nmod:agent, might not exist exactly
        return td.getType().toString().contains("AGENT") || td.getType().toString().contains("agent");
    }


    public static boolean nmodWith(TypedDependency td) {
        return td.getType().equals(TdType.NMOD_WITH) || td.getType().equals(TdType.OBL_WITH);
    }


    public static boolean nmodPoss(TypedDependency td) {
        return td.getType().equals(TdType.NMOD_POSS);
    }


    public static boolean appos(TypedDependency td) {
        return td.getType().equals(TdType.APPOS);
    }


    public static boolean acl(TypedDependency td) {
        return td.getType().equals(TdType.ACL);
    }


    public static boolean aclRelcl(TypedDependency td) {
        return td.getType().equals(TdType.ACL_RELCL) || td.getType().equals(TdType.ACL);
    }


    public static boolean nmodAnd(TypedDependency td) {
        return td.getType().equals(TdType.CONJ_AND);
    }


    public static boolean nmodOr(TypedDependency td) {
        return td.getType().equals(TdType.CONJ_OR);
    }


    public static boolean mark(TypedDependency td) {
        return td.getType().equals(TdType.MARK);
    }


    public static boolean xcomp(TypedDependency td) {
        return td.getType().equals(TdType.XCOMP);
    }


    public static boolean advcl(TypedDependency td) {
        return td.getType().equals(TdType.ADVCL);
    }


    public static boolean nummod(TypedDependency td) {
        return td.getType().equals(TdType.NUMMOD);
    }


    public static boolean det(TypedDependency td) {
        return td.getType().equals(TdType.DET);
    }


    public static boolean neg(TypedDependency td) {
        // neg doesn't exist in TdType, so check if it might be in a string or as a pattern
        var tdType = td.getType().toString();
        return tdType.contains("NEG") || tdType.contains("neg");
    }


    public static boolean cop(TypedDependency td) {
        return td.getType().equals(TdType.COP);
    }


    //region BE_VERBS (registry owned by GeneralizationVocabulary)
    /** Reset the BE_VERBS set to empty (for testing purposes). */
    public static void resetBeVerbs() {
        GeneralizationVocabulary.resetBeVerbs();
    }

    public static void registerBeVerbs(Collection<String> verbs) {
        GeneralizationVocabulary.registerBeVerbs(verbs);
    }

    /**
     * Returns true if the dependent of this dependency is a form of the copular verb "be".
     */
    public static boolean isBeVerbDependent(TypedDependency td) {
        return GeneralizationVocabulary.containsBeVerb(td.getB());
    }
    // endregion

    //region INDEFINITE_ARTICLES (registry owned by GeneralizationVocabulary)
    public static void resetIndefiniteArticles() {
        GeneralizationVocabulary.resetIndefiniteArticles();
    }

    public static void registerIndefiniteArticles(Collection<String> articles) {
        GeneralizationVocabulary.registerIndefiniteArticles(articles);
    }

    /**
     * Returns true if the dependent B of this dependency is an indefinite article ("a" or "an").
     */
    public static boolean isIndefiniteArticleDependent(TypedDependency td) {
        return GeneralizationVocabulary.containsIndefiniteArticle(td.getB());
    }
    // endregion

    //region KIND_TYPE_SORT_TERMS (registry owned by GeneralizationVocabulary)
    public static void resetKindTypeSortTerms() {
        GeneralizationVocabulary.resetKindTypeSortTerms();
    }

    public static void registerKindTypeSortTerms(Collection<String> terms) {
        GeneralizationVocabulary.registerKindTypeSortTerms(terms);
    }

    /**
     * Returns true if the given string contains any registered kind/type/sort term.
     */
    public static boolean containsKindTypeOrSort(String lemma) {
        return GeneralizationVocabulary.containsKindTypeOrSort(lemma);
    }

    /**
     * Returns true if the dependent B of this dependency contains a kind/type/sort term.
     */
    public static boolean isKindTypeOrSortDependent(TypedDependency td) {
        return containsKindTypeOrSort(td.getB());
    }

    /**
     * Returns true if the governor A of this dependency contains a kind/type/sort term.
     */
    public static boolean isKindTypeOrSortGovernor(TypedDependency td) {
        return containsKindTypeOrSort(td.getA());
    }
    // endregion

    //region STOP_ADJECTIVES (registry owned by GeneralizationVocabulary)
    public static void resetStopAdjectives() {
        GeneralizationVocabulary.resetStopAdjectives();
    }

    public static void registerStopAdjectives(Collection<String> adjectives) {
        GeneralizationVocabulary.registerStopAdjectives(adjectives);
    }

    /**
     * Convenience reset for all generalization vocabularies
     * (BE_VERBS, INDEFINITE_ARTICLES, KIND_TYPE_SORT_TERMS, STOP_ADJECTIVES).
     */
    public static void resetGeneralizationVocabularies() {
        GeneralizationVocabulary.resetAll();
    }

    /**
     * Returns true if the given adjective lemma is a stop adjective (evaluative/generic).
     */
    public static boolean isStopAdjective(String lemma) {
        return GeneralizationVocabulary.containsStopAdjective(lemma);
    }
    // endregion

    //region SYNONYM_MARKERS (registry owned by SynonymVocabulary)
    public static void resetSynonymMarkers() {
        SynonymVocabulary.reset();
    }

    public static void registerSynonymMarkers(Collection<String> markers) {
        SynonymVocabulary.register(markers);
    }

    /**
     * Returns true if the given lemma is a synonym-marker verb — the predicate
     * of a defining construction such as "X, also known as Y", "X, termed Y",
     * or "X, referred to as Y" (see SYNONYMS.md §1).
     */
    public static boolean isSynonymMarker(String lemma) {
        return SynonymVocabulary.contains(lemma);
    }

    /**
     * Convenience reset for all synonym vocabularies.
     */
    public static void resetSynonymVocabularies() {
        SynonymVocabulary.reset();
    }
    // endregion

    private TypedDependencyPredicates() {
    }

}

