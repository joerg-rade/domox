package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.TypedDependency;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 18)
public class TDR18 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: nsubj(VB, E1) and dobj(VB, E2) and nmod:to(VB, E3)
        // currentTd = nsubj(VB, E1), nextTd = dobj(VB, E2),
        // and an nmod:to(VB, E3) dependency governed by the same VB must exist
        if (!isNsubj(currentTd)
                || !isVerbA(currentTd)
                || !isNounB(currentTd)) {
            return false;
        }
        if (nextTd == null
                || !dobj(nextTd)
                || !isNounB(nextTd)
                || nextTd.getGovernorIndex() != currentTd.getGovernorIndex()) {
            return false;
        }
        return findNmodTo(currentTd) != null;
    }

    @Override
    @Then
    public void then() {
        // relationship.add(E1 (VB) E2), relationship.add(E2 (VB+ "to") E3), relationship.add(E1 (VB+ "to") E3)
        String verb = currentTd.getA();
        String e1 = currentTd.getB();
        String e2 = nextTd.getB();
        TypedDependency nmodTo = findNmodTo(currentTd);
        String e3 = nmodTo != null ? nmodTo.getB() : "E3";
        result = "relationship.add(" + e1 + " (" + verb + ") " + e2 + "), " +
                "relationship.add(" + e2 + " (" + verb + " to) " + e3 + "), " +
                "relationship.add(" + e1 + " (" + verb + " to) " + e3 + ")";

        // Phase 1: record the matches; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "ClassCdd",
                    capitalizeFirstLetter(e1),
                    "ClassCdd",
                    capitalizeFirstLetter(e2),
                    "relationship.add(" + e1 + " (" + verb + ") " + e2 + ")");
            if (nmodTo != null) {
                ruleMatches.create(
                        nmodTo,
                        getRuleName(),
                        "ClassCdd",
                        capitalizeFirstLetter(e2),
                        "ClassCdd",
                        capitalizeFirstLetter(e3),
                        "relationship.add(" + e2 + " (" + verb + " to) " + e3 + ")");
                ruleMatches.create(
                        nmodTo,
                        getRuleName(),
                        "ClassCdd",
                        capitalizeFirstLetter(e1),
                        "ClassCdd",
                        capitalizeFirstLetter(e3),
                        "relationship.add(" + e1 + " (" + verb + " to) " + e3 + ")");
            }
        }
    }

    /**
     * Finds the nmod:to(VB, E3) dependency governed by the same verb as the
     * nsubj within the same sentence.
     */
    private TypedDependency findNmodTo(TypedDependency nsubj) {
        if (nsubj == null || nsubj.getSentence() == null) {
            return null;
        }
        for (TypedDependency td : nsubj.getSentence().getTypedDependencies()) {
            if (nmodTo(td)
                    && td.getGovernorIndex() == nsubj.getGovernorIndex()
                    && isNounB(td)) {
                return td;
            }
        }
        return null;
    }

}