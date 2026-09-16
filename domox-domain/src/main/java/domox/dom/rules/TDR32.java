package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 32)
public class TDR32 extends TypedDependencyRule {

    public TDR32(NlpProperties nlpProperties) {
        super(nlpProperties);
    }

    @Override
    @When
    public boolean when() {
        if (currentTd == null || currentTd.getA() == null || currentTd.getB() == null) {
            return false;
        }
        if (isNsubj(currentTd) || nmodBy(currentTd)) {
            String verbA = currentTd.getA().toLowerCase();
            String actorB = currentTd.getB();

            if (isVerbA(currentTd) && nlpProperties.getUserInputVerbs().contains(verbA) && isExternalActor(actorB)) {
                return true;
            }

            return isVerbA(currentTd) && nlpProperties.getSystemOutputVerbs().contains(verbA) && isSystem(actorB);
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        String verb = currentTd.getA();
        String actor = currentTd.getB();

        if (nlpProperties.getUserInputVerbs().contains(verb.toLowerCase()) && isExternalActor(actor)) {
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
        } else if (nlpProperties.getSystemOutputVerbs().contains(verb.toLowerCase()) && isSystem(actor)) {
            result = "System_Actions.add(" + verb + ")";

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

    private boolean isExternalActor(String actor) {
        return !actor.equalsIgnoreCase("system");
    }

    private boolean isSystem(String actor) {
        return actor.equalsIgnoreCase("system");
    }
}