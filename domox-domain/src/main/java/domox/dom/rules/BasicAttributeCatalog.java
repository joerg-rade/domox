package domox.dom.rules;

import domox.dom.nlp.TypedDependencyPredicates;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Vocabulary of "generic" attribute lemmas treated as attributes rather than entities
 * by the TDR rules (see RULES_EXAMPLES.md, TDR1-TDR13).
 *
 * <p>The vocabulary is populated from the {@code domox.nlp.basic-attributes}
 * configuration property, so it can be customized per deployment without
 * recompiling the rules.</p>
 */
@Service
public class BasicAttributeCatalog {

    private final Set<String> attributes = new HashSet<>();
    private final NlpProperties nlpProperties;

    public BasicAttributeCatalog(NlpProperties nlpProperties) {
        this.nlpProperties = nlpProperties;
    }

    @PostConstruct
    public void init() {
        addAll(nlpProperties.getBasicAttributes());
    }

    public boolean contains(String lemma) {
        return lemma != null && attributes.contains(lemma.toLowerCase(Locale.ROOT));
    }

    private void addAll(List<String> verbs) {
        if (verbs != null) {
            for (String verb : verbs) {
                String lower = verb.toLowerCase(Locale.ROOT);
                attributes.add(lower);
                // Make the same vocabulary available to TypedDependencyPredicates
                // so that static predicate methods (isBasicAttributeA/B) see it.
                TypedDependencyPredicates.registerBasicAttributes(Set.of(lower));
            }
        }
    }

    /** Programmatic extension, e.g. from SeedService or tests. */
    public boolean add(String lemma) {
        boolean added = lemma != null && attributes.add(lemma.toLowerCase(Locale.ROOT));
        if (added) {
            TypedDependencyPredicates.registerBasicAttributes(Set.of(lemma));
        }
        return added;
    }

    //FIXME should be used in ATTRIBUTE related rules
    public boolean isBasicAttribute(String lemma) {
        return attributes.contains(lemma);
    }
}