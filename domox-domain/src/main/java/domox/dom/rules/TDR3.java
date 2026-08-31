package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RuleBean
@Rule(order = 3)
/*
 * Purpose: Identifies classes from direct objects (dobj, iobj, pobj) where
 *          the verb is the governor, the object is a non-basic-attribute noun,
 *          the previous dependency is neither amod nor advmod, and the verb is
 *          not a "blocked" verb (entered/inputted/saved/added/has).
 * Example: "The system processes a payment." → Payment is identified as a class.
 * Candidate Type: ClassCdd.
 */
public class TDR3 extends TypedDependencyRule {
    private static final Logger log = LoggerFactory.getLogger(TDR3.class);

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        boolean answer = false;
        if (currentTd.dobj() || currentTd.iobj() || currentTd.pobj()) {
            if (currentTd.isVerbA() && currentTd.isNounB() && !currentTd.isBasicAttributeB()) {
                if (previousTd == null || (!previousTd.amod() && !previousTd.advmod())) {
                    String verbA = currentTd.getA();
                    if (!isBlockedVerb(verbA)) {
                        answer = true;
                    }
                }
            }
        }
        return answer;
    }

    @Then
    public void then() {
        log.debug("TDR3 fired: {}", currentTd);
        if (previousTd != null && previousTd.compound()) {
            // Entity.add(compound(B) + Compound(A))
            result = "compound(" + currentTd.getB() + ") + Compound(" + currentTd.getA() + ")";
        } else {
            // Entity.add(dobj(B))
            result = "dobj(" + currentTd.getB() + ")";
        }

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "ClassCdd",
                    determineClassName(),
                    null,
                    null,
                    result);
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