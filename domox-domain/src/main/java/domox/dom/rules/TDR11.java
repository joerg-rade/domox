package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 11)
public class TDR11 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        return currentTd.amod() && currentTd.isNounA() && currentTd.isAdjectiveB();
    }

    @Then
    public void then() {
        if (currentTd.isBasicAttributeA()) {
            // if A=Noun and B = JJ and A=basic_Attrib then Attributes.add(B + A)
            result = "Attributes.add(" + currentTd.getB() + " " + currentTd.getA() + ")";
        } else {
            // else if A=Noun and B=JJ and A ≠ Basic_Attrib then Entity.add(A)
            result = "Entity.add(" + currentTd.getA() + ")";
        }
    }

}

