package domox.dom.crc.model;

/**
 * How a {@link SubDomain} contributes to the business — the strategic DDD classification.
 *
 * <p>This is <em>business judgement</em>, not something the use-case corpus records: two
 * sub-domains with identical evidence can be core in one organisation and generic in another.
 * The {@link #UNCLASSIFIED} constant therefore exists so a sub-domain can be created from the
 * corpus evidence first and classified later, instead of being silently mislabelled.</p>
 *
 * <ul>
 *   <li>{@link #CORE} — the differentiating capability: the organisation's competitive advantage,
 *       worth its own best people and a bespoke model.</li>
 *   <li>{@link #SUPPORTING} — custom to the organisation but not differentiating; worth building,
 *       not worth optimising.</li>
 *   <li>{@link #GENERIC} — solved the same way everywhere; a candidate for buying or adopting a
 *       standard solution (and for the reference vocabularies to already cover).</li>
 *   <li>{@link #UNCLASSIFIED} — not yet decided; the default for a freshly created sub-domain.</li>
 * </ul>
 */
public enum SubDomainClassification {

    CORE,
    SUPPORTING,
    GENERIC,
    UNCLASSIFIED
}
