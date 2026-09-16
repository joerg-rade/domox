package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Given;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import jakarta.inject.Inject;
import lombok.Getter;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.List;
import java.util.stream.Collectors;

import static domox.dom.nlp.TypedDependencyPredicates.*;

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

    /**
     * NLP configuration properties (vocabulary lists, disabled rules, etc.)
     * shared by many TDR rules.  Injected via constructor so the field is
     * available to all subclasses without each re-declaring it.
     */
    protected NlpProperties nlpProperties;

    protected TypedDependencyRule() {
        // no-op: for subclasses that do not need NlpProperties
    }

    protected TypedDependencyRule(NlpProperties nlpProperties) {
        this.nlpProperties = nlpProperties;
    }

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
     */
    @Programmatic
    public void analyzeAndMatch(Sentence sentence) {
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

    /**
     * Finds the possessor class name if {@code nounGloss} is the governor of an
     * {@code nmod:poss} dependency in the same sentence.  Handles both noun
     * possessors (NN) and pronominal possessors (PRP$).  Pronominal possessors
     * are resolved by scanning for the nearest {@code nsubj} or
     * {@code nsubjpass} entity in the sentence.
     *
     * @param nounGloss the governor noun to look up (e.g. {@code "pets"})
     * @param sentence  the containing sentence (may be null)
     * @return the owning class name (e.g. {@code "Customer"}), or {@code null}
     *         if the noun is not possessed or no possessor can be resolved
     */
    @Programmatic
    protected String possessorClassIfPossessed(String nounGloss, Sentence sentence) {
        if (sentence == null || nounGloss == null) {
            return null;
        }
        for (TypedDependency td : sentence.getTypedDependencies()) {
            if (nmodPoss(td) && nounGloss.equals(td.getGovernorGloss())) {
                if (isNounB(td)) {
                    return capitalizeFirstLetter(td.getB());
                }
                if (td.getDependentPos() == domox.dom.nlp.PartOfSpeechType.PRP$) {
                    return resolvePronominalPossessor(sentence);
                }
            }
        }
        return null;
    }

    /**
     * Returns the glosses of every adjectival modifier whose governor matches
     * {@code nounGloss} in the given sentence.
     * <p>
     * Example: for {@code "beloved pets"} with {@code amod(pets, beloved)},
     * returns {@code ["beloved"]}.
     */
    @Programmatic
    protected List<String> modifierGlossesForGovernor(String nounGloss, Sentence sentence) {
        if (sentence == null || nounGloss == null) {
            return List.of();
        }
        return sentence.getTypedDependencies().stream()
                .filter(td -> amod(td) && nounGloss.equals(td.getGovernorGloss()))
                .map(TypedDependency::getB)
                .collect(Collectors.toList());
    }

    /**
     * Resolves a pronominal possessor ({@code PRP$}) to the nearest
     * entity-class candidate in the sentence by scanning for the first
     * {@code nsubj} or {@code nsubjpass} whose dependent is a noun.
     *
     * @return the class name of the resolved entity, or {@code null}
     */
    @Programmatic
    protected String resolvePronominalPossessor(Sentence sentence) {
        if (sentence == null) {
            return null;
        }
        for (TypedDependency td : sentence.getTypedDependencies()) {
            if ((isNsubj(td) || isNsubjPass(td)) && isNounB(td)) {
                return capitalizeFirstLetter(td.getB());
            }
        }
        return null;
    }
}