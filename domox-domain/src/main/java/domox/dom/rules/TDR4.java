package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.PartOfSpeechType;

@RuleBean
@Rule(order = 4)
/*
 * Purpose: Identifies attributes from direct objects (dobj, iobj, pobj) where
 *          the verb is the governor and the object is a basic-attribute noun,
 *          or the verb is a "blocked" verb (entered/inputted/saved/added/has).
 *          Outputs Attributes (PropertyCdd) rather than Entities (ClassCdd).
 * Example: "The user enters the name." → name is identified as an attribute.
 * Candidate Type: PropertyCdd.
 */
public class TDR4 extends TypedDependencyRule {

    @Override
    public boolean when() {
        if (currentTd == null) {
            return false;
        }
        boolean answer = false;
        if (currentTd.dobj() || currentTd.iobj() || currentTd.pobj()) {
            // Spec TDR4: A must be VB (base form verb, not VBG/VBN/VBP/VBZ)
            PartOfSpeechType pos = currentTd.getGovernorPos();
            if (pos == PartOfSpeechType.VB && currentTd.isNounB()) {
                if (currentTd.isBasicAttributeB() || isBlockedVerb(currentTd.getA())) {
                    answer = true;
                }
            }
        }
        return answer;
    }

    @Then
    public void then() {
        if (previousTd != null && previousTd.compound()) {
            result = "compound(" + currentTd.getB() + ") + Compound(" + currentTd.getA() + ")";
        } else {
            result = "dobj(" + currentTd.getB() + ")";
        }

        // Phase 1: record the match — TDR4 outputs Attributes (PropertyCdd)
        if (ruleMatches != null && currentTd != null) {
            // B is a basic attribute (guaranteed by when() unless a blocked verb).
            // The owning class is derived from the non-basic-attrib side if possible.
            if (!currentTd.isBasicAttributeA()) {
                // A is a non-basic-attrib noun → use A as the owning class
                String className = capitalizeFirstLetter(currentTd.getA());
                String propertyName = currentTd.getA() + " " + currentTd.getB();
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd", propertyName, "ClassCdd", className, result);
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd", currentTd.getA(), null, null, result);
            } else {
                // Both A and B are basic attributes (or A is verb), use B as the class
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

    @Override
    protected String determineClassName() {
        // Override to provide a more meaningful class name.
        // For TDR4, the attribute's owning class is typically the subject noun,
        // which would be the dependent of an nsubj dependency in the same sentence.
        // As a fallback, use the governor (A) when it's a noun.
        if (!currentTd.isBasicAttributeA() && currentTd.isNounA()) {
            return capitalizeFirstLetter(currentTd.getA());
        }
        return capitalizeFirstLetter(currentTd.getB());
    }
}