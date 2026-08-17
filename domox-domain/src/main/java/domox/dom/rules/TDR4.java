package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import domox.dom.uml.Candidate;
import domox.dom.uml.PropertyCandidates;
import jakarta.inject.Inject;

@RuleBean
@Rule(order = 4)
/**
 * Purpose: Likely identifies classes from direct objects (dobj).
 * Example: "The system processes a payment." → payment is identified as a class.
 * Candidate Type: ClassCdd.
 */
public class TDR4 extends TypedDependencyRuleWithPreviousAndNext {

    @Inject
    private PropertyCandidates propertyCandidates;

    @Result
    private String result;

    @Override
    public boolean when() {
        if (currentTd == null) {
            return false;
        }
        boolean answer = false;
        if (currentTd.dobj() || currentTd.iobj() || currentTd.pobj()) {
            if (currentTd.isVerbA() && currentTd.isNounB()) {
                if (currentTd.isBasicAttributeB() || isBlockedVerb(currentTd.getA())) {
                    answer = true;
                }
            }
        }
        return answer;
    }

    @Then
    public void then() {
        if (previousTd.compound()) {
            result = "compound(" + currentTd.getB() + ") + Compound(" + currentTd.getA() + ")";
        } else {
            result = "dobj(" + currentTd.getB() + ")";
        }
    }

    @Override
    protected Candidate createNewCandidate(TypedDependency dependency, Sentence sentence) {
        String className = determineClassName();
        String propertyName = currentTd.getB();
        String type = determineType();

        // Validate inputs
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Class name cannot be null or empty.");
        }
        if (propertyName == null || propertyName.isEmpty()) {
            throw new IllegalArgumentException("Property name cannot be null or empty.");
        }

        return propertyCandidates.findOrCreate(className, propertyName, type);
    }

    @Override
    protected String getResult() {
        return result;
    }

    /**
     * Determines the class name for this property.
     * In this rule, the class name is inferred from the governor of the dependency (currentTd.getA()).
     *
     * @return The name of the class to which this property belongs.
     */
    private String determineClassName() {
        // Example: "The system stores the customer name." → "customer" is the property, but the class might be inferred from context.
        // For now, use the governor of the dependency as the class name.
        return capitalizeFirstLetter(currentTd.getA());
    }

    private boolean isBlockedVerb(String verb) {
        return verb.equalsIgnoreCase("entered") ||
                verb.equalsIgnoreCase("inputted") ||
                verb.equalsIgnoreCase("saved") ||
                verb.equalsIgnoreCase("added") ||
                verb.equalsIgnoreCase("has");
    }
}