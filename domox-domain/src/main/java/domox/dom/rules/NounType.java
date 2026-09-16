package domox.dom.rules;

/**
 * Noun-type categories inspired by Höhn (2003) and Coad & Yourdon (1991) for
 * classifying noun lemmas in TDR rules.
 *
 * <p>These replace or complement the flat {@code basic-attributes} list by
 * providing a structured taxonomy. Each category influences how the noun is
 * treated by the extraction rules:</p>
 *
 * <ul>
 *   <li><b>GENERIC_ATTRIBUTE</b> — primitive-like nouns (name, number, type) that
 *       should always be treated as attributes, never as entities.</li>
 *   <li><b>ROLE_NOUN</b> — nouns representing roles played by persons or things
 *       (customer, user, employee); often valid entities with few properties.</li>
 *   <li><b>DOMAIN_ENTITY</b> — core domain nouns (order, product, invoice) that
 *       are primary entity candidates.</li>
 *   <li><b>ABSTRACT_CONCEPT</b> — abstract nouns describing qualities, states,
 *       or measurements (status, priority, level).</li>
 *   <li><b>LOCATION</b> — geographic or structural places (address, office, warehouse).</li>
 *   <li><b>TEMPORAL</b> — time-related nouns (date, duration, timestamp).</li>
 *   <li><b>COLLECTION</b> — collective nouns (list, set, group) that typically
 *       indicate multiplicity.</li>
 * </ul>
 */
public enum NounType {
    GENERIC_ATTRIBUTE,
    ROLE_NOUN,
    DOMAIN_ENTITY,
    ABSTRACT_CONCEPT,
    LOCATION,
    TEMPORAL,
    COLLECTION
}