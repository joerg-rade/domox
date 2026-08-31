package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.TdType;

@RuleBean
@Rule(order = 7)
public class TDR7 extends TypedDependencyRule {

    @Override
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: Dependency = nmod:in(A,B) with A=Noun and B=Noun
        // Use precise TdType.OBL_IN check (nmod:in maps to obl:in in UD scheme)
        return currentTd.isNounA() && currentTd.isNounB()
                && currentTd.getType() == TdType.OBL_IN;
    }

    @Override
    public void then() {
        // Spec: Entity.add(B), Attributes.add(A)
        // B is the entity, A is an attribute of B
        result = "Entity.add(" + currentTd.getB() + "), Attributes.add(" + currentTd.getA() + ")";

        // Phase 1: persist the RuleMatch records
        if (ruleMatches != null && currentTd != null) {
            // B is the entity
            ruleMatches.create(currentTd, getRuleName(), "ClassCdd",
                    capitalizeFirstLetter(currentTd.getB()), null, null, result);
            // A is an attribute of B
            ruleMatches.create(currentTd, getRuleName(), "PropertyCdd",
                    currentTd.getA(), "ClassCdd",
                    capitalizeFirstLetter(currentTd.getB()), result);
        }
    }

}