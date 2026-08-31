package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

/**
 * Purpose: Likely identifies classes from passive constructions (nsubjpass).
 * Example: "The request was approved by the manager." → Request and Manager are identified as classes.
 * Candidate Type: ClassCdd.
 */
@RuleBean
@Rule(order = 10)
public class TDR10 extends TypedDependencyRule {

    @Override
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        return currentTd.nmodPoss() && currentTd.isNounA() && currentTd.isNounB();
    }

    @Override
    public void then() {
        //FIXME
    }
}