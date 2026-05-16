package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 13)
public class TDR13 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        return (currentTd.nmodAnd() || currentTd.nmodOr()) &&
               currentTd.isNounA() &&
               currentTd.isNounB();
    }

    @Then
    public void then() {
        boolean aIsBasicAttrib = currentTd.isBasicAttributeA();
        boolean bIsBasicAttrib = currentTd.isBasicAttributeB();

        if (aIsBasicAttrib && bIsBasicAttrib) {
            // if A=Noun and B = Noun and A=Basic_Attrib and B= Basic_Attrib then
            // Attributes.add(A), Attributes.add(B)
            result = "Attributes.add(" + currentTd.getA() + "), Attributes.add(" + currentTd.getB() + ")";
        } else if (!aIsBasicAttrib && !bIsBasicAttrib) {
            // else if A=Noun and B = Noun and A≠Basic_Attrib and B≠ Basic_Attrib then
            // Entity.add(A), Entity.add(B)
            result = "Entity.add(" + currentTd.getA() + "), Entity.add(" + currentTd.getB() + ")";
        }
    }

}

