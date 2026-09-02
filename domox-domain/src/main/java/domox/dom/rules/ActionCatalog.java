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
 * Vocabulary of business-level action verbs and service concepts that the
 * closed sets of TDR27-TDR37 never cover ("offer", "provide", "sell",
 * "train", "grooming", "boarding", "daycare", ...).
 *
 * <p>Extendable at deployment time through the
 * {@code domox.nlp.action-verbs} and {@code domox.nlp.service-nouns}
 * configuration properties, mirroring {@link BasicAttributeCatalog}.</p>
 */
@Service
public class ActionCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(ActionCatalog.class);

    /** Verbs typically performed by the business / system side. */
    private static final Set<String> DEFAULT_BUSINESS_VERBS = Set.of(
            "offer", "provide", "sell", "deliver", "train", "groom", "board",
            "organize", "host", "maintain", "facilitate", "connect", "partner",
            "operate", "care", "capture", "attract", "allow", "leave", "interact",
            "run", "manage", "supply", "serve", "promote");

    /** Verbs typically performed by the customer / external actor. */
    private static final Set<String> DEFAULT_CUSTOMER_VERBS = Set.of(
            "purchase", "buy", "adopt", "order", "book", "request", "enroll");

    /** Service concepts (often gerunds or plain nouns) offered as products. */
    private static final Set<String> DEFAULT_SERVICE_NOUNS = Set.of(
            "grooming", "boarding", "daycare", "adoption", "training",
            "photography", "insurance", "delivery", "socialization",
            "fashion", "workshop", "event", "class", "service");

    private final Set<String> businessVerbs = new HashSet<>();
    private final Set<String> customerVerbs = new HashSet<>();
    private final Set<String> serviceNouns = new HashSet<>();

    public ActionCatalog(
            @Value("${domox.nlp.action-verbs:}") List<String> configuredActionVerbs,
            @Value("${domox.nlp.service-nouns:}") List<String> configuredServiceNouns) {
        businessVerbs.addAll(DEFAULT_BUSINESS_VERBS);
        customerVerbs.addAll(DEFAULT_CUSTOMER_VERBS);
        serviceNouns.addAll(DEFAULT_SERVICE_NOUNS);

        addAll(businessVerbs, configuredActionVerbs);
        addAll(customerVerbs, configuredActionVerbs);
        addAll(serviceNouns, configuredServiceNouns);

        TypedDependencyPredicates.registerActionVerbs(businessVerbs);
        TypedDependencyPredicates.registerActionVerbs(customerVerbs);
        TypedDependencyPredicates.registerServiceNouns(serviceNouns);

        LOG.info("ActionCatalog initialised: {} business verbs, {} customer verbs, {} service nouns",
                businessVerbs.size(), customerVerbs.size(), serviceNouns.size());
    }

    public boolean isBusinessVerb(String lemma) {
        return lemma != null && businessVerbs.contains(lemma.toLowerCase(Locale.ROOT));
    }

    public boolean isCustomerVerb(String lemma) {
        return lemma != null && customerVerbs.contains(lemma.toLowerCase(Locale.ROOT));
    }

    public boolean isActionVerb(String lemma) {
        return isBusinessVerb(lemma) || isCustomerVerb(lemma);
    }

    public boolean isServiceNoun(String lemma) {
        return lemma != null && serviceNouns.contains(lemma.toLowerCase(Locale.ROOT));
    }

    private static void addAll(Set<String> target, List<String> configured) {
        if (configured != null) {
            configured.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(t -> t.trim().toLowerCase(Locale.ROOT))
                    .forEach(target::add);
        }
    }
}