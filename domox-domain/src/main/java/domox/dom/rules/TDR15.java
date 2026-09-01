package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.PartOfSpeechType;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 15)
public class TDR15 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: nsubjpass(VBN, E1) and (nmod:agent(VBN, E2) or nmod:by(VBN, E2))
        // currentTd = nsubjpass(VBN, E1), nextTd = nmod:agent/nmod:by(VBN, E2),
        // both dependencies must be governed by the same VBN
        return isNsubjPass(currentTd)
                && currentTd.getGovernorPos() == PartOfSpeechType.VBN
                && isNounB(currentTd)
                && nextTd != null
                && (nmodAgent(nextTd) || nmodBy(nextTd))
                && isNounB(nextTd)
                && nextTd.getGovernorIndex() == currentTd.getGovernorIndex();
    }

    @Override
    @Then
    public void then() {
        // relationship.add(E1 (VBN) E2)
        String vbn = currentTd.getA();
        String e1 = currentTd.getB();
        String e2 = nextTd.getB();
        result = "relationship.add(" + e1 + " (" + vbn + ") " + e2 + ")";

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