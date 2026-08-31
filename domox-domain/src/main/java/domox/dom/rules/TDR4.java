package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 4)
/*
 * Purpose: Likely identifies classes from direct objects (dobj).
 * Example: "The system processes a payment." → payment is identified as a class.
 * Candidate Type: ClassCdd.
 */
public class TDR4 extends TypedDependencyRule {

    @Override
    public boolean when() {
        if (currentTd == null) {
            return false;
        }
        boolean answer = false;
        if (currentTd.dobj() || currentTd.iobj() || currentTd.pobj()) {
            if (currentTd.isVerbA() && currentTd.isNounB()) {
                if (currentTd.isBasicAttributeB() || isBlockedVerb(currentTd.getA())) {
                    answer = true;
                }
            }
        }
        return answer;
    }

    @Then
    public void then() {
        if (previousTd != null && previousTd.compound()) {
            result = "compound(" + currentTd.getB() + ") + Compound(" + currentTd.getA() + ")";
        } else {
            result = "dobj(" + currentTd.getB() + ")";
        }

        // Phase 1: record the match
        String className = determineClassName();
        String propertyName = currentTd.getB();
        if (ruleMatches != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "PropertyCdd",
                    propertyName,
                    "ClassCdd",
                    className,
                    result
            );
        }
    }

    private boolean isBlockedVerb(String verb) {
        return verb.equalsIgnoreCase("entered") ||
                verb.equalsIgnoreCase("inputted") ||
                verb.equalsIgnoreCase("saved") ||
                verb.equalsIgnoreCase("added") ||
                verb.equalsIgnoreCase("has");
    }
}