package domox.dom.rules;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the candidate + rule signature shared by {@code Candidate} (in-memory de-duplication) and
 * {@code RuleMatches} (the repository-query equivalent): two matches are the same finding only when
 * all six signature fields agree, the first occurrence wins, and the input list is never mutated.
 */
class RuleMatchSignatureTest {

    // --- equal ---

    @Test
    void equal_isTrueForIdenticalSignatures() {
        assertThat(RuleMatchSignature.equal(
                match("TDR10", "ClassCdd", "Customer", null, null, "an actor"),
                match("TDR10", "ClassCdd", "Customer", null, null, "an actor"))).isTrue();
    }

    @Test
    void equal_isTrueForTwoNullRelatedFields() {
        assertThat(RuleMatchSignature.equal(
                match("TDR1", "ClassCdd", "Customer", null, null, null),
                match("TDR1", "ClassCdd", "Customer", null, null, null))).isTrue();
    }

    @Test
    void equal_isFalseWhenAnySignatureFieldDiffers() {
        final RuleMatch base = match("TDR10", "ClassCdd", "Customer", "Order", "places", "an actor");

        assertThat(RuleMatchSignature.equal(base, match("TDR11", "ClassCdd", "Customer", "Order", "places", "an actor")))
                .as("ruleClassName").isFalse();
        assertThat(RuleMatchSignature.equal(base, match("TDR10", "ActionCdd", "Customer", "Order", "places", "an actor")))
                .as("candidateType").isFalse();
        assertThat(RuleMatchSignature.equal(base, match("TDR10", "ClassCdd", "Client", "Order", "places", "an actor")))
                .as("candidateName").isFalse();
        assertThat(RuleMatchSignature.equal(base, match("TDR10", "ClassCdd", "Customer", "Product", "places", "an actor")))
                .as("relatedCandidateType").isFalse();
        assertThat(RuleMatchSignature.equal(base, match("TDR10", "ClassCdd", "Customer", "Order", "owns", "an actor")))
                .as("relatedCandidateName").isFalse();
        assertThat(RuleMatchSignature.equal(base, match("TDR10", "ClassCdd", "Customer", "Order", "places", "a role")))
                .as("description").isFalse();
    }

    @Test
    void equal_isFalseForNullOperands() {
        final RuleMatch one = match("TDR1", "ClassCdd", "Customer", null, null, null);

        assertThat(RuleMatchSignature.equal(null, one)).isFalse();
        assertThat(RuleMatchSignature.equal(one, null)).isFalse();
        assertThat(RuleMatchSignature.equal(null, null)).isFalse();
    }

    // --- contains ---

    @Test
    void contains_isFalseForAnEmptyList() {
        assertThat(RuleMatchSignature.contains(List.of(), match("TDR1", "ClassCdd", "Customer", null, null, null)))
                .isFalse();
    }

    @Test
    void contains_isTrueWhenAnEquivalentMatchIsPresent() {
        final List<RuleMatch> matches = List.of(match("TDR1", "ClassCdd", "Customer", null, null, null));

        assertThat(RuleMatchSignature.contains(matches, match("TDR1", "ClassCdd", "Customer", null, null, null)))
                .isTrue();
    }

    @Test
    void contains_isFalseWhenNoEquivalentMatchIsPresent() {
        final List<RuleMatch> matches = List.of(match("TDR1", "ClassCdd", "Customer", null, null, null));

        assertThat(RuleMatchSignature.contains(matches, match("TDR2", "ClassCdd", "Customer", null, null, null)))
                .isFalse();
    }

    // --- deduplicate ---

    @Test
    void deduplicate_keepsTheFirstOccurrenceOfEachSignatureInOrder() {
        final RuleMatch firstA = match("TDR1", "ClassCdd", "Customer", null, null, null);
        final RuleMatch b = match("TDR2", "ClassCdd", "Order", null, null, null);
        final RuleMatch secondA = match("TDR1", "ClassCdd", "Customer", null, null, null);

        final List<RuleMatch> deduped = RuleMatchSignature.deduplicate(List.of(firstA, b, secondA));

        assertThat(deduped).containsExactly(firstA, b);
    }

    @Test
    void deduplicate_returnsAFreshMutableListAndLeavesTheInputUntouched() {
        final List<RuleMatch> input = new ArrayList<>(List.of(
                match("TDR1", "ClassCdd", "Customer", null, null, null),
                match("TDR1", "ClassCdd", "Customer", null, null, null)));

        final List<RuleMatch> deduped = RuleMatchSignature.deduplicate(input);

        assertThat(deduped).isNotSameAs(input).hasSize(1);
        assertThat(input).as("input untouched").hasSize(2);
        deduped.add(match("TDR9", "ClassCdd", "Other", null, null, null));   // must not throw
        assertThat(deduped).hasSize(2);
    }

    @Test
    void deduplicate_handlesEmptyAndSingleElementLists() {
        assertThat(RuleMatchSignature.deduplicate(List.of())).isEmpty();

        final RuleMatch only = match("TDR1", "ClassCdd", "Customer", null, null, null);
        assertThat(RuleMatchSignature.deduplicate(List.of(only))).containsExactly(only);
    }

    // --- helper ---

    private static RuleMatch match(String ruleClassName, String candidateType, String candidateName,
                                   String relatedType, String relatedName, String description) {
        final RuleMatch match = new RuleMatch();
        match.setRuleClassName(ruleClassName);
        match.setCandidateType(candidateType);
        match.setCandidateName(candidateName);
        match.setRelatedCandidateType(relatedType);
        match.setRelatedCandidateName(relatedName);
        match.setDescription(description);
        return match;
    }
}
