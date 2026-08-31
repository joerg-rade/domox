package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 2)
public class TDR2 extends TypedDependencyRule {

    @Override
    public boolean when() {
        if (currentTd == null) {
            return false;
        }

        boolean answer = false;
        if (currentTd.nsubj() || currentTd.nsubjpass()) {
            if (currentTd.isVerbA() && currentTd.isNounB() && currentTd.isBasicAttributeB()) {
                answer = true;
            }
        }
        return answer;
    }

    @Then
    public void then() {
        if (previousTd != null && previousTd.compound()) {
            result = "compound(" + currentTd.getB() + ") + Compound(" + currentTd.getA() + ")";
        } else {
            result = "nsubj(" + currentTd.getB() + ")";
        }

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            boolean aIsBasicAttrib = currentTd.isBasicAttributeA();
            boolean bIsBasicAttrib = currentTd.isBasicAttributeB();

            if (aIsBasicAttrib && !bIsBasicAttrib) {
                String className = capitalizeFirstLetter(currentTd.getB());
                String propertyName = currentTd.getB() + " " + currentTd.getA();
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd", propertyName, "ClassCdd", className, result);
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd", currentTd.getB(), null, null, result);
            } else if (!aIsBasicAttrib && bIsBasicAttrib) {
                String className = capitalizeFirstLetter(currentTd.getA());
                String propertyName = currentTd.getA() + " " + currentTd.getB();
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd", propertyName, "ClassCdd", className, result);
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd", currentTd.getA(), null, null, result);
            } else if (aIsBasicAttrib && bIsBasicAttrib) {
                String className = capitalizeFirstLetter(currentTd.getB());
                String propertyName = currentTd.getB() + " " + currentTd.getA();
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd", propertyName, "ClassCdd", className, result);
            } else if (!aIsBasicAttrib && !bIsBasicAttrib) {
                String entityName = currentTd.getB() + capitalizeFirstLetter(currentTd.getA());
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd", entityName, null, null, result);
            }
        }
    }
}