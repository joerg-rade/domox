package domox.dom.rules;

import domox.dom.nlp.TypedDependencyPredicates;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Configurable lexicon that maps noun lemmas to {@link NounType} categories
 * (inspired by Höhn 2003). Loaded from {@code domox.nlp.noun-types.*} config.
 *
 * <p>This extends the flat {@link BasicAttributeCatalog} with a structured
 * taxonomy. Rules can query the type of a noun to decide entity vs. attribute
 * treatment with finer granularity than the binary
 * {@code isBasicAttribute} check.</p>
 *
 * <p>Registered into {@link TypedDependencyPredicates} at startup so the static
 * predicate methods used by TDR rules see it.</p>
 */
@Service
public class NounTypeLexicon {

    private static final Logger LOG = LoggerFactory.getLogger(NounTypeLexicon.class);

    private final Map<String, NounType> lexicon = new HashMap<>();
    private final NlpProperties nlpProperties;

    public NounTypeLexicon(NlpProperties nlpProperties) {
        this.nlpProperties = nlpProperties;
    }

    @PostConstruct
    public void init() {
        // Load legacy basic-attributes config into GENERIC_ATTRIBUTE category
        loadCategory(nlpProperties.getBasicAttributes(), NounType.GENERIC_ATTRIBUTE);

        // Load each noun-type list from config into the lexicon
        loadCategory(nlpProperties.getNounTypeGenericAttributes(), NounType.GENERIC_ATTRIBUTE);
        loadCategory(nlpProperties.getNounTypeRoleNouns(), NounType.ROLE_NOUN);
        loadCategory(nlpProperties.getNounTypeDomainEntities(), NounType.DOMAIN_ENTITY);
        loadCategory(nlpProperties.getNounTypeAbstractConcepts(), NounType.ABSTRACT_CONCEPT);
        loadCategory(nlpProperties.getNounTypeLocations(), NounType.LOCATION);
        loadCategory(nlpProperties.getNounTypeTemporals(), NounType.TEMPORAL);
        loadCategory(nlpProperties.getNounTypeCollections(), NounType.COLLECTION);

        // Register every lemma into the static predicates for runtime lookup
        for (var entry : lexicon.entrySet()) {
            TypedDependencyPredicates.registerNounType(entry.getKey(), entry.getValue());
            if (entry.getValue() == NounType.GENERIC_ATTRIBUTE) {
                TypedDependencyPredicates.registerBasicAttributes(Set.of(entry.getKey()));
            }
        }

        LOG.info("NounTypeLexicon initialised: {} entries across {} categories",
                lexicon.size(), NounType.values().length);
    }

    /**
     * Returns the {@link NounType} for the given lemma, or {@code null}
     * if the lemma is not in the lexicon.
     */
    public NounType classify(String lemma) {
        if (lemma == null) return null;
        return lexicon.get(lemma.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns an unmodifiable view of the full lexicon.
     */
    public Map<String, NounType> getLexicon() {
        return Collections.unmodifiableMap(lexicon);
    }

    /**
     * Programmatically add a lemma with a given type (e.g., from SeedService or tests).
     *
     * @return true if the lemma was added (was not already present)
     */
    public boolean add(String lemma, NounType type) {
        if (lemma == null || type == null) return false;
        String lower = lemma.toLowerCase(Locale.ROOT);
        boolean added = lexicon.put(lower, type) == null;
        if (added && type == NounType.GENERIC_ATTRIBUTE) {
            TypedDependencyPredicates.registerBasicAttributes(Set.of(lemma));
        }
        return added;
    }

    // ----------------------------------------------------------------

    private void loadCategory(List<String> lemmas, NounType type) {
        if (lemmas != null) {
            for (String lemma : lemmas) {
                if (lemma != null) {
                    lexicon.put(lemma.toLowerCase(Locale.ROOT), type);
                }
            }
        }
    }

    private void loadCategory(Set<String> lemmas, NounType type) {
        if (lemmas != null) {
            for (String lemma : lemmas) {
                if (lemma != null) {
                    lexicon.put(lemma.toLowerCase(Locale.ROOT), type);
                }
            }
        }
    }
}