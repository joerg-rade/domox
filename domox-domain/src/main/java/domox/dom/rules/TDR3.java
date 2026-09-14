package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 3)
/*
 * Purpose: Identifies actions from direct objects (dobj, iobj, pobj) where
 *          the verb is the governor, the object is a non-basic-attribute noun,
 *          the previous dependency is neither amod nor advmod, and the verb is
 *          not a "blocked" verb (entered/inputted/saved/added/has).
 * Example: "A pet shop can offer a wide range of pet products."
 *          → ActionCdd("offer") with relatedCandidate ClassCdd("Products").
 *          When the previous dependency is a compound, e.g.
 *          "compound(products, pet)", the noun part ("products") becomes the
 *          relatedCandidateName.
 * Candidate Type: ActionCdd (the verb is an action, not a class).
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
        if (dobj(currentTd) || iobj(currentTd) || pobj(currentTd)) {
            if (isVerbA(currentTd) && isNounB(currentTd) && !isBasicAttributeB(currentTd)) {
                if (previousTd == null || (!amod(previousTd) && !advmod(previousTd))) {
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
        String verb = currentTd.getA().toLowerCase();
        String objectNoun = currentTd.getB();

        if (previousTd != null && isCompound(previousTd)) {
            // Entity.add(compound(B) + Compound(A))
            result = "compound(" + objectNoun + ") + compound(" + verb + ")";
        } else {
            // Entity.add(dobj(B))
            result = "dobj(" + objectNoun + ")";
        }

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        // candidateType = "ActionCdd" — the verb represents an action, not a class
        // candidateName  = verb lowercase — the action identifier
        // relatedCandidateType = "ClassCdd" — B (the direct object) is a class candidate
        // relatedCandidateName = capitalizeFirstLetter(objectNoun) — the noun as a class name
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "ActionCdd",
                    verb,
                    "ClassCdd",
                    capitalizeFirstLetter(objectNoun),
                    result);
        }
    }

}