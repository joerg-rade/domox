package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 1)
/*
 * Purpose: Identifies entities (classes) from nsubj or nsubjpass dependencies where the verb is the governor and the noun is the dependent.
 * Example: "The customer places an order." → customer and order are identified as classes.
 * Candidate Type: ClassCdd.
 */
public class TDR1 extends TypedDependencyRule {

    @Override
    public boolean when() {
        if (currentTd == null) {
            return false;
        }

        boolean answer = false;
        if (currentTd.nsubj() || currentTd.nsubjpass()) {
            if (currentTd.isVerbA() && currentTd.isNounB() && !currentTd.isBasicAttributeB()) {
                answer = true;
            }
        }
        return answer;
    }

    @Then
    public void then() {
        if (previousTd != null && previousTd.compound()) {
            result = "compound(" + currentTd.getB() + ") + Compound(" + currentTd.getA() + ")";
        } else {
            result = "nsubj(" + currentTd.getB() + ")";
        }

        // in case of a compound term, each part should be capitalized and the concatenated

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "ClassCdd",
                    determineClassName(),
                    null,
                    null,
                    result);
        }
    }

    @Override
    /*
     * Determines the class name for this dependency.
     * The class name is derived from the dependent noun (currentTd.getB()).
     * If the previous dependency is a compound, both parts are capitalized
     * and concatenated: dependent part first, then the head noun.
     * Example: compound(document, draft) → "DraftDocument"
     *
     * @return The name of the class.
     */
    protected String determineClassName() {
        if (previousTd != null && previousTd.compound()) {
            // compound(governor=document, dependent=draft) → "DraftDocument"
            String head = capitalizeFirstLetter(currentTd.getB());
            String modifier = capitalizeFirstLetter(previousTd.getB());
            return modifier + head;
        }
        return capitalizeFirstLetter(currentTd.getB());
    }

}