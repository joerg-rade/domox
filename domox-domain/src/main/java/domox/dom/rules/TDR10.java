package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 10)
/**
 * Purpose: Likely identifies classes from passive constructions (nsubjpass).
 * Example: "The request was approved by the manager." → Request and Manager are identified as classes.
 * Candidate Type: ClassCdd.
 */
public class TDR10 extends TypedDependencyRule {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        return currentTd.nmodPoss();
    }

    @Then
    public void then() {
        if (currentTd.isNounA() && currentTd.isNounB()) {
            // if A=Noun and B = Noun then Entity.add(B), Attributes.add(A)
            result = "Entity.add(" + currentTd.getB() + "), Attributes.add(" + currentTd.getA() + ")";
        } else if (currentTd.isNounA() && !currentTd.isBasicAttributeB()) {
            // else if A=Noun and B= PREP ≠ Basic_Attrib then Attributes.add(B)
            result = "Attributes.add(" + currentTd.getB() + ")";
        }
    }

}

