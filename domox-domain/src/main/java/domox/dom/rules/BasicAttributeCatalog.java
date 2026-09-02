package domox.dom.rules;

import domox.dom.nlp.TypedDependencyPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Vocabulary of "generic" attribute lemmas treated as attributes rather than entities
 * by the TDR rules (see RULES_EXAMPLES.md, TDR1-TDR13).
 *
 * <p>The vocabulary is the union of a built-in default set and any lemmas from the
 * {@code domox.nlp.basic-attributes} configuration property, so it can be extended at
 * deployment time without recompiling the rules.</p>
 */
@Service
public class BasicAttributeCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(BasicAttributeCatalog.class);

    private static final Set<String> DEFAULT_ATTRIBUTES = Set.of(
            // original vocabulary
            "name", "number", "type", "address", "level", "date", "time",
            // additional generic attribute lemmas that were previously misclassified as entities
            "id", "code", "status", "description", "title", "label", "value",
            "amount", "quantity", "size", "color", "email", "phone", "username",
            "password", "comment", "note", "version", "category", "priority");

    private final Set<String> attributes = new HashSet<>();

    public BasicAttributeCatalog(
            @Value("${domox.nlp.basic-attributes:}") List<String> configured) {
        attributes.addAll(DEFAULT_ATTRIBUTES);
        if (configured != null) {
            configured.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(t -> t.trim().toLowerCase(Locale.ROOT))
                    .forEach(attributes::add);
        }
        TypedDependencyPredicates.registerBasicAttributes(attributes);
        LOG.info("BasicAttributeCatalog initialised with {} attribute lemmas", attributes.size());
    }

    public boolean contains(String lemma) {
        return lemma != null && attributes.contains(lemma.toLowerCase(Locale.ROOT));
    }

    /** Programmatic extension, e.g. from SeedService or tests. */
    public boolean add(String lemma) {
        boolean added = lemma != null && attributes.add(lemma.toLowerCase(Locale.ROOT));
        if (added) {
            TypedDependencyPredicates.registerBasicAttributes(Set.of(lemma));
        }
        return added;
    }
}