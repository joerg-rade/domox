package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.PartOfSpeechType;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 2)
public class TDR2 extends TypedDependencyRule {

    @Override
    public boolean when() {
        if (currentTd == null) {
            return false;
        }

        boolean answer = false;
        if (isNsubj(currentTd) || isNsubjPass(currentTd)) {
            // Spec TDR2: A must be VB or VBN (not all verb forms)
            PartOfSpeechType pos = currentTd.getGovernorPos();
            if ((pos == PartOfSpeechType.VB || pos == PartOfSpeechType.VBN)
                    && isNounB(currentTd)
                    && isBasicAttributeB(currentTd)) {
                answer = true;
            }
        }
        return answer;
    }

    @Then
    public void then() {
        if (previousTd != null && isCompound(previousTd)) {
            result = "compound(" + currentTd.getB() + ") + Compound(" + currentTd.getA() + ")";
        } else {
            result = "nsubj(" + currentTd.getB() + ")";
        }

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            // when() guarantees B is a Basic Attribute, so only branches with bIsBasicAttrib == true are reachable
            if (!isBasicAttributeA(currentTd)) {
                String className = capitalizeFirstLetter(currentTd.getA());
                String propertyName = currentTd.getA() + " " + currentTd.getB();
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd", propertyName, "ClassCdd", className, result);
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd", currentTd.getA(), null, null, result);
            } else {
                String className = capitalizeFirstLetter(currentTd.getB());
                String propertyName = currentTd.getB() + " " + currentTd.getA();
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd", propertyName, "ClassCdd", className, result);
            }
        }
    }
}