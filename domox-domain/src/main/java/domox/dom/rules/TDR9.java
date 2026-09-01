package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 9)
/*
 * Purpose: Identifies entities/attributes from nmod:by, nmod:agent and nmod:with dependencies.
 * Example: "processed by the system" → system is identified as an entity.
 * Candidate Type: ClassCdd (entity) or PropertyCdd (basic attribute).
 */
public class TDR9 extends TypedDependencyRule {

    @Override
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependencies = nmod:by(A,B) OR nmod:agent(A,B) OR nmod:with(A,B)
        // if B=Noun then
        return (nmodBy(currentTd) || nmodAgent(currentTd) || nmodWith(currentTd))
                && isNounB(currentTd);
    }

    @Then
    public void then() {
        if (isBasicAttributeB(currentTd)) {
            // if B=Noun and B = Basic_Attrib then Attributes.add(B)
            result = "Attributes.add(" + currentTd.getB() + ")";

            // Phase 1: persist the match — B is an attribute of the governor A
            if (ruleMatches != null && currentTd != null) {
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd",
                        currentTd.getB(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getA()), result);
            }
        } else {
            // else if B=Noun and B ≠ Basic_Attrib then Entity.add(B)
            result = "Entity.add(" + currentTd.getB() + ")";

            // Phase 1: persist the match — B is an entity
            if (ruleMatches != null && currentTd != null) {
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getB()), null, null, result);
            }
        }
    }

}