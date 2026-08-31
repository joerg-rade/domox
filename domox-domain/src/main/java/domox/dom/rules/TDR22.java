package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.TypedDependency;

@RuleBean
@Rule(order = 22)
public class TDR22 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: nsubj(VB, E1) and nmod:for(VB, E2)
        // currentTd = nsubj(VB, E1), and an nmod:for(VB, E2) dependency
        // governed by the same VB must exist
        if (!currentTd.nsubj()
                || !currentTd.isVerbA()
                || !currentTd.isNounB()) {
            return false;
        }
        return findNmodFor(currentTd) != null;
    }

    @Override
    @Then
    public void then() {
        // relationship.add(E1 (VB + "for") E2)
        String verb = currentTd.getA();
        String e1 = currentTd.getB();
        TypedDependency nmodFor = findNmodFor(currentTd);
        String e2 = nmodFor != null ? nmodFor.getB() : "E2";
        result = "relationship.add(" + e1 + " (" + verb + " for) " + e2 + ")";

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
     * Finds the nmod:for(VB, E2) dependency governed by the same verb as the
     * nsubj within the same sentence.
     */
    private TypedDependency findNmodFor(TypedDependency nsubj) {
        if (nsubj == null || nsubj.getSentence() == null) {
            return null;
        }
        for (TypedDependency td : nsubj.getSentence().getTypedDependencies()) {
            if (td.nmodFor()
                    && td.getGovernorIndex() == nsubj.getGovernorIndex()
                    && td.isNounB()) {
                return td;
            }
        }
        return null;
    }

}