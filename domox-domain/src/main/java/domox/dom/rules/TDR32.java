package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 32)
public class TDR32 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependencies= nsubj(A,B) OR nmod:by(A,B)
        if (currentTd.nsubj() || currentTd.nmodBy()) {
            String verbA = currentTd.getA();
            String actorB = currentTd.getB();

            // if A=VB and A in {input, enter, fill, click, select, add, submit, choose}
            // AND B=External Actor
            if (currentTd.isVerbA() && isUserInputVerb(verbA) && isExternalActor(actorB)) {
                return true;
            }

            // if A=VB and A in {display, output, retrieve, show, view, print, calculate,
            // process, update, delete, search, modify, edit, remove}
            // AND B= System
            if (currentTd.isVerbA() && isSystemOutputVerb(verbA) && isSystem(actorB)) {
                return true;
            }
        }
        return false;
    }

    @Then
    public void then() {
        String verb = currentTd.getA();
        String actor = currentTd.getB();

        if (isUserInputVerb(verb) && isExternalActor(actor)) {
            result = "User_Action.add(" + verb + ")";
        } else if (isSystemOutputVerb(verb) && isSystem(actor)) {
            result = "System_Actions.add(" + verb + ")";
        }
    }

    private boolean isUserInputVerb(String verb) {
        return verb.equalsIgnoreCase("input") ||
               verb.equalsIgnoreCase("enter") ||
               verb.equalsIgnoreCase("fill") ||
               verb.equalsIgnoreCase("click") ||
               verb.equalsIgnoreCase("select") ||
               verb.equalsIgnoreCase("add") ||
               verb.equalsIgnoreCase("submit") ||
               verb.equalsIgnoreCase("choose");
    }

    private boolean isSystemOutputVerb(String verb) {
        return verb.equalsIgnoreCase("display") ||
               verb.equalsIgnoreCase("output") ||
               verb.equalsIgnoreCase("retrieve") ||
               verb.equalsIgnoreCase("show") ||
               verb.equalsIgnoreCase("view") ||
               verb.equalsIgnoreCase("print") ||
               verb.equalsIgnoreCase("calculate") ||
               verb.equalsIgnoreCase("process") ||
               verb.equalsIgnoreCase("update") ||
               verb.equalsIgnoreCase("delete") ||
               verb.equalsIgnoreCase("search") ||
               verb.equalsIgnoreCase("modify") ||
               verb.equalsIgnoreCase("edit") ||
               verb.equalsIgnoreCase("remove");
    }

    private boolean isExternalActor(String actor) {
        // External actor is typically user, customer, etc.
        return !actor.equalsIgnoreCase("system");
    }

    private boolean isSystem(String actor) {
        return actor.equalsIgnoreCase("system");
    }

}

