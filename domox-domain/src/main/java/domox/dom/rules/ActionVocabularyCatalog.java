package domox.dom.rules;

import domox.dom.nlp.vocab.ActionVocabulary;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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
 * <p>The vocabulary is registered into {@link ActionVocabulary} so the
 * static predicate methods ({@code isActionVerbA/B}, {@code isServiceNounA/B})
 * used by the rules see it.</p>
 */
@Slf4j
@Service
public class ActionVocabularyCatalog {

    private final Set<String> actionVerbs = new HashSet<>();
    private final Set<String> serviceNouns = new HashSet<>();
    private final NlpProperties nlpProperties;

    public ActionVocabularyCatalog(NlpProperties nlpProperties) {
        this.nlpProperties = nlpProperties;
    }

    @PostConstruct
    public void init() {
        addAll(actionVerbs, nlpProperties.getActionVerbs(), ActionVocabulary::registerActionVerbs);
        addAll(serviceNouns, nlpProperties.getServiceNouns(), ActionVocabulary::registerServiceNouns);
        log.info("ActionVocabularyCatalog initialised: {} action verbs, {} service nouns",
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
                // Make the same vocabulary available to the action predicates
                // so that static predicate methods see it.
                registrar.register(Set.of(lower));
            }
        }
    }

    /** Programmatic extension, e.g. from SeedService or tests. */
    public boolean addResponsibilityVerb(String lemma) {
        return add(actionVerbs, lemma, ActionVocabulary::registerActionVerbs);
    }

    /** Programmatic extension, e.g. from SeedService or tests. */
    public boolean addServiceNoun(String lemma) {
        return add(serviceNouns, lemma, ActionVocabulary::registerServiceNouns);
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
