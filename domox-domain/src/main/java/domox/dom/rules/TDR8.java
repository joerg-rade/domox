package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 8)
/*
 * Purpose: Identifies classes from prepositional nmod dependencies (to/for/from/as).
 * Example: "The report for the project is ready." → Project is identified as a class.
 * Candidate Type: ClassCdd.
 */
public class TDR8 extends TypedDependencyRule {

    @Override
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependency= nmod:to(A,B) OR nmod:for(A,B) OR nmod:from(A,B) OR nmod:as(A,B)
        // if B=Noun then
        return (nmodTo(currentTd) || nmodFor(currentTd) || nmodFrom(currentTd) || nmodAs(currentTd))
                && isNounB(currentTd);
    }

    @Then
    public void then() {
        // Entity.add(B)
        String b = currentTd.getB();
        result = "Entity.add(" + b + ")";

        // Phase 1: persist the RuleMatch record
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(currentTd, getRuleName(), "ClassCdd",
                    capitalizeFirstLetter(b), null, null, result);
        }
    }

}