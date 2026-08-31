package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 12)
public class TDR12 extends TypedDependencyRule {

    @Override
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null || nextTd == null) {
            return false;
        }
        return currentTd.compound() &&
                !nextTd.nsubj() &&
                !nextTd.dobj() &&
                currentTd.isNounA() &&
                currentTd.isNounB();
    }

    @Override
    public void then() {
        //FIXME
    }

}