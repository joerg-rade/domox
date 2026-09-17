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
 * Vocabulary of generalization-related terms consumed by TDR38, TDR39, and TDR40.
 *
 * <p>Populated from the {@code domox.nlp.*} configuration properties:
 * <ul>
 *   <li>{@code auxiliary-verbs} — be-verb forms used by TDR38</li>
 *   <li>{@code indefinite-articles} — "a" and "an" used by TDR38</li>
 *   <li>{@code kind-type-sort-terms} — "kind", "type", "sort" used by TDR38/TDR39</li>
 *   <li>{@code generalization-stop-adjectives} — evaluative/generic adjectives excluded by TDR40</li>
 * </ul>
 * </p>
 */
@Service
public class GeneralizationCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(GeneralizationCatalog.class);

    private final Set<String> beVerbs = new HashSet<>();
    private final Set<String> indefiniteArticles = new HashSet<>();
    private final Set<String> kindTypeSortTerms = new HashSet<>();
    private final Set<String> stopAdjectives = new HashSet<>();
    private final NlpProperties nlpProperties;

    public GeneralizationCatalog(NlpProperties nlpProperties) {
        this.nlpProperties = nlpProperties;
    }

    @PostConstruct
    public void init() {
        addAll(beVerbs, nlpProperties.getCopulaBeVerbs(), TypedDependencyPredicates::registerBeVerbs);
        addAll(indefiniteArticles, nlpProperties.getIndefiniteArticles(), TypedDependencyPredicates::registerIndefiniteArticles);
        addAll(kindTypeSortTerms, nlpProperties.getKindTypeSortTerms(), TypedDependencyPredicates::registerKindTypeSortTerms);
        addAll(stopAdjectives, nlpProperties.getGeneralizationStopAdjectives(), TypedDependencyPredicates::registerStopAdjectives);

        LOG.info("GeneralizationCatalog initialised: {} be-verbs, {} indefinite articles, {} kind/type/sort terms, {} stop adjectives",
                beVerbs.size(), indefiniteArticles.size(), kindTypeSortTerms.size(), stopAdjectives.size());
    }

    public boolean isBeVerb(String lemma) {
        return lemma != null && beVerbs.contains(lemma.toLowerCase(Locale.ROOT));
    }

    public boolean isIndefiniteArticle(String lemma) {
        return lemma != null && indefiniteArticles.contains(lemma.toLowerCase(Locale.ROOT));
    }

    public boolean isKindTypeOrSort(String lemma) {
        if (lemma == null) return false;
        String lower = lemma.toLowerCase(Locale.ROOT);
        for (String term : kindTypeSortTerms) {
            if (lower.contains(term)) return true;
        }
        return false;
    }

    public boolean isStopAdjective(String lemma) {
        return lemma != null && stopAdjectives.contains(lemma.toLowerCase(Locale.ROOT));
    }

    public boolean addBeVerb(String lemma) {
        return add(beVerbs, lemma, TypedDependencyPredicates::registerBeVerbs);
    }

    public boolean addIndefiniteArticle(String lemma) {
        return add(indefiniteArticles, lemma, TypedDependencyPredicates::registerIndefiniteArticles);
    }

    public boolean addKindTypeSortTerm(String lemma) {
        return add(kindTypeSortTerms, lemma, TypedDependencyPredicates::registerKindTypeSortTerms);
    }

    public boolean addStopAdjective(String lemma) {
        return add(stopAdjectives, lemma, TypedDependencyPredicates::registerStopAdjectives);
    }

    private void addAll(Set<String> target, List<String> source, Registrar registrar) {
        if (source != null) {
            for (String term : source) {
                String lower = term.toLowerCase(Locale.ROOT);
                target.add(lower);
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

    @FunctionalInterface
    private interface Registrar {
        void register(Set<String> terms);
    }
}