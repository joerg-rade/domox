package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import static domox.dom.nlp.TypedDependencyPredicates.isNsubj;
import static domox.dom.nlp.TypedDependencyPredicates.xcomp;

@RuleBean
@Rule(order = 37)
public class TDR37 extends TypedDependencyRule {

    public TDR37(NlpProperties nlpProperties) {
        super(nlpProperties);
    }

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null || currentTd.getA() == null) {
            return false;
        }

        // Spec: Dependencies = nsubj(A,B) OR xcomp(A,B)
        //        if A is a control-flow verb (configured via domox.nlp.control-flow-verbs)
        if (isNsubj(currentTd) || xcomp(currentTd)) {
            String a = currentTd.getA();
            return isControlFlowVerb(a);
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        // Spec: System_Action.add(A + nummod.B || dobj.B)
        // (falls back to currentTd's B when no nummod/dobj companion exists)
        String a = currentTd.getA();
        String b = currentTd.getB();
        result = "System_Action.add(" + a + " " + b + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "ActionCdd",
                    capitalizeFirstLetter(a),
                    null,
                    null,
                    result);
        }
    }

    private boolean isControlFlowVerb(String verb) {
        return nlpProperties.getControlFlowVerbs().contains(verb.toLowerCase());
    }

}