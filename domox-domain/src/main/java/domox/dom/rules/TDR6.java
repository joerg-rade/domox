package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 6)
public class TDR6 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        return currentTd.nmodOf();
    }

    @Then
    public void then() {
        boolean aIsBasicAttrib = currentTd.isBasicAttributeA();
        boolean bIsBasicAttrib = currentTd.isBasicAttributeB();

        if (aIsBasicAttrib && !bIsBasicAttrib) {
            // if A=noun and B=Noun and A = Basic_Attrib and B≠Basic_Attrib then
            // Entity.add(B), Attributes.add(A)
            result = "Entity.add(" + currentTd.getB() + "), Attributes.add(" + currentTd.getA() + ")";
        } else if (!aIsBasicAttrib && !bIsBasicAttrib) {
            // if A=noun and B=Noun and A≠Basic_Attrib and B≠Basic_Attrib then
            // Entity.add(A), Entity.add(B)
            result = "Entity.add(" + currentTd.getA() + "), Entity.add(" + currentTd.getB() + ")";
        } else if (aIsBasicAttrib && bIsBasicAttrib) {
            // if A=noun and B=Noun and A=Basic_Attrib and B=Basic_Attrib then
            // Attributes.add(A + "of" + B)
            result = "Attributes.add(" + currentTd.getA() + " of " + currentTd.getB() + ")";
        }
    }

}

