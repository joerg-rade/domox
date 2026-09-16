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

    public TDR29(NlpProperties nlpProperties) {
        super(nlpProperties);
    }

    @Override
    @When
    public boolean when() {
        if (currentTd == null || currentTd.getA() == null) {
            return false;
        }
        if (isNsubj(currentTd) || isNsubjPass(currentTd) || dobj(currentTd) ||
                iobj(currentTd) || pobj(currentTd) || nmodTo(currentTd) || mark(currentTd)) {
            return isVerbA(currentTd) && nlpProperties.getActionVerbs().contains(currentTd.getA().toLowerCase());
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        List<String> outputData = new ArrayList<>();
        List<String> inputData = new ArrayList<>();
        if (currentTd.getSentence() != null) {
            for (TypedDependency td : currentTd.getSentence().getTypedDependencies()) {
                if (isNsubj(td) || isNsubjPass(td) || dobj(td) || iobj(td) ||
                        pobj(td) || mark(td)) {
                    continue;
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
}