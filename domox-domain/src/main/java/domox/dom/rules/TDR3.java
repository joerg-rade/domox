package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 3)
/*
 * Purpose: Likely identifies compound nouns (e.g., "order detail" → OrderDetail).
 * Example: "The order detail is missing." → OrderDetail is identified as a class.
 * Candidate Type: ClassCdd.
 */
public class TDR3 extends TypedDependencyRule {

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        boolean answer = false;
        if (currentTd.dobj() || currentTd.iobj() || currentTd.pobj()) {
            if (currentTd.isVerbA() && currentTd.isNounB() && !currentTd.isBasicAttributeB()) {
                if (!previousTd.amod() && !previousTd.advmod()) {
                    String verbA = currentTd.getA();
                    if (!isBlockedVerb(verbA)) {
                        answer = true;
                    }
                }
            }
        }
        return answer;
    }

    @Then
    public void then() {
        if (previousTd.compound()) {
            // Entity.add(compound(B) + Compound(A))
            result = "compound(" + currentTd.getB() + ") + Compound(" + currentTd.getA() + ")";
        } else {
            // Entity.add(dobj(B))
            result = "dobj(" + currentTd.getB() + ")";
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

