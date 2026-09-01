package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.TypedDependency;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 21)
public class TDR21 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: nsubj(VB, E1) and nmod:in(VB, E2)
        // currentTd = nsubj(VB, E1), and an nmod:in(VB, E2) dependency
        // governed by the same VB must exist
        if (!isNsubj(currentTd)
                || !isVerbA(currentTd)
                || !isNounB(currentTd)) {
            return false;
        }
        return findNmodIn(currentTd) != null;
    }

    @Override
    @Then
    public void then() {
        // relationship.add(E1 (VB + "in") E2)
        String verb = currentTd.getA();
        String e1 = currentTd.getB();
        TypedDependency nmodIn = findNmodIn(currentTd);
        String e2 = nmodIn != null ? nmodIn.getB() : "E2";
        result = "relationship.add(" + e1 + " (" + verb + " in) " + e2 + ")";

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
     * Finds the nmod:in(VB, E2) dependency governed by the same verb as the
     * nsubj within the same sentence.
     */
    private TypedDependency findNmodIn(TypedDependency nsubj) {
        if (nsubj == null || nsubj.getSentence() == null) {
            return null;
        }
        for (TypedDependency td : nsubj.getSentence().getTypedDependencies()) {
            if (nmodIn(td)
                    && td.getGovernorIndex() == nsubj.getGovernorIndex()
                    && isNounB(td)) {
                return td;
            }
        }
        return null;
    }

}