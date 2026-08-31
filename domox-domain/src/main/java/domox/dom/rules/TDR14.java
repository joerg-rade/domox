package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 14)
public class TDR14 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: nsubj(Verb, E1) & dobj(Verb, E2)
        // currentTd = nsubj(Verb, E1), nextTd = dobj(Verb, E2),
        // both dependencies must be governed by the same Verb
        return currentTd.nsubj()
                && currentTd.isVerbA()
                && currentTd.isNounB()
                && nextTd != null
                && nextTd.dobj()
                && nextTd.isNounB()
                && nextTd.getGovernorIndex() == currentTd.getGovernorIndex();
    }

    @Override
    @Then
    public void then() {
        // relationship.add(E1 (Verb) E2)
        String verb = currentTd.getA();
        String e1 = currentTd.getB();
        String e2 = nextTd.getB();
        result = "relationship.add(" + e1 + " (" + verb + ") " + e2 + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "ClassCdd",
                    capitalizeFirstLetter(e1),
                    "ClassCdd",
                    capitalizeFirstLetter(e2),
                    result);
        }
    }

}