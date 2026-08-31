package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 12)
public class TDR12 extends TypedDependencyRule {

    @Override
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: compound(A,B) and nextTD≠nsubj and nextTD≠dobj, with A=Noun and B=Noun
        // A null nextTd satisfies "nextTD≠nsubj and nextTD≠dobj"
        boolean nextIsNotSubjectOrObject = nextTd == null || (!nextTd.nsubj() && !nextTd.dobj());
        return currentTd.compound()
                && nextIsNotSubjectOrObject
                && currentTd.isNounA()
                && currentTd.isNounB();
    }

    @Override
    public void then() {
        boolean aIsBasicAttrib = currentTd.isBasicAttributeA();
        boolean bIsBasicAttrib = currentTd.isBasicAttributeB();

        if (aIsBasicAttrib && !bIsBasicAttrib) {
            // if A=Basic_Attrib and B≠Basic_Attrib then
            // Attributes.add(B + A), Entity.add(B)
            result = "Attributes.add(" + currentTd.getB() + " " + currentTd.getA()
                    + "), Entity.add(" + currentTd.getB() + ")";

            if (ruleMatches != null && currentTd != null) {
                // B is the entity, "B A" is an attribute of B
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getB()), null, null, result);
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd",
                        currentTd.getB() + " " + currentTd.getA(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getB()), result);
            }
        } else if (!aIsBasicAttrib && bIsBasicAttrib) {
            // else if A≠Basic_Attrib and B=Basic_Attrib then
            // Attributes.add(A + B), Entity.add(A)
            result = "Attributes.add(" + currentTd.getA() + " " + currentTd.getB()
                    + "), Entity.add(" + currentTd.getA() + ")";

            if (ruleMatches != null && currentTd != null) {
                // A is the entity, "A B" is an attribute of A
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getA()), null, null, result);
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd",
                        currentTd.getA() + " " + currentTd.getB(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getA()), result);
            }
        } else if (aIsBasicAttrib && bIsBasicAttrib) {
            // else if A=Basic_Attrib and B=Basic_Attrib then
            // Attributes.add(B+A)
            result = "Attributes.add(" + currentTd.getB() + " " + currentTd.getA() + ")";

            if (ruleMatches != null && currentTd != null) {
                // combined attribute "B A"
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd",
                        currentTd.getB() + " " + currentTd.getA(), null, null, result);
            }
        } else {
            // else A≠Basic_Attrib and B≠Basic_Attrib then
            // Entity.add(B+A)
            result = "Entity.add(" + currentTd.getB() + currentTd.getA() + ")";

            if (ruleMatches != null && currentTd != null) {
                // combined entity "BA", capitalized like TDR1's compound naming
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getB()) + capitalizeFirstLetter(currentTd.getA()),
                        null, null, result);
            }
        }
    }

}