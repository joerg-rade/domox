package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.TypedDependency;

import java.util.ArrayList;
import java.util.List;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 30)
public class TDR30 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null || currentTd.getA() == null) {
            return false;
        }
        // Spec: Dependencies = nmod:by(A,B) OR nmod:agent(A,B) OR nmod:with(A,B)
        //        if A=VB and A in {inputted, entered, filled, clicked, selected,
        //                           added, recorded, processed, validated}
        if (nmodBy(currentTd) || nmodAgent(currentTd) || nmodWith(currentTd)) {
            return isVerbA(currentTd) && isInputPastVerb(currentTd.getA());
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        // while (TD≠ nmod:by || nmod:agent || nmod:with)
        //   if (TD.B == attributes) Input_Data.add(B)
        List<String> inputData = new ArrayList<>();
        if (currentTd.getSentence() != null) {
            for (TypedDependency td : currentTd.getSentence().getTypedDependencies()) {
                if (nmodBy(td) || nmodAgent(td) || nmodWith(td)) {
                    continue;
                }
                if (isBasicAttributeB(td)) {
                    inputData.add(td.getB());
                }
            }
        }
        result = inputData.isEmpty()
                ? "Input_Data.add(" + currentTd.getB() + ")"
                : "Input_Data.add(" + String.join(", ", inputData) + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            for (String b : inputData) {
                ruleMatches.create(
                        currentTd,
                        getRuleName(),
                        "Input_Data",
                        capitalizeFirstLetter(b),
                        null,
                        null,
                        "Input_Data.add(" + b + ")");
            }
        }
    }

    private boolean isInputPastVerb(String verb) {
        return verb.equalsIgnoreCase("inputted") ||
                verb.equalsIgnoreCase("entered") ||
                verb.equalsIgnoreCase("filled") ||
                verb.equalsIgnoreCase("clicked") ||
                verb.equalsIgnoreCase("selected") ||
                verb.equalsIgnoreCase("added") ||
                verb.equalsIgnoreCase("recorded") ||
                verb.equalsIgnoreCase("processed") ||
                verb.equalsIgnoreCase("validated");
    }

}