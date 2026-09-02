package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Given;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import jakarta.inject.Inject;
import lombok.Getter;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.List;

public abstract class TypedDependencyRule {

    @Given("currentTd")
    protected TypedDependency currentTd;

    @Given("previousTd")
    protected TypedDependency previousTd;

    @Given("nextTd")
    protected TypedDependency nextTd;

    @Getter
    @Programmatic
    /* Human-readable explanation of the match, set inside then(). */
    protected String result;

    /**
     * Phase 1: persists RuleMatch records.
     */
    @Inject
    protected RuleMatches ruleMatches;

    public boolean appliesTo(TypedDependency dependency) {
        this.currentTd = dependency;
        return when();
    }

    /**
     * Subclasses must implement this method to define their specific rule logic.
     *
     * @return true if the rule applies to the current dependency, false otherwise.
     */
    public abstract boolean when();

    public abstract void then();

    /**
     * Returns the name of this rule (e.g., "TDR1").
     *
     * @return The fully qualified class name of the rule.
     */
    public final String getRuleName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Phase 1: Analyzes a sentence and creates RuleMatch records for all matching dependencies.
     *
     * @param sentence The sentence to analyze.
     * @return A list of RuleMatch records created (not Candidate objects).
     */
    @Programmatic
    public List<RuleMatch> analyzeAndMatch(Sentence sentence) {
        if (sentence == null) {
            return java.util.Collections.emptyList();
        }
        List<RuleMatch> matches = new java.util.ArrayList<>();
        List<TypedDependency> deps = sentence.getTypedDependencies();
        for (int i = 0; i < deps.size(); i++) {
            TypedDependency dependency = deps.get(i);
            this.currentTd = dependency;
            this.previousTd = i > 0 ? deps.get(i - 1) : null;
            this.nextTd = i + 1 < deps.size() ? deps.get(i + 1) : null;
            if (appliesTo(dependency)) {
                then(); // records the RuleMatch (and sets result)
                // optionally collect the persisted match here
            }
        }
        return matches;
    }

    /**
     * Determines the type of the property based on its name or context.
     *
     * @return The type of the property (e.g., "String", "int", "Integer", "Boolean").
     */
    protected String determineType() {
        String propertyName = currentTd.getB().toLowerCase();

        // Example: Infer type from property name
        if (propertyName.contains("count") || propertyName.contains("number") || propertyName.contains("age")) {
            return "int";
        } else if (propertyName.contains("price") || propertyName.contains("amount")) {
            return "double";
        } else if (propertyName.contains("active") || propertyName.contains("valid") || propertyName.contains("enabled")) {
            return "boolean";
        } else if (propertyName.contains("date") || propertyName.contains("time")) {
            return "LocalDateTime";
        } else {
            // Default to "String" for other cases
            return "String";
        }
    }

    /**
     * Determines the class name for this property.
     * In this rule, the class name is inferred from the governor of the dependency (currentTd.getA()).
     *
     * @return The name of the class to which this property belongs.
     */
    protected String determineClassName() {
        // Example: "The customer name is required." → "customer" is the class name.
        return capitalizeFirstLetter(currentTd.getA());
    }

    protected static String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
}