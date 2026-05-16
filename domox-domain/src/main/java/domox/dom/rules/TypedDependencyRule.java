package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Given;
import domox.dom.nlp.TypedDependency;

public abstract class TypedDependencyRule {
    @Given("currentTd")
    protected TypedDependency currentTd;
}
