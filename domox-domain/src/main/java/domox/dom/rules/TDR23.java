package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.TypedDependency;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 23)
public class TDR23 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: nmod:as(VB, E1) and dobj(VB, E2)
        // currentTd = nmod:as(VB, E1), and a dobj(VB, E2) dependency
        // governed by the same VB must exist
        if (!nmodAs(currentTd)
                || !isVerbA(currentTd)
                || !isNounB(currentTd)) {
            return false;
        }
        return findDobj(currentTd) != null;
    }

    @Override
    @Then
    public void then() {
        // relationship.add(E1 (VB) E2)
        String verb = currentTd.getA();
        String e1 = currentTd.getB();
        TypedDependency dobj = findDobj(currentTd);
        String e2 = dobj != null ? dobj.getB() : "E2";
        result = "relationship.add(" + e1 + " (" + verb + ") " + e2 + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "ClassCdd",
                    capitalizeFirstLetter(e1),
                    "ClassCdd",
                    capitalizeFirstLetter(e2),
                    result);
        }
    }

    /**
     * Finds the dobj(VB, E2) dependency governed by the same verb as the
     * nmod:as within the same sentence.
     */
    private TypedDependency findDobj(TypedDependency nmodAs) {
        if (nmodAs == null || nmodAs.getSentence() == null) {
            return null;
        }
        for (TypedDependency td : nmodAs.getSentence().getTypedDependencies()) {
            if (dobj(td)
                    && td.getGovernorIndex() == nmodAs.getGovernorIndex()
                    && isNounB(td)) {
                return td;
            }
        }
        return null;
    }

}