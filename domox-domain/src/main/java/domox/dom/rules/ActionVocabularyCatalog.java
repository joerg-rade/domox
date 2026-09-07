package domox.dom.rules;

import domox.dom.nlp.TypedDependencyPredicates;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Vocabulary of business-level action verbs and service concepts consumed by
 * {@code TDR38} (business action detection) and {@code TDR39} (service concept
 * detection).
 *
 * <p>Unlike the negated, non-domain-verb vocabulary tracked by
 * {@link ActionCatalog}, this catalog is a <em>positive</em> list of the verbs
 * and nouns that the rules should recognise. It is populated from the
 * {@code domox.nlp.action-verbs} and {@code domox.nlp.service-nouns}
 * configuration properties, so it can be customized per deployment without
 * recompiling the rules.</p>
 *
 * <p>The vocabulary is registered into {@link TypedDependencyPredicates} so the
 * static predicate methods ({@code isActionVerbA/B}, {@code isServiceNounA/B})
 * used by the rules see it.</p>
 */
@Service
public class ActionVocabularyCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(ActionVocabularyCatalog.class);

    private final Set<String> actionVerbs = new HashSet<>();
    private final Set<String> serviceNouns = new HashSet<>();
    private final NlpProperties nlpProperties;

    public ActionVocabularyCatalog(NlpProperties nlpProperties) {
        this.nlpProperties = nlpProperties;
    }

    @PostConstruct
    public void init() {
        addAll(actionVerbs, nlpProperties.getActionVerbs(), TypedDependencyPredicates::registerActionVerbs);
        addAll(serviceNouns, nlpProperties.getServiceNouns(), TypedDependencyPredicates::registerServiceNouns);
        LOG.info("ActionVocabularyCatalog initialised: {} action verbs, {} service nouns",
                actionVerbs.size(), serviceNouns.size());
    }

    public boolean isActionVerb(String lemma) {
        return lemma != null && actionVerbs.contains(lemma.toLowerCase(Locale.ROOT));
    }

    public boolean isServiceNoun(String lemma) {
        return lemma != null && serviceNouns.contains(lemma.toLowerCase(Locale.ROOT));
    }

    private void addAll(Set<String> target, List<String> source, Registrar registrar) {
        if (source != null) {
            for (String term : source) {
                String lower = term.toLowerCase(Locale.ROOT);
                target.add(lower);
                // Make the same vocabulary available to TypedDependencyPredicates
                // so that static predicate methods see it.
                registrar.register(Set.of(lower));
            }
        }
    }

    /** Programmatic extension, e.g. from SeedService or tests. */
    public boolean addActionVerb(String lemma) {
        return add(actionVerbs, lemma, TypedDependencyPredicates::registerActionVerbs);
    }

    /** Programmatic extension, e.g. from SeedService or tests. */
    public boolean addServiceNoun(String lemma) {
        return add(serviceNouns, lemma, TypedDependencyPredicates::registerServiceNouns);
    }

    private boolean add(Set<String> target, String lemma, Registrar registrar) {
        if (lemma == null) {
            return false;
        }
        String lower = lemma.toLowerCase(Locale.ROOT);
        boolean added = target.add(lower);
        if (added) {
            registrar.register(Set.of(lemma));
        }
        return added;
    }

    /** Small functional interface so {@link #addAll} can share one code path. */
    @FunctionalInterface
    private interface Registrar {
        void register(Set<String> terms);
    }
}
