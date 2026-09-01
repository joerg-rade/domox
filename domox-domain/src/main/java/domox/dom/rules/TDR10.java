package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.PartOfSpeechType;

import static domox.dom.nlp.TypedDependencyPredicates.*;

/**
 * Purpose: Identifies possessive relationships (nmod:poss).
 * Example: "the customer's name" → name is an attribute of customer.
 * Candidate Type: ClassCdd (possessed entity) or PropertyCdd (possessor/preposition attribute).
 */
@RuleBean
@Rule(order = 10)
public class TDR10 extends TypedDependencyRule {

    @Override
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependencies= nmod:poss(A,B) and A=Noun
        // (B=Noun or B=PREP is resolved inside then())
        return nmodPoss(currentTd) && isNounA(currentTd);
    }

    @Override
    public void then() {
        if (isNounB(currentTd)) {
            // if A=Noun and B = Noun then
            // Entity.add(B), Attributes.add(A)
            result = "Entity.add(" + currentTd.getB() + "), Attributes.add(" + currentTd.getA() + ")";

            // Phase 1: persist the match — B is the entity, A is an attribute of B
            if (ruleMatches != null && currentTd != null) {
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getB()), null, null, result);
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd",
                        currentTd.getA(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getB()), result);
            }
        } else if (currentTd.getDependentPos() == PartOfSpeechType.IN
                && !isBasicAttributeB(currentTd)) {
            // else if A=Noun and B= PREP ≠ Basic_Attrib then
            // Attributes.add(B)
            result = "Attributes.add(" + currentTd.getB() + ")";

            // Phase 1: persist the match — B is an attribute
            if (ruleMatches != null && currentTd != null) {
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd",
                        currentTd.getB(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getA()), result);
            }
        }
    }
}