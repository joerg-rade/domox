package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 33)
public class TDR33 extends TypedDependencyRule {

    public TDR33(NlpProperties nlpProperties) {
        super(nlpProperties);
    }

    @Override
    @When
    public boolean when() {
        if (currentTd == null || currentTd.getA() == null) {
            return false;
        }
        if (isNsubj(currentTd) || nmodBy(currentTd)) {
            return isVerbA(currentTd) && nlpProperties.getReceiveVerbs().contains(currentTd.getA().toLowerCase());
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        String verb = currentTd.getA();
        result = "User_Action.add(" + verb + ")";

        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "ActionCdd",
                    capitalizeFirstLetter(verb),
                    null,
                    null,
                    result);
        }
    }
}