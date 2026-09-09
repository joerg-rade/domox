package domox.dom.uml;

import domox.dom.AbstractEntity;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.TypedDependencyRule;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import lombok.Data;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all UML class-diagram candidate entities.
 * <p>
 * Holds the sentence / typed-dependency provenance of a candidate, and passes on
 * the {@link AbstractEntity} {@code id}/{@code version} mapping to its concrete
 * {@code @Entity} subclasses via the {@link MappedSuperclass} inheritance chain.
 */
@Data
@MappedSuperclass
public abstract class Candidate extends AbstractEntity {

    @Transient
    private Sentence sentence;
    @Transient
    private List<TypedDependency> typedDependencies = new ArrayList<>();
    @Transient
    private List<TypedDependencyRule> matchingRules = new ArrayList<>();
    @Transient
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