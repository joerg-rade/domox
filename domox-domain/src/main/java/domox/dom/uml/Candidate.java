package domox.dom.uml;

import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.TypedDependencyRule;
import lombok.Data;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.ArrayList;
import java.util.List;

@Data
public abstract class Candidate {

    private Sentence sentence;
    private List<TypedDependency> typedDependencies = new ArrayList<>();
    private List<TypedDependencyRule> matchingRules = new ArrayList<>();
    private String result;

    /**
     * Adds a typed dependency to this candidate.
     * @param dependency The typed dependency to add.
     */
    @Programmatic
    public void addTypedDependency(TypedDependency dependency) {
        if (dependency != null && !typedDependencies.contains(dependency)) {
            typedDependencies.add(dependency);
        }
    }

    /**
     * Adds a matching rule to this candidate.
     * @param rule The rule that matched this candidate.
     */
    @Programmatic
    public void addMatchingRule(TypedDependencyRule rule) {
        if (rule != null && !matchingRules.contains(rule)) {
            matchingRules.add(rule);
        }
    }
}