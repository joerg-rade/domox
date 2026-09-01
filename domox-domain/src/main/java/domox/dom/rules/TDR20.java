package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.TypedDependency;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 20)
public class TDR20 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: nsubj(Verb, E1) and nsubjpass(VBN, E2) and nmod:to(VBN, E3)
        // currentTd = nsubj(Verb, E1); a nsubjpass(VBN, E2) must exist, and the
        // nmod:to(VBN, E3) must be governed by the same VBN as that nsubjpass
        if (!isNsubj(currentTd)
                || !isVerbA(currentTd)
                || !isNounB(currentTd)) {
            return false;
        }
        TypedDependency nsubjpass = findNsubjpass(currentTd);
        return nsubjpass != null && findNmodTo(nsubjpass) != null;
    }

    @Override
    @Then
    public void then() {
        // relationship.add(E1 (VB) E2), relationship.add(E1 (VBN + "to") E3), relationship.add(E2 (VBN + "to") E3)
        String verb = currentTd.getA();
        String e1 = currentTd.getB();
        TypedDependency nsubjpass = findNsubjpass(currentTd);
        String vbn = nsubjpass != null ? nsubjpass.getA() : "VBN";
        String e2 = nsubjpass != null ? nsubjpass.getB() : "E2";
        TypedDependency nmodTo = nsubjpass != null ? findNmodTo(nsubjpass) : null;
        String e3 = nmodTo != null ? nmodTo.getB() : "E3";
        result = "relationship.add(" + e1 + " (" + verb + ") " + e2 + "), " +
                "relationship.add(" + e1 + " (" + vbn + " to) " + e3 + "), " +
                "relationship.add(" + e2 + " (" + vbn + " to) " + e3 + ")";

        // Phase 1: record the matches; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null && nsubjpass != null) {
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
                        capitalizeFirstLetter(e1),
                        "ClassCdd",
                        capitalizeFirstLetter(e3),
                        "relationship.add(" + e1 + " (" + vbn + " to) " + e3 + ")");
                ruleMatches.create(
                        nmodTo,
                        getRuleName(),
                        "ClassCdd",
                        capitalizeFirstLetter(e2),
                        "ClassCdd",
                        capitalizeFirstLetter(e3),
                        "relationship.add(" + e2 + " (" + vbn + " to) " + e3 + ")");
            }
        }
    }

    /**
     * Finds a nsubjpass(VBN, E2) dependency in the same sentence as the nsubj.
     */
    private TypedDependency findNsubjpass(TypedDependency nsubj) {
        if (nsubj == null || nsubj.getSentence() == null) {
            return null;
        }
        for (TypedDependency td : nsubj.getSentence().getTypedDependencies()) {
            if (isNsubjPass(td)
                    && td.getGovernorPos() == PartOfSpeechType.VBN
                    && isNounB(td)) {
                return td;
            }
        }
        return null;
    }

    /**
     * Finds the nmod:to(VBN, E3) dependency governed by the same VBN as the
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