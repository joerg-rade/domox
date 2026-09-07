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

    private final NlpProperties nlpProperties;

    public TDR31(NlpProperties nlpProperties) {
        this.nlpProperties = nlpProperties;
    }

    @Override
    @When
    public boolean when() {
        if (currentTd == null || currentTd.getA() == null) {
            return false;
        }
        if (nmodBy(currentTd) || nmodAgent(currentTd) || nmodWith(currentTd)) {
            return isVerbA(currentTd) && nlpProperties.getOutputPastVerbs().contains(currentTd.getA().toLowerCase());
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        List<String> outputData = new ArrayList<>();
        if (currentTd.getSentence() != null) {
            for (TypedDependency td : currentTd.getSentence().getTypedDependencies()) {
                if (nmodBy(td) || nmodAgent(td) || nmodWith(td)) {
                    continue;
                }
                if (isBasicAttributeB(td)) {
                    outputData.add(td.getB());
                }
            }
        }
        result = outputData.isEmpty()
                ? "Output_Data.add(" + currentTd.getB() + ")"
                : "Output_Data.add(" + String.join(", ", outputData) + ")";

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
}