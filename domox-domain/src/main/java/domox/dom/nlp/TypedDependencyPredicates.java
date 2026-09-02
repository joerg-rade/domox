package domox.dom.nlp;

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

    public static boolean isNounB(TypedDependency td) {
        return td.getDependentPos() != null && NOUN_TYPES.contains(td.getDependentPos());
    }

    public static boolean isCompound(TypedDependency td) {
        return td.getType() == TdType.COMPOUND;
    }

    //region BASIC_ATTRIB
    private static final Set<String> BASIC_ATTRIB = new HashSet<>(Set.of(
            "name", "number", "type", "address", "level", "date", "time", "owner"));

    /**
     * Reset to the default set (for testing purposes).
     */
    public static void resetBasicAttributes() {
        BASIC_ATTRIB.clear();
        BASIC_ATTRIB.addAll(Set.of(
                "name", "number", "type", "address", "level", "date", "time", "owner"));
    }

    public static void registerBasicAttributes(Collection<String> attributes) {
        if (attributes != null) {
            BASIC_ATTRIB.addAll(attributes);
        }
    }

    public static boolean isBasicAttributeA(TypedDependency td) {
        final String aName = td.getA();
        if (aName == null) return false;
        return BASIC_ATTRIB.contains(aName);
    }

    public static boolean isBasicAttributeB(TypedDependency td) {
        return td.getB() != null && BASIC_ATTRIB.contains(td.getB());
    }
    // end region

    //region ACTION_VERBS & SERVICE_NOUNS
    private static final Set<String> ACTION_VERBS = new HashSet<>(Set.of(
            "offer", "provide", "sell", "purchase", "buy", "adopt", "order",
            "deliver", "train", "groom", "board", "organize", "host",
            "maintain", "capture", "facilitate", "connect", "partner",
            "operate", "care", "attract", "allow", "leave", "book"));

    private static final Set<String> SERVICE_NOUNS = new HashSet<>(Set.of(
            "grooming", "boarding", "daycare", "adoption", "training",
            "photography", "insurance", "delivery", "socialization",
            "fashion", "workshop", "event", "class", "service"));

    /**
     * Reset to the default action vocabularies (for testing purposes).
     */
    public static void resetActionVocabularies() {
        ACTION_VERBS.clear();
        ACTION_VERBS.addAll(Set.of(
                "offer", "provide", "sell", "purchase", "buy", "adopt", "order",
                "deliver", "train", "groom", "board", "organize", "host",
                "maintain", "capture", "facilitate", "connect", "partner",
                "operate", "care", "attract", "allow", "leave", "book"));
        SERVICE_NOUNS.clear();
        SERVICE_NOUNS.addAll(Set.of(
                "grooming", "boarding", "daycare", "adoption", "training",
                "photography", "insurance", "delivery", "socialization",
                "fashion", "workshop", "event", "class", "service"));
    }

    public static void registerActionVerbs(Collection<String> verbs) {
        if (verbs != null) {
            verbs.forEach(v -> {
                if (v != null) ACTION_VERBS.add(v.toLowerCase(Locale.ROOT));
            });
        }
    }

    public static void registerServiceNouns(Collection<String> nouns) {
        if (nouns != null) {
            nouns.forEach(n -> {
                if (n != null) SERVICE_NOUNS.add(n.toLowerCase(Locale.ROOT));
            });
        }
    }

    public static boolean isActionVerbA(TypedDependency td) {
        return td.getA() != null && ACTION_VERBS.contains(td.getA().toLowerCase(Locale.ROOT));
    }

    public static boolean isActionVerbB(TypedDependency td) {
        return td.getB() != null && ACTION_VERBS.contains(td.getB().toLowerCase(Locale.ROOT));
    }

    public static boolean isServiceNounA(TypedDependency td) {
        return td.getA() != null && SERVICE_NOUNS.contains(td.getA().toLowerCase(Locale.ROOT));
    }

    public static boolean isServiceNounB(TypedDependency td) {
        return td.getB() != null && SERVICE_NOUNS.contains(td.getB().toLowerCase(Locale.ROOT));
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
        return td.getType().equals(TdType.NMOD_WITH) || td.getType().equals(TdType.OBL_WIN);
    }


    public static boolean nmodPoss(TypedDependency td) {
        return td.getType().equals(TdType.NMOD_POSS);
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

    private TypedDependencyPredicates() {
    }

}

