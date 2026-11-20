package domox.dom.rules.tdr;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.rules.NlpProperties;
import domox.dom.rules.lexicon.MultiplicityNotation;
import domox.dom.rules.lexicon.UseCaseBlockedNouns;

import java.util.Locale;

import static domox.dom.nlp.DependencyTypePredicates.det;
import static domox.dom.nlp.DependencyPosPredicates.isNounA;

@RuleBean
@Rule(order = 26)
public class TDR26 extends TypedDependencyRule {

    public TDR26(NlpProperties nlpProperties) {
        super(nlpProperties);
    }

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: det(E1, DT) -> multiplicity.add based on the DT value
        // E1 (governor) must be a noun entity and DT (dependent) one of
        // the determiners handled by this rule
        if (!det(currentTd) || !isNounA(currentTd) || currentTd.getB() == null) {
            return false;
        }
        // Scope guard, as in TDR25: "each sentence", "a day" is not the multiplicity of an
        // association end.  The determiner list alone is not enough — the governor has to be a
        // domain noun too.
        if (UseCaseBlockedNouns.isMetadataNoun(nlpProperties, currentTd.getA())) {
            return false;
        }
        String dt = currentTd.getB().toLowerCase(Locale.ROOT);
        return MultiplicityNotation.isQuantifyingDeterminer(dt)
                || MultiplicityNotation.isSingularDeterminer(dt);
    }

    @Override
    @Then
    public void then() {
        // if (DT="Each" OR "All" OR "some" OR "Any" OR "Many" OR "Every" OR "multiple")
        //    multiplicity.add(E1, N)
        // if (DT= "a" OR "an")
        //    multiplicity.add(E1, 1)
        // "N" was a literal placeholder, never a value.  The determiner is translated to UML
        // notation instead — "a"/"an" -> "1", "some" -> "1..*", "each"/"every"/"all"/"any"/
        // "many"/"multiple" -> "*" — see MultiplicityNotation.
        final String e1 = currentTd.getA();
        final String dt = currentTd.getB().toLowerCase(Locale.ROOT);
        result = "multiplicity.add(" + e1 + ", " + MultiplicityNotation.umlNotation(dt) + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        // The DT (determiner) indicates a multiplicity value, not a separate class
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