package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Given;
import domox.dom.nlp.TypedDependency;

public abstract class TypedDependencyRuleWithPreviousAndNext extends TypedDependencyRule {
    @Given("previousTd")
    protected TypedDependency previousTd;
    @Given("nextTd")
    protected TypedDependency nextTd;
}
