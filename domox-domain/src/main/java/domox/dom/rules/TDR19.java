package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.TypedDependency;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 19)
public class TDR19 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: nsubjpass(VBN, E1) and nmod:to(VBN, E2)
        // currentTd = nsubjpass(VBN, E1), and an nmod:to(VBN, E2)
        // dependency governed by the same VBN must exist
        if (!isNsubjPass(currentTd)
                || currentTd.getGovernorPos() != PartOfSpeechType.VBN
                || !isNounB(currentTd)) {
            return false;
        }
        return findNmodTo(currentTd) != null;
    }

    @Override
    @Then
    public void then() {
        // relationship.add(E1 (VBN + "to") E2)
        String vbn = currentTd.getA();
        String e1 = currentTd.getB();
        TypedDependency nmodTo = findNmodTo(currentTd);
        String e2 = nmodTo != null ? nmodTo.getB() : "E2";
        result = "relationship.add(" + e1 + " (" + vbn + " to) " + e2 + ")";

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
     * Finds the nmod:to(VBN, E2) dependency governed by the same VBN as the
     * nsubjpass within the same sentence.
     */
    private TypedDependency findNmodTo(TypedDependency nsubjpass) {
        if (nsubjpass == null || nsubjpass.getSentence() == null) {
            return null;
        }
        for (TypedDependency td : nsubjpass.getSentence().getTypedDependencies()) {
            if (nmodTo(td)
                    && td.getGovernorIndex() == nsubjpass.getGovernorIndex()
                    && isNounB(td)) {
                return td;
            }
        }
        return null;
    }

}