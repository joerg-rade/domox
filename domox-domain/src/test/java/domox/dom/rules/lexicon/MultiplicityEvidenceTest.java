package domox.dom.rules.lexicon;

import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit guardrail for {@link MultiplicityEvidence} — the sentence-scoped lookup that turns the
 * multiplicity rule matches of one sentence into the value of an association end.
 *
 * <p>Before this existed, {@code TDR25}/{@code TDR26} recorded the multiplicity in
 * {@code RuleMatch.description} and nothing read it back, so every edge of the exported diagram had
 * an empty multiplicity. These cases pin the mapping (which rule, which token, which of two competing
 * values) and the "no evidence" answer, which must be {@code ""} rather than a guessed
 * {@code *}.</p>
 */
class MultiplicityEvidenceTest {

    /** A multiplicity match, as {@code TDR25}/{@code TDR26} record it: rule, capitalised class, token. */
    private static RuleMatch match(final String ruleClassName, final String candidateName, final String token) {
        final TypedDependency dependency = new TypedDependency();
        dependency.setGovernorLemma(candidateName.toLowerCase(java.util.Locale.ROOT));
        dependency.setDependentLemma(token);

        final RuleMatch match = new RuleMatch();
        match.setRuleClassName(ruleClassName);
        match.setCandidateType("ClassCdd");
        match.setCandidateName(candidateName);
        match.setTypedDependency(dependency);
        return match;
    }

    // --- the "no evidence" answer -------------------------------------------

    @Test
    void returnsEmptyWhenThereIsNoEvidence() {
        assertThat(MultiplicityEvidence.cardinalityFor(null, "Pet")).isEmpty();
        assertThat(MultiplicityEvidence.cardinalityFor(List.of(), "Pet")).isEmpty();
        assertThat(MultiplicityEvidence.cardinalityFor(List.of(), null)).isEmpty();
        assertThat(MultiplicityEvidence.cardinalityFor(List.of(match("TDR25", "Pet", "2")), null)).isEmpty();
    }

    @Test
    void ignoresMatchesThatAreNotMultiplicitiesOfTheRequestedClass() {
        final List<RuleMatch> matches = Arrays.asList(
                null,
                match("TDR14", "Pet", "2"),        // not a multiplicity rule
                match("TDR25", "Customer", "2"),   // a different class
                match("TDR25", "Pet", "2"));       // the one that counts

        assertThat(MultiplicityEvidence.cardinalityFor(matches, "Pet")).isEqualTo("2");
    }

    @Test
    void ignoresAMultiplicityMatchWithoutADependency() {
        final RuleMatch match = match("TDR25", "Pet", "2");
        match.setTypedDependency(null);

        assertThat(MultiplicityEvidence.cardinalityFor(List.of(match), "Pet")).isEmpty();
    }

    /** A hand-built (or detached) match carries no rule class name — it must be skipped, not throw. */
    @Test
    void ignoresAMatchWithoutARuleClassName() {
        final RuleMatch match = match("TDR25", "Pet", "2");
        match.setRuleClassName(null);

        assertThat(MultiplicityEvidence.cardinalityFor(List.of(match), "Pet")).isEmpty();
    }

    /** A multiplicity match that names no candidate cannot be attributed to a class. */
    @Test
    void ignoresAMatchWithoutACandidateName() {
        final RuleMatch match = match("TDR25", "Pet", "2");
        match.setCandidateName(null);

        assertThat(MultiplicityEvidence.cardinalityFor(List.of(match), "Pet")).isEmpty();
    }

    @Test
    void ignoresAMultiplicityMatchWithoutAToken() {
        final RuleMatch match = match("TDR25", "Pet", null);

        assertThat(MultiplicityEvidence.cardinalityFor(List.of(match), "Pet")).isEmpty();
    }

    // --- the values ----------------------------------------------------------

    @Test
    void translatesAnExplicitCount() {
        assertThat(MultiplicityEvidence.cardinalityFor(List.of(match("TDR25", "Pet", "01")), "Pet"))
                .as("TDR25's zero-padded token is the count 1")
                .isEqualTo("1");
    }

    @Test
    void translatesAQuantifyingDeterminer() {
        assertThat(MultiplicityEvidence.cardinalityFor(List.of(match("TDR26", "Pet", "many")), "Pet"))
                .isEqualTo("*");
        assertThat(MultiplicityEvidence.cardinalityFor(List.of(match("TDR26", "Pet", "some")), "Pet"))
                .isEqualTo("1..*");
        assertThat(MultiplicityEvidence.cardinalityFor(List.of(match("TDR26", "Pet", "A")), "Pet"))
                .as("a determiner is compared case-insensitively, as TDR26 itself does")
                .isEqualTo("1");
    }

    @Test
    void matchesTheClassNameCaseInsensitively() {
        assertThat(MultiplicityEvidence.cardinalityFor(List.of(match("TDR25", "Pet", "2")), "pet"))
                .isEqualTo("2");
        assertThat(MultiplicityEvidence.cardinalityFor(List.of(match("TDR25", "Pet", "2")), "PET"))
                .isEqualTo("2");
    }

    // --- precedence ----------------------------------------------------------

    @Test
    void anExplicitCountBeatsADeterminer() {
        final List<RuleMatch> matches = List.of(
                match("TDR26", "Pet", "many"),
                match("TDR25", "Pet", "2"));

        assertThat(MultiplicityEvidence.cardinalityFor(matches, "Pet"))
                .as("the text stated a number — that beats an inferred determiner")
                .isEqualTo("2");
    }

    @Test
    void theFirstMatchOfARuleWins() {
        final List<RuleMatch> matches = List.of(
                match("TDR25", "Pet", "2"),
                match("TDR25", "Pet", "3"));

        assertThat(MultiplicityEvidence.cardinalityFor(matches, "Pet"))
                .as("the result must not depend on the iteration order of a query")
                .isEqualTo("2");
    }
}
