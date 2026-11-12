package domox.dom.nlp;

/**
 * <b>Predicates on the relation type of a {@link TypedDependency}</b> — the grammatical relation
 * between governor (A) and dependent (B).
 * <p>
 * Carved out of {@code TypedDependencyPredicates}, which had grown into a grab-bag of three unrelated
 * kinds of predicate (POS-of-A/B, dependency type, and vocabulary lookup) plus a pass-through layer
 * for the six vocabulary registries.  This is the relation half; {@link DependencyPosPredicates}
 * answers "what part of speech is this?" and {@link DependencyVocabularyPredicates} answers "does a
 * vocabulary know this word?".
 * <p>
 * The names follow the Universal Dependencies relations the rules were written against (the
 * Stanford-UD names in {@link TdType}), not the older Stanford Dependencies ones.
 */
public final class DependencyTypePredicates {

    private DependencyTypePredicates() {
    }

    public static boolean isNsubj(final TypedDependency td) {
        return td.getType() == TdType.NSUBJ;
    }

    public static boolean isNsubjPass(final TypedDependency td) {
        return td.getType() == TdType.NSUBJPASS;
    }

    public static boolean isCompound(final TypedDependency td) {
        return td.getType() == TdType.COMPOUND;
    }

    public static boolean dobj(final TypedDependency td) {
        // Direct object: in Stanford UD, this is 'obj'
        return td.getType().equals(TdType.OBJ);
    }

    public static boolean iobj(final TypedDependency td) {
        // Indirect object: check for indirect object patterns in obl types
        return td.getType().equals(TdType.OBL);
    }

    public static boolean pobj(final TypedDependency td) {
        // Prepositional object: check for obl types
        return td.getType().toString().startsWith("OBL");
    }

    public static boolean amod(final TypedDependency td) {
        return td.getType().equals(TdType.AMOD);
    }

    public static boolean advmod(final TypedDependency td) {
        return td.getType().equals(TdType.ADVMOD);
    }

    public static boolean nmodOf(final TypedDependency td) {
        return td.getType().equals(TdType.NMOD_OF);
    }

    public static boolean nmodIn(final TypedDependency td) {
        // in: use obl:in or similar
        return td.getType().equals(TdType.OBL_IN) || td.getType().equals(TdType.NMOD);
    }

    public static boolean nmodTo(final TypedDependency td) {
        // to: use obl:to or similar
        return td.getType().equals(TdType.OBL_TO);
    }

    public static boolean nmodFor(final TypedDependency td) {
        return td.getType().equals(TdType.NMOD_FOR) || td.getType().equals(TdType.OBL_FOR);
    }

    public static boolean nmodFrom(final TypedDependency td) {
        // from: check for obl types
        return td.getType().toString().contains("FROM") || td.getType().toString().contains("from");
    }

    public static boolean nmodAs(final TypedDependency td) {
        // as: check obl types or nmod
        return td.getType().toString().contains("AS") || td.getType().toString().contains("as");
    }

    public static boolean nmodBy(final TypedDependency td) {
        return td.getType().equals(TdType.OBL_BY) || td.getType().equals(TdType.NMOD);
    }

    public static boolean nmodAgent(final TypedDependency td) {
        // agent: typically obl:agent or nmod:agent, might not exist exactly
        return td.getType().toString().contains("AGENT") || td.getType().toString().contains("agent");
    }

    public static boolean nmodWith(final TypedDependency td) {
        return td.getType().equals(TdType.NMOD_WITH) || td.getType().equals(TdType.OBL_WITH);
    }

    public static boolean nmodPoss(final TypedDependency td) {
        return td.getType().equals(TdType.NMOD_POSS);
    }

    public static boolean appos(final TypedDependency td) {
        return td.getType().equals(TdType.APPOS);
    }

    public static boolean acl(final TypedDependency td) {
        return td.getType().equals(TdType.ACL);
    }

    public static boolean aclRelcl(final TypedDependency td) {
        return td.getType().equals(TdType.ACL_RELCL) || td.getType().equals(TdType.ACL);
    }

    public static boolean nmodAnd(final TypedDependency td) {
        return td.getType().equals(TdType.CONJ_AND);
    }

    public static boolean nmodOr(final TypedDependency td) {
        return td.getType().equals(TdType.CONJ_OR);
    }

    public static boolean mark(final TypedDependency td) {
        return td.getType().equals(TdType.MARK);
    }

    public static boolean xcomp(final TypedDependency td) {
        return td.getType().equals(TdType.XCOMP);
    }

    public static boolean advcl(final TypedDependency td) {
        return td.getType().equals(TdType.ADVCL);
    }

    public static boolean nummod(final TypedDependency td) {
        return td.getType().equals(TdType.NUMMOD);
    }

    public static boolean det(final TypedDependency td) {
        return td.getType().equals(TdType.DET);
    }

    public static boolean neg(final TypedDependency td) {
        // neg doesn't exist in TdType, so check if it might be in a string or as a pattern
        var tdType = td.getType().toString();
        return tdType.contains("NEG") || tdType.contains("neg");
    }

    public static boolean cop(final TypedDependency td) {
        return td.getType().equals(TdType.COP);
    }
}
