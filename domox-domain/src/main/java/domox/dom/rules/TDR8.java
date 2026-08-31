package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 8)
/*
 * Purpose: Likely identifies classes from prepositional phrases (prep).
 * Example: "The report for the project is ready." → Project is identified as a class.
 * Candidate Type: ClassCdd.
 */
public class TDR8 extends TypedDependencyRule {

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependency= nmod:to(A,B) OR nmod:for(A,B) OR nmod:from(A,B) OR nmod:as(A,B)
        // if B=Noun then
        return (currentTd.nmodTo() || currentTd.nmodFor() || currentTd.nmodFrom() || currentTd.nmodAs())
                && currentTd.isNounB();
    }

    @Then
    public void then() {
        // Entity.add(B)
        String b = currentTd.getB();
        result = "Entity.add(" + b + ")";
    }

}

