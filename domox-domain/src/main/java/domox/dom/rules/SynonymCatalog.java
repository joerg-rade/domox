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
 * Vocabulary of synonym-marker predicate verbs consumed by {@code TDR41}.
 *
 * <p>These are the predicate heads of defining constructions such as
 * "X, also known as Y", "X, termed Y", "X, referred to as Y" — the lexico-syntactic
 * synonym patterns described in {@code SYNONYMS.md} §1.</p>
 *
 * <p>Populated from the {@code domox.nlp.synonym-markers} configuration property
 * so it can be customized per deployment without recompiling the rules. The
 * vocabulary is registered into {@link TypedDependencyPredicates} so the static
 * predicate method ({@code isSynonymMarker(...)}) used by the rule sees it.</p>
 */
@Service
public class SynonymCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(SynonymCatalog.class);

    private final Set<String> synonymMarkers = new HashSet<>();
    private final NlpProperties nlpProperties;

    public SynonymCatalog(NlpProperties nlpProperties) {
        this.nlpProperties = nlpProperties;
    }

    @PostConstruct
    public void init() {
        addAll(synonymMarkers, nlpProperties.getSynonymMarkers(),
                TypedDependencyPredicates::registerSynonymMarkers);
        LOG.info("SynonymCatalog initialised: {} synonym markers", synonymMarkers.size());
    }

    public boolean isSynonymMarker(String lemma) {
        return lemma != null && synonymMarkers.contains(lemma.toLowerCase(Locale.ROOT));
    }

    /** Programmatic extension, e.g. from SeedService or tests. */
    public boolean addSynonymMarker(String lemma) {
        return add(synonymMarkers, lemma, TypedDependencyPredicates::registerSynonymMarkers);
    }

    private void addAll(Set<String> target, List<String> source, Registrar registrar) {
        if (source != null) {
            for (String term : source) {
                String lower = term.toLowerCase(Locale.ROOT);
                target.add(lower);
                // Make the same vocabulary available to TypedDependencyPredicates
                // so that the static predicate method sees it.
                registrar.register(Set.of(lower));
            }
        }
    }

    private boolean add(Set<String> target, String lemma, Registrar registrar) {
        if (lemma == null) {
            return false;
        }
        String lower = lemma.toLowerCase(Locale.ROOT);
        boolean added = target.add(lower);
        if (added) {
            registrar.register(Set.of(lower));
        }
        return added;
    }

    /** Small functional interface so {@link #addAll} can share one code path. */
    @FunctionalInterface
    private interface Registrar {
        void register(Set<String> terms);
    }
}