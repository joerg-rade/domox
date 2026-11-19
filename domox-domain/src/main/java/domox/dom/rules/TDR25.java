package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import static domox.dom.nlp.DependencyPosPredicates.isNounA;
import static domox.dom.nlp.DependencyTypePredicates.nummod;

@RuleBean
@Rule(order = 25)
public class TDR25 extends TypedDependencyRule {

    public TDR25(NlpProperties nlpProperties) {
        super(nlpProperties);
    }

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: nummod(E1, CD) -> multiplicity.add(E1, CD)
        // E1 (governor) must be a noun entity
        if (!nummod(currentTd) || !isNounA(currentTd)) {
            return false;
        }
        // Scope guard: a nummod on a document-metadata noun is a cross-reference, not a
        // multiplicity — "elaborated from sentence 10 of ...", "Use Case ID UC - 09", "within 2
        // seconds".  Measured: 33 of the 44 TDR25 matches in the PetShop corpus were of this kind,
        // and each one also fabricated a ClassCdd named after the metadata noun.  The spec is
        // unchanged (nummod(E1, CD) -> multiplicity.add(E1, CD)); this only narrows which E1 count.
        return !UseCaseBlockedNouns.isMetadataNoun(nlpProperties, currentTd.getA());
    }

    @Override
    @Then
    public void then() {
        // multiplicity.add(E1, CD)
        // The CD token is the parser's, not a UML multiplicity: it can be zero-padded ("01"),
        // a number word ("one") or a comparative ("more"). Normalise it — see MultiplicityNotation.
        final String e1 = currentTd.getA();
        final String multiplicity = MultiplicityNotation.umlNotation(currentTd.getB());
        result = "multiplicity.add(" + e1 + ", " + multiplicity + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        // The CD (number) is a multiplicity value, not a separate class
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "ClassCdd",
                    capitalizeFirstLetter(e1),
                    null,
                    null,
                    result);
        }
    }

}