package domox.dom.rules.lexicon;

import domox.dom.rules.RuleMatch;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit guardrail for {@link AssociationLabel} — the verb drawn on an association's edge.
 *
 * <p>Every edge of the exported diagram used to be labelled with the candidate's own name
 * ({@code Customer_Premium}), because {@code CollaboratorCdd} stores only the two ends. The verb is
 * recorded by the association rules in their own explanation, in the spec's shape
 * {@code relationship.add(<source> (<verb>) <target>)}; these cases pin that extraction and the
 * fallback that keeps an edge labelled when no match records a verb.</p>
 */
class AssociationLabelTest {

    private static RuleMatch match(final String description) {
        final RuleMatch match = new RuleMatch();
        match.setRuleClassName("TDR16");
        match.setDescription(description);
        return match;
    }

    @Test
    void readsTheVerbFromTheRulesOwnExplanation() {
        assertThat(AssociationLabel.of(List.of(match("relationship.add(inventory (has) product)")), "fallback"))
                .isEqualTo("has");
    }

    @Test
    void keepsAMultiWordVerbPhrase() {
        assertThat(AssociationLabel.of(List.of(match("relationship.add(customer (enroll in) class)")), "fallback"))
                .isEqualTo("enroll in");
        assertThat(AssociationLabel.of(List.of(match("relationship.add(customer (register for) event)")), "fallback"))
                .isEqualTo("register for");
    }

    @Test
    void fallsBackToTheCandidateNameWhenNoMatchRecordsAVerb() {
        assertThat(AssociationLabel.of(null, "Customer_Premium")).isEqualTo("Customer_Premium");
        assertThat(AssociationLabel.of(List.of(), "Customer_Premium")).isEqualTo("Customer_Premium");
        assertThat(AssociationLabel.of(List.of(match("relationship.add(a, b)")), "Customer_Premium"))
                .as("no parenthesised verb")
                .isEqualTo("Customer_Premium");
        assertThat(AssociationLabel.of(List.of(match("relationship.add(a () b)")), "Customer_Premium"))
                .as("an empty verb group states nothing")
                .isEqualTo("Customer_Premium");
    }

    @Test
    void skipsMatchesWithoutADescription() {
        final RuleMatch withoutDescription = new RuleMatch();
        withoutDescription.setRuleClassName("TDR16");

        assertThat(AssociationLabel.of(
                Arrays.asList(withoutDescription, match("relationship.add(pet (take) list)")), "fallback"))
                .as("the first match that does state a verb wins")
                .isEqualTo("take");
        assertThat(AssociationLabel.of(Arrays.asList(withoutDescription, null), "fallback"))
                .isEqualTo("fallback");
    }
}
