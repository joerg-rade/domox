package domox.dom.rules;

import domox.dom.nlp.TypedDependency;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Derives the UML multiplicity of one end of an association from the <em>sentence the association
 * was extracted from</em>.
 *
 * <p><b>Why it exists.</b> {@link TDR25} ({@code nummod(E1, CD)}) and {@link TDR26}
 * ({@code det(E1, DT)}) already fire on the multiplicity words of a sentence, but the value only ever
 * reached the persisted {@code RuleMatch.description} — the {@code CollaboratorCdd} columns
 * {@code sourceCardinality}/{@code targetCardinality} stayed empty, so the exported diagram had no
 * edge carrying a multiplicity.  This class turns the rule matches of one sentence into the value
 * those columns want.</p>
 *
 * <p><b>Why it is a pure collaborator.</b> The mapping is a decision with branches
 * (which rule, which end, which of two competing values) and it is exercised on every association of
 * every analysis run — the natural home for a branch this fiddly is a function with a unit test, not
 * an extra branch inside {@link CandidateFactory} (which pitest floors at 95 %).  It has no
 * dependencies beyond the domain objects it is handed, so it is testable without Spring, a database
 * or a running CoreNLP.</p>
 *
 * <p><b>What it reads.</b> The value is re-derived from the {@link TypedDependency} the rule fired on
 * — {@code MultiplicityNotation.umlNotation(token)} — and <em>not</em> parsed back out of
 * {@code RuleMatch.description}.  The persisted descriptions predate the normalisation (they still
 * read {@code multiplicity.add(pet, 01)} / {@code (pet, N)}) and are therefore not a trustworthy
 * source until the corpus is re-analysed.</p>
 *
 * <p><b>Which value wins.</b> An explicit count ({@code TDR25}, {@code "2 pets"}) beats an inferred
 * determiner ({@code TDR26}, {@code "many pets"}), because the text stated a number; within one rule
 * the first match in the list wins, so the result does not depend on iteration order of a query.
 * When the sentence says nothing about the class, the answer is the empty string — the columns are
 * {@code NOT NULL} and {@code ""} is their "absent" value, not a guessed {@code "*"}.</p>
 *
 * <p>A utility rather than a {@code @Service} — same shape as {@link MultiplicityNotation} and
 * {@link UseCaseBlockedNouns}.</p>
 */
public final class MultiplicityEvidence {

    /** The rules that record a multiplicity, in precedence order (explicit count first). */
    static final String EXPLICIT_COUNT_RULE = "TDR25";
    static final String DETERMINER_RULE = "TDR26";

    private static final Set<String> MULTIPLICITY_RULES = Set.of(EXPLICIT_COUNT_RULE, DETERMINER_RULE);

    private MultiplicityEvidence() {
    }

    /**
     * Returns the UML multiplicity of {@code className} as stated by {@code sentenceMatches}, or
     * {@code ""} when that sentence states none for it.
     *
     * @param sentenceMatches every {@link RuleMatch} whose typed dependency belongs to the sentence
     *                        the association was extracted from (may be {@code null} or empty)
     * @param className       the name of the association end to look up, matched case-insensitively
     *                        (the multiplicity rules capitalise, the association rules may not)
     */
    public static String cardinalityFor(final List<RuleMatch> sentenceMatches, final String className) {
        if (sentenceMatches == null || className == null) {
            return "";
        }
        String explicitCount = null;
        String determiner = null;
        for (final RuleMatch match : sentenceMatches) {
            final String value = valueOf(match, className);
            if (value == null) {
                continue;
            }
            if (EXPLICIT_COUNT_RULE.equals(match.getRuleClassName())) {
                explicitCount = explicitCount != null ? explicitCount : value;
            } else {
                determiner = determiner != null ? determiner : value;
            }
        }
        if (explicitCount != null) {
            return explicitCount;
        }
        return determiner != null ? determiner : "";
    }

    /**
     * The UML notation this match states for {@code className}, or {@code null} when the match is not
     * a multiplicity match for that class.
     */
    private static String valueOf(final RuleMatch match, final String className) {
        // NOTE: the rule-class check must reject null *before* the set lookup — Set.of(...).contains(null)
        // throws NullPointerException, and a hand-built match (a unit test, or a detached entity) has no
        // rule class name.
        if (match == null
                || match.getRuleClassName() == null
                || !MULTIPLICITY_RULES.contains(match.getRuleClassName())) {
            return null;
        }
        if (match.getCandidateName() == null || !match.getCandidateName().equalsIgnoreCase(className)) {
            return null;
        }
        final TypedDependency dependency = match.getTypedDependency();
        if (dependency == null || dependency.getB() == null) {
            return null;
        }
        // TDR26 compares the determiner case-insensitively (see its when()); TDR25's token is a number.
        final String token = DETERMINER_RULE.equals(match.getRuleClassName())
                ? dependency.getB().toLowerCase(Locale.ROOT)
                : dependency.getB();
        return MultiplicityNotation.umlNotation(token);
    }
}
