package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.TypedDependency;

import java.util.ArrayList;
import java.util.List;

import static domox.dom.nlp.TypedDependencyPredicates.isBasicAttributeB;
import static domox.dom.nlp.TypedDependencyPredicates.isNsubj;

@RuleBean
@Rule(order = 36)
public class TDR36 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: Dependencies = nsubj(A,B) and A = "validate"
        if (isNsubj(currentTd)) {
            String a = currentTd.getA();
            return a != null && a.equalsIgnoreCase("validate");
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        // Spec:
        //   System_Actions.add(B + A)
        //   while (TD≠NULL)
        //     if (TD.B == attributes) System_Actions.add(B)
        String a = currentTd.getA();
        String b = currentTd.getB();

        // System_Actions.add(B + A)
        String action = "System_Actions.add(" + b + " " + a + ")";

        // while (TD≠NULL): collect every basic-attribute B in the sentence
        List<String> attributeNames = new ArrayList<>();
        List<String> attributeActions = new ArrayList<>();
        if (currentTd.getSentence() != null) {
            for (TypedDependency td : currentTd.getSentence().getTypedDependencies()) {
                if (isBasicAttributeB(td)) {
                    attributeNames.add(td.getB());
                    attributeActions.add("System_Actions.add(" + td.getB() + ")");
                }
            }
        }

        result = action;
        if (!attributeActions.isEmpty()) {
            result += "; " + String.join("; ", attributeActions);
        }

        // Phase 1: record the matches; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "System_Actions",
                    capitalizeFirstLetter(b != null ? b : ""),
                    null,
                    null,
                    action);
            for (String name : attributeNames) {
                ruleMatches.create(
                        currentTd,
                        getRuleName(),
                        "System_Actions",
                        capitalizeFirstLetter(name),
                        null,
                        null,
                        "System_Actions.add(" + name + ")");
            }
        }
    }

}