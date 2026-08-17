package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Given;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import domox.dom.uml.Candidate;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.List;

public abstract class TypedDependencyRule {

    @Given("currentTd")
    protected TypedDependency currentTd;

    public boolean appliesTo(TypedDependency dependency) {
        this.currentTd = dependency;
        return when();
    }

    /**
     * Subclasses must implement this method to define their specific rule logic.
     * @return true if the rule applies to the current dependency, false otherwise.
     */
    public abstract boolean when();

    /**
     * Creates or updates a Candidate object based on the rule match.
     * Subclasses can override this method to customize candidate creation.
     * @param dependency The dependency that triggered the rule.
     * @param sentence The sentence containing the dependency.
     * @param existingCandidates List of existing candidates to check for matches.
     * @return A Candidate object representing the rule match, or null if no candidate is created.
     */
    public Candidate createCandidate(TypedDependency dependency, Sentence sentence, List<Candidate> existingCandidates) {
        // Check if a matching candidate already exists
        Candidate candidate = findMatchingCandidate(dependency, sentence, existingCandidates);

        if (candidate == null) {
            // Create a new candidate if no match is found
            candidate = createNewCandidate(dependency, sentence);
        }

        // Add the current rule and dependency to the candidate
        if (candidate != null) {
            candidate.addMatchingRule(this);
            candidate.addTypedDependency(dependency);
            candidate.setResult(getResult());
        }

        return candidate;
    }

    /**
     * Creates a new Candidate object based on the rule match.
     * Subclasses must override this method to create the appropriate Candidate subclass.
     * @param dependency The dependency that triggered the rule.
     * @param sentence The sentence containing the dependency.
     * @return A new Candidate object.
     */
    @Programmatic
    protected Candidate createNewCandidate(TypedDependency dependency, Sentence sentence) {
        return null;
    }

    /**
     * Finds an existing candidate that matches the dependency and sentence.
     * Subclasses can override this method to customize matching logic.
     * @param dependency The dependency that triggered the rule.
     * @param sentence The sentence containing the dependency.
     * @param existingCandidates List of existing candidates to check.
     * @return The matching candidate, or null if no match is found.
     */
    @Programmatic
    protected Candidate findMatchingCandidate(TypedDependency dependency, Sentence sentence, List<Candidate> existingCandidates) {
        if (existingCandidates == null || existingCandidates.isEmpty()) {
            return null;
        }

        // Example: Match candidates by sentence and dependency
        for (Candidate candidate : existingCandidates) {
            if (candidate.getSentence().equals(sentence) &&
                    candidate.getTypedDependencies().contains(dependency)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Subclasses can override this method to provide a custom result.
     * @return The result of the rule evaluation (e.g., "nsubj(B)").
     */
    @Programmatic
    protected String getResult() {
        return null;
    }

    /**
     * Returns the name of this rule (e.g., "TDR1").
     * @return The fully qualified class name of the rule.
     */
    public final String getRuleName() {
        return this.getClass().getSimpleName();
    }

    public List<Candidate> analyze(Sentence sentence) {
        return null; //FIXME
    }

    /**
     * Capitalizes the first letter of a string.
     * @param input The string to capitalize.
     * @return The string with the first letter capitalized, or the original string if it is null or empty.
     */
    protected static String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    /**
     * Determines the type of the property based on its name or context.
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
}