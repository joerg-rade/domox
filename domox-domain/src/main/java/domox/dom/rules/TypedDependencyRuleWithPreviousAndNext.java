package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Given;
import domox.dom.nlp.TypedDependency;

public abstract class TypedDependencyRuleWithPreviousAndNext extends TypedDependencyRule {
    @Given()
    protected TypedDependency previousTd;
    @Given()
    protected TypedDependency nextTd;
}
