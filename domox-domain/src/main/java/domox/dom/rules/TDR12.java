package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import static domox.dom.nlp.DependencyPosPredicates.*;
import static domox.dom.nlp.DependencyTypePredicates.*;
import static domox.dom.nlp.DependencyVocabularyPredicates.*;

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
        boolean nextIsNotSubjectOrObject = nextTd == null || (!isNsubj(nextTd) && !dobj(nextTd));
        return isCompound(currentTd)
                && nextIsNotSubjectOrObject
                && isNounA(currentTd)
                && isNounB(currentTd);
    }

    @Override
    public void then() {
        boolean aIsBasicAttrib = isBasicAttributeA(currentTd);
        boolean bIsBasicAttrib = isBasicAttributeB(currentTd);

        if (aIsBasicAttrib && !bIsBasicAttrib) {
            // if A=Basic_Attrib and B≠Basic_Attrib then
            // Attributes.add(B + A), Entity.add(B)
            //
            // Recorded as Attributes.add(A) — the attribute *word*, not the whole phrase.  The spec's
            // "B + A" produced names like "pet size" / "pet type" whose first word is the class the
            // attribute belongs to; the class is already carried as relatedCandidateName, and an
            // attribute name must be an attribute noun (a reference to another class is an
            // association, not an attribute).  See docs/scratch_pad/rules/RULES_REGISTRY.md.
            result = "Attributes.add(" + currentTd.getA() + "), Entity.add(" + currentTd.getB() + ")";

            if (ruleMatches != null && currentTd != null) {
                // B is the entity, A is the attribute of B
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getB()), null, null, result);
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd",
                        currentTd.getA(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getB()), result);
            }
        } else if (!aIsBasicAttrib && bIsBasicAttrib) {
            // else if A≠Basic_Attrib and B=Basic_Attrib then
            // Attributes.add(A + B), Entity.add(A)
            // Recorded as Attributes.add(B) — the attribute word (see the branch above).
            result = "Attributes.add(" + currentTd.getB() + "), Entity.add(" + currentTd.getA() + ")";

            if (ruleMatches != null && currentTd != null) {
                // A is the entity, B is the attribute of A
                ruleMatches.create(currentTd, getRuleName(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getA()), null, null, result);
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd",
                        currentTd.getB(), "ClassCdd",
                        capitalizeFirstLetter(currentTd.getA()), result);
            }
        } else if (aIsBasicAttrib && bIsBasicAttrib) {
            // else if A=Basic_Attrib and B=Basic_Attrib then
            // Attributes.add(B+A)
            //
            // Both sides are attribute words and the rule names no owning class, so the recorded match
            // carries no relatedCandidateName — CandidateFactory skips it rather than inventing a class
            // called "Unknown" to own it.  The description keeps the head (A, the compound's own head).
            result = "Attributes.add(" + currentTd.getA() + ")";

            if (ruleMatches != null && currentTd != null) {
                ruleMatches.create(currentTd, getRuleName(), "PropertyCdd",
                        currentTd.getA(), null, null, result);
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