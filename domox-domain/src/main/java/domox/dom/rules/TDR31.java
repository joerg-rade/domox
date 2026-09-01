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
@Rule(order = 31)
public class TDR31 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null || currentTd.getA() == null) {
            return false;
        }
        // Spec: Dependencies = nmod:by(A,B) OR nmod:agent(A,B) OR nmod:with(A,B)
        //        if A=VB and A in {displayed, outputted, retrieved, showed, viewed, printed}
        if (nmodBy(currentTd) || nmodAgent(currentTd) || nmodWith(currentTd)) {
            return isVerbA(currentTd) && isOutputPastVerb(currentTd.getA());
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        // Spec:
        //   while (TD≠ nmod:by || nmod:agent || nmod:with)
        //     if (TD.B == attributes) Output_Data.add(B)
        //
        // Process all dependencies in the sentence that are NOT one of the
        // excluded types and collect attribute B's into Output_Data.
        List<String> outputData = new ArrayList<>();
        if (currentTd.getSentence() != null) {
            for (TypedDependency td : currentTd.getSentence().getTypedDependencies()) {
                if (nmodBy(td) || nmodAgent(td) || nmodWith(td)) {
                    continue; // while loop condition: skip these types
                }
                if (isBasicAttributeB(td)) {
                    outputData.add(td.getB());
                }
            }
        }
        result = outputData.isEmpty()
                ? "Output_Data.add(" + currentTd.getB() + ")"
                : "Output_Data.add(" + String.join(", ", outputData) + ")";

        // Phase 1: record the matches; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            for (String b : outputData) {
                ruleMatches.create(
                        currentTd,
                        getRuleName(),
                        "Output_Data",
                        capitalizeFirstLetter(b),
                        null,
                        null,
                        "Output_Data.add(" + b + ")");
            }
        }
    }

    private boolean isOutputPastVerb(String verb) {
        return verb.equalsIgnoreCase("displayed") ||
                verb.equalsIgnoreCase("outputted") ||
                verb.equalsIgnoreCase("retrieved") ||
                verb.equalsIgnoreCase("showed") ||
                verb.equalsIgnoreCase("viewed") ||
                verb.equalsIgnoreCase("printed");
    }

}