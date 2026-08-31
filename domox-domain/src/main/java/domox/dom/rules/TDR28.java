package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.TypedDependency;

import java.util.ArrayList;
import java.util.List;

@RuleBean
@Rule(order = 28)
public class TDR28 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null || currentTd.getA() == null) {
            return false;
        }
        // Spec: Dependencies = nsubj(A,B) OR nsubjpass(A,B) OR dobj(A,B) OR
        //        iobj(A,B) OR pobj(A,B) OR nmod:to(A,B) OR mark(A,B)
        //        if A=VB and A in {display, output, retrieve, show, view, print}
        if (currentTd.nsubj() || currentTd.nsubjpass() || currentTd.dobj() ||
                currentTd.iobj() || currentTd.pobj() || currentTd.nmodTo() || currentTd.mark()) {
            return currentTd.isVerbA() && isOutputVerb(currentTd.getA());
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        // while (TD≠nsubj || nsubjpass || dobj || iobj || pobj || mark)
        //   if (TD.B == attributes) Output_Data.add(B)
        List<String> outputData = new ArrayList<>();
        if (currentTd.getSentence() != null) {
            for (TypedDependency td : currentTd.getSentence().getTypedDependencies()) {
                if (td.nsubj() || td.nsubjpass() || td.dobj() || td.iobj() ||
                        td.pobj() || td.mark()) {
                    continue;
                }
                if (td.isBasicAttributeB()) {
                    outputData.add(td.getB());
                }
            }
        }
        result = outputData.isEmpty()
                ? "Output_Data.add(" + currentTd.getB() + ")"
                : "Output_Data.add(" + String.join(", ", outputData) + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
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

    private boolean isOutputVerb(String verb) {
        return verb.equalsIgnoreCase("display") ||
                verb.equalsIgnoreCase("output") ||
                verb.equalsIgnoreCase("retrieve") ||
                verb.equalsIgnoreCase("show") ||
                verb.equalsIgnoreCase("view") ||
                verb.equalsIgnoreCase("print");
    }

}