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
@Rule(order = 2)
public class TDR2 extends TypedDependencyRule {

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
        if (currentTd.nsubj() || currentTd.nsubjpass()) {
            if (currentTd.isVerbA() && currentTd.isNounB() && currentTd.isBasicAttributeB()) {
                answer = true;
            }
        }
        return answer;
    }

    @Then
    public void then() {
        if (previousTd != null && previousTd.compound()) {
            result = "compound(" + currentTd.getB() + ") + Compound(" + currentTd.getA() + ")";
        } else {
            result = "nsubj(" + currentTd.getB() + ")";
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

    private String className() {
        if (previousTd != null && previousTd.compound()) {
            return currentTd.getB() + capitalizeFirstLetter(currentTd.getA());
        } else {
            return currentTd.getB();
        }
    }

    /**
     * Determines the class name for this property.
     * In this rule, the class name is inferred from the governor of the dependency (currentTd.getA()).
     * @return The name of the class to which this property belongs.
     */
    String determineClassName() {
        // Example: "The customer name is required." → "customer" is the class name.
        return capitalizeFirstLetter(currentTd.getA());
    }

}