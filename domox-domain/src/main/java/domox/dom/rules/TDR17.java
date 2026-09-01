package domox.dom.rules;


import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.TypedDependency;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 17)
public class TDR17 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: nsubj(Verb, E1) and dobj(VBN, E2) and nmod:of(E2, E3)
        // currentTd = nsubj(Verb, E1), nextTd = dobj(VBN, E2),
        // and an nmod:of(E2, E3) dependency governed by E2 must exist
        if (!isNsubj(currentTd)
                || !isVerbA(currentTd)
                || !isNounB(currentTd)) {
            return false;
        }
        if (nextTd == null
                || !dobj(nextTd)
                || nextTd.getGovernorPos() != PartOfSpeechType.VBN
                || !isNounB(nextTd)
                || nextTd.getGovernorIndex() != currentTd.getGovernorIndex()) {
            return false;
        }
        return findNmodOf(nextTd) != null;
    }

    @Override
    @Then
    public void then() {
        // relationship.add(E1 (VB) E2), relationship.add(E2 (has) E3)
        String verb = currentTd.getA();
        String e1 = currentTd.getB();
        String e2 = nextTd.getB();
        TypedDependency nmodOf = findNmodOf(nextTd);
        String e3 = nmodOf != null ? nmodOf.getB() : "E3";
        result = "relationship.add(" + e1 + " (" + verb + ") " + e2 + "), " +
                "relationship.add(" + e2 + " (has) " + e3 + ")";

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
            if (nmodOf != null) {
                ruleMatches.create(
                        nmodOf,
                        getRuleName(),
                        "ClassCdd",
                        capitalizeFirstLetter(e2),
                        "ClassCdd",
                        capitalizeFirstLetter(e3),
                        "relationship.add(" + e2 + " (has) " + e3 + ")");
            }
        }
    }

    /**
     * Finds the nmod:of(E2, E3) dependency whose governor is E2 (the dependent
     * of the dobj) within the same sentence.
     */
    private TypedDependency findNmodOf(TypedDependency dobj) {
        if (dobj == null || dobj.getSentence() == null) {
            return null;
        }
        for (TypedDependency td : dobj.getSentence().getTypedDependencies()) {
            if (nmodOf(td)
                    && td.getGovernorIndex() == dobj.getDependentIndex()
                    && isNounB(td)) {
                return td;
            }
        }
        return null;
    }

}