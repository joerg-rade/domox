package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 11)
/*
 * Purpose: Identifies entities/attributes from adjectival modifiers (amod).
 * Example: "the full name" → "full name" is an attribute; "the active order" → order is an entity.
 * Candidate Type: PropertyCdd (attribute) or ClassCdd (entity).
 */
public class TDR11 extends TypedDependencyRule {

    @Override
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependencies = amod(A,B) with A=Noun and B=JJ
        return currentTd.amod() && currentTd.isNounA() && currentTd.isAdjectiveB();
    }

    @Then
    public void then() {
        if (currentTd.isBasicAttributeA()) {
            // if A=Noun and B = JJ and A=basic_Attrib then Attributes.add(B + A)
            result = "Attributes.add(" + currentTd.getB() + " " + currentTd.getA() + ")";

            // Phase 1: persist the match — combined attribute "B A" (e.g. "full name")
            if (ruleMatches != null && currentTd != null) {
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd",
                        currentTd.getB() + " " + currentTd.getA(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getA()), result);
            }
        } else {
            // else if A=Noun and B=JJ and A ≠ Basic_Attrib then Entity.add(A)
            result = "Entity.add(" + currentTd.getA() + ")";

            // Phase 1: persist the match — A is an entity
            if (ruleMatches != null && currentTd != null) {
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getA()), null, null, result);
            }
        }
    }

}