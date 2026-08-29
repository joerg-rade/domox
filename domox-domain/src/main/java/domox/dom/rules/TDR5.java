package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 5)
/**
 * Purpose: Likely identifies classes from adjectival modifiers (amod).
 * Example: "The red car is fast." → Car is identified as a class, with red as a potential attribute.
 * Candidate Type: ClassCdd.
 */
public class TDR5 extends TypedDependencyRule {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
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
        if ((previousTd.amod() || previousTd.advmod()) && previousTd.isAdjectiveB()) {
            // Attributes.add(amod(B) + amod(A))
            result = "amod(" + currentTd.getB() + ") + amod(" + currentTd.getA() + ")";
        } else {
            // Attributes.add(dobj(B))
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

