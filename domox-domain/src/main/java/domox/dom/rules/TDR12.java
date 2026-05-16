package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 12)
public class TDR12 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        return currentTd.compound() &&
               !nextTd.nsubj() &&
               !nextTd.dobj() &&
               currentTd.isNounA() &&
               currentTd.isNounB();
    }

    @Then
    public void then() {
        boolean aIsBasicAttrib = currentTd.isBasicAttributeA();
        boolean bIsBasicAttrib = currentTd.isBasicAttributeB();

        if (aIsBasicAttrib && !bIsBasicAttrib) {
            // if A=Noun and B = Noun and A=Basic_Attrib and B≠ Basic_Attrib then
            // Attributes.add(B + A), Entity.add(B)
            result = "Attributes.add(" + currentTd.getB() + " " + currentTd.getA() + "), Entity.add(" + currentTd.getB() + ")";
        } else if (!aIsBasicAttrib && bIsBasicAttrib) {
            // else if A=Noun and B = Noun and A≠Basic_Attrib and B= Basic_Attrib then
            // Attributes.add(A + B), Entity.add(A)
            result = "Attributes.add(" + currentTd.getA() + " " + currentTd.getB() + "), Entity.add(" + currentTd.getA() + ")";
        } else if (aIsBasicAttrib && bIsBasicAttrib) {
            // else if A=Noun and B = Noun and A=Basic_Attrib and B= Basic_Attrib then
            // Attributes.add(B+A)
            result = "Attributes.add(" + currentTd.getB() + " " + currentTd.getA() + ")";
        } else if (!aIsBasicAttrib && !bIsBasicAttrib) {
            // else if A=Noun and B = Noun and A≠Basic_Attrib and B≠ Basic_Attrib then
            // Entity.add(B+A)
            result = "Entity.add(" + currentTd.getB() + " " + currentTd.getA() + ")";
        }
    }

}

