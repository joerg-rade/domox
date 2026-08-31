package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 7)
public class TDR7 extends TypedDependencyRule {

    @Override
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        return currentTd.isNounA() && currentTd.isNounB() &&
                (currentTd.nmodIn());
    }

    @Override
    public void then() {
        //FIXME
    }

}