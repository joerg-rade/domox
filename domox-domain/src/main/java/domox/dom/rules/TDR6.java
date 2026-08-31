package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 6)
public class TDR6 extends TypedDependencyRule {

    @Override
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        return currentTd.isNounA() && currentTd.isNounB() && currentTd.nmodOf();
    }

    @Then
    public void then() {
        boolean aIsBasicAttrib = currentTd.isBasicAttributeA();
        boolean bIsBasicAttrib = currentTd.isBasicAttributeB();

        if (aIsBasicAttrib && !bIsBasicAttrib) {
            // if A=noun and B=Noun and A = Basic_Attrib and B≠Basic_Attrib then
            // Entity.add(B), Attributes.add(A)
            result = "Entity.add(" + currentTd.getB() + "), Attributes.add(" + currentTd.getA() + ")";
            if (ruleMatches != null && currentTd != null) {
                // B is the entity, A is an attribute of B
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getB()), null, null, result);
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd",
                        currentTd.getA(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getB()), result);
            }
        } else if (!aIsBasicAttrib && !bIsBasicAttrib) {
            // if A=noun and B=Noun and A≠Basic_Attrib and B≠Basic_Attrib then
            // Entity.add(A), Entity.add(B)
            result = "Entity.add(" + currentTd.getA() + "), Entity.add(" + currentTd.getB() + ")";
            if (ruleMatches != null && currentTd != null) {
                // Both A and B are entities
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getA()), null, null, result);
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getB()), null, null, result);
            }
        } else if (aIsBasicAttrib && bIsBasicAttrib) {
            // if A=noun and B=Noun and A=Basic_Attrib and B=Basic_Attrib then
            // Attributes.add(A + "of" + B)
            result = "Attributes.add(" + currentTd.getA() + " of " + currentTd.getB() + ")";
            if (ruleMatches != null && currentTd != null) {
                // Combined attribute: A of B
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd",
                        currentTd.getA() + "Of" + capitalizeFirstLetter(currentTd.getB()),
                        null, null, result);
            }
        }
    }

}