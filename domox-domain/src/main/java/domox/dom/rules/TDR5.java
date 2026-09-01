package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.PartOfSpeechType;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 5)
/*
 * Purpose: Identifies attributes from direct objects (dobj, iobj, pobj) where
 *          the verb is the governor and the object is a basic-attribute noun
 *          (or the verb is one of the "blocked" verbs), refining the result
 *          when the previous dependency is an amod/advmod with a JJ dependent.
 * Example: "The user enters the account name." → name is an attribute,
 *          refined by the amod "account".
 * Candidate Type: PropertyCdd.
 */
public class TDR5 extends TypedDependencyRule {

    @Override
    public boolean when() {
        if (currentTd == null) {
            return false;
        }
        boolean answer = false;
        if (dobj(currentTd) || iobj(currentTd) || pobj(currentTd)){
            // Spec TDR5: A must be VB (base form verb, not VBG/VBN/VBP/VBZ)
            PartOfSpeechType pos = currentTd.getGovernorPos();
            if (pos == PartOfSpeechType.VB && isNounB(currentTd)){
                if (isBasicAttributeB(currentTd) || isBlockedVerb(currentTd.getA())) {
                    answer = true;
                }
            }
        }
        return answer;
    }

    @Then
    public void then() {
        // Spec: If (prevTD = "amod" || prevTD = "advmod") and prev(B)="JJ" then
        //       Attributes.add(amod(B) + amod(A))
        //       else Attributes.add(dobj(B))
        if (previousTd != null
                && (amod(previousTd) || advmod(previousTd))
                && isAdjectiveB(previousTd)) {
            result = "amod(" + currentTd.getB() + ") + amod(" + currentTd.getA() + ")";
        } else {
            result = "dobj(" + currentTd.getB() + ")";
        }

        // Phase 1: record the match — TDR5 outputs Attributes (PropertyCdd)
        if (ruleMatches != null && currentTd != null) {
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

    private boolean isBlockedVerb(String verb) {
        return verb.equalsIgnoreCase("entered") ||
                verb.equalsIgnoreCase("inputted") ||
                verb.equalsIgnoreCase("saved") ||
                verb.equalsIgnoreCase("added") ||
                verb.equalsIgnoreCase("has");
    }
}