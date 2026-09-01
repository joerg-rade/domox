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
@Rule(order = 29)
public class TDR29 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null || currentTd.getA() == null) {
            return false;
        }
        // Spec: Dependencies = nsubj(A,B) OR nsubjpass(A,B) OR dobj(A,B) OR
        //        iobj(A,B) OR pobj(A,B) OR nmod:to(A,B) OR mark(A,B)
        //        if A=VB AND A in {get, send, prepare}
        if (isNsubj(currentTd) || isNsubjPass(currentTd) || dobj(currentTd) ||
                iobj(currentTd) || pobj(currentTd) || nmodTo(currentTd) || mark(currentTd)) {
            return isVerbA(currentTd) && isActionVerb(currentTd.getA());
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        // Spec:
        //   while (TD≠nsubj || nsubjpass || dobj || iobj || pobj || mark)
        //     if (B == 'system')      -> Output_Data.add(B)
        //     else if (B != 'system') -> Input_Data.add(B)
        //
        // Process all dependencies in the sentence that are NOT one of the
        // excluded types; check B == 'system' FIRST (not gated by attributes),
        // and collect matching B's into their respective lists.
        List<String> outputData = new ArrayList<>();
        List<String> inputData = new ArrayList<>();
        if (currentTd.getSentence() != null) {
            for (TypedDependency td : currentTd.getSentence().getTypedDependencies()) {
                if (isNsubj(td) || isNsubjPass(td) || dobj(td) || iobj(td) ||
                        pobj(td) || mark(td)) {
                    continue; // while loop condition: skip these types
                }
                if (td.getB() != null && td.getB().equalsIgnoreCase("system")) {
                    outputData.add(td.getB());
                } else if (isBasicAttributeB(td)) {
                    inputData.add(td.getB());
                }
            }
        }
        if (!outputData.isEmpty()) {
            result = "Output_Data.add(" + String.join(", ", outputData) + ")";
        } else if (!inputData.isEmpty()) {
            result = "Input_Data.add(" + String.join(", ", inputData) + ")";
        } else {
            result = "Input_Data/Output_Data.add(" + currentTd.getB() + ")";
        }

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

    private boolean isActionVerb(String verb) {
        return verb.equalsIgnoreCase("get") ||
                verb.equalsIgnoreCase("send") ||
                verb.equalsIgnoreCase("prepare");
    }

}