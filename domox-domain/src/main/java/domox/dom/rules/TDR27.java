package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.TypedDependency;

import java.util.ArrayList;
import java.util.List;

@RuleBean
@Rule(order = 27)
public class TDR27 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null || currentTd.getA() == null) {
            return false;
        }
        // Spec: Dependencies = nsubj(A,B) OR nsubjpass(A,B) OR dobj(A,B) OR
        //        iobj(A,B) OR pobj(A,B) OR nmod:to(A,B) OR mark(A,B)
        //        if A=VB and A in {input, enter, fill, click, select, add, record, process, validate}
        if (currentTd.nsubj() || currentTd.nsubjpass() || currentTd.dobj() ||
                currentTd.iobj() || currentTd.pobj() || currentTd.nmodTo() || currentTd.mark()) {
            return currentTd.isVerbA() && isInputVerb(currentTd.getA());
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        // while (TD≠nsubj || nsubjpass || dobj || iobj || pobj || mark)
        //   if (TD.B == attributes) Input_Data.add(B)
        List<String> inputData = new ArrayList<>();
        if (currentTd.getSentence() != null) {
            for (TypedDependency td : currentTd.getSentence().getTypedDependencies()) {
                if (td.nsubj() || td.nsubjpass() || td.dobj() || td.iobj() ||
                        td.pobj() || td.mark()) {
                    continue;
                }
                if (td.isBasicAttributeB()) {
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

    private boolean isInputVerb(String verb) {
        return verb.equalsIgnoreCase("input") ||
                verb.equalsIgnoreCase("enter") ||
                verb.equalsIgnoreCase("fill") ||
                verb.equalsIgnoreCase("click") ||
                verb.equalsIgnoreCase("select") ||
                verb.equalsIgnoreCase("add") ||
                verb.equalsIgnoreCase("record") ||
                verb.equalsIgnoreCase("process") ||
                verb.equalsIgnoreCase("validate");
    }

}