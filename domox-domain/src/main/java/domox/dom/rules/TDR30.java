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

    public TDR30(NlpProperties nlpProperties) {
        super(nlpProperties);
    }

    @Override
    @When
    public boolean when() {
        if (!nlpProperties.getDisabledRules().contains("TDR30")) {
            return false;
        }
        if (currentTd == null || currentTd.getA() == null) {
            return false;
        }
        if (nmodBy(currentTd) || nmodAgent(currentTd) || nmodWith(currentTd)) {
            return isVerbA(currentTd) && nlpProperties.getInputPastVerbs().contains(currentTd.getA().toLowerCase());
        }
        return false;
    }

    @Override
    @Then
    public void then() {
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
}