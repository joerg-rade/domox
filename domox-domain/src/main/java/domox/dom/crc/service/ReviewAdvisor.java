package domox.dom.crc.service;

import domox.DomainModule;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.ReviewRationale;
import domox.dom.rdf.OntologyLookup;
import domox.dom.rdf.OntologyTerm;
import jakarta.annotation.Priority;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns ontology evidence plus duplicate evidence into an <b>advisory</b> review suggestion.
 *
 * <p>Pure and side-effect free: it is given the candidate, its {@link OntologyLookup} and the
 * already-approved candidates that share its name, and it returns a decision with the reasons behind
 * it.  The MCP tools expose it so an LLM reviewer can weigh the evidence — it is <em>advice</em>, not
 * a decision, and the reviewer remains free to disagree.</p>
 *
 * <p><b>Why the heuristics are conservative.</b>  The reference vocabularies (Schema.org, BFO, BPMN)
 * are generic: most domain nouns of a use-case corpus will not appear in them.  Absence of a match is
 * therefore <em>not</em> evidence against a candidate, so it yields {@link Decision#UNCERTAIN} rather
 * than a rejection.  Only two signals are treated as positive grounds for rejection: a same-name
 * already-approved candidate ({@link ReviewRationale#DUPLICATE}) and a clear type disagreement with
 * the ontology ({@link ReviewRationale#WRONG_CANDIDATE_TYPE}).</p>
 */
@DomainService
@Named(DomainModule.NAMESPACE + ".ReviewAdvisor")
@Priority(PriorityPrecedence.LATE)
public class ReviewAdvisor {

    /**
     * The ontology kind a candidate type <em>should</em> map onto, or {@code null} when the type has
     * no ontology counterpart (an operation or an association is not a term in these vocabularies).
     */
    @Programmatic
    public OntologyTerm.Kind expectedKind(final Candidate candidate) {
        if (candidate instanceof ClassCdd) {
            return OntologyTerm.Kind.CLASS;
        }
        if (candidate instanceof AttributeCdd) {
            return OntologyTerm.Kind.PROPERTY;
        }
        return null;
    }

    /**
     * Whether the ontology agrees with the candidate's type.
     *
     * @return {@link Agreement#AGREES} when a match has the expected kind, {@link Agreement#MISMATCH}
     *         when matches exist but none has it, {@link Agreement#UNKNOWN} otherwise (no matches, no
     *         expectation, or the source could not be queried)
     */
    @Programmatic
    public Agreement agreement(final Candidate candidate, final OntologyLookup lookup) {
        final OntologyTerm.Kind expected = expectedKind(candidate);
        if (expected == null || lookup == null || !lookup.hasMatches()) {
            return Agreement.UNKNOWN;
        }
        return lookup.terms().stream().anyMatch(term -> term.kind() == expected)
                ? Agreement.AGREES
                : Agreement.MISMATCH;
    }

    /**
     * Suggests what to do with {@code candidate}, with the reasons that led there.
     *
     * @param approvedDuplicates already-approved candidates sharing this candidate's name (see
     *                           {@code Reviews#findApprovedWithSameName})
     */
    @Programmatic
    public Suggestion suggest(
            final Candidate candidate,
            final OntologyLookup lookup,
            final List<Candidate> approvedDuplicates) {

        final List<String> reasons = new ArrayList<>();
        final String name = candidate != null ? candidate.getCandidateName() : null;
        final String typeName = candidate != null ? candidate.getClass().getSimpleName() : "candidate";
        final Agreement agreement = agreement(candidate, lookup);
        final List<OntologyTerm> matches = lookup != null ? lookup.terms() : List.of();

        if (approvedDuplicates != null && !approvedDuplicates.isEmpty()) {
            reasons.add("Duplicate: " + approvedDuplicates.size()
                    + " already-approved candidate(s) share the name '" + name + "': "
                    + approvedDuplicates.stream()
                            .map(c -> c.getCandidateName() + " (id " + c.getId() + ")")
                            .collect(Collectors.joining(", ")));
            return new Suggestion(Decision.REJECT, ReviewRationale.DUPLICATE, Confidence.HIGH, reasons);
        }

        if (lookup != null && !lookup.sourceAvailable()) {
            reasons.add("Ontology source unavailable - no evidence either way.");
            if (lookup.message() != null) {
                reasons.add(lookup.message());
            }
            return new Suggestion(Decision.UNCERTAIN, null, Confidence.LOW, reasons);
        }

        if (agreement == Agreement.MISMATCH) {
            reasons.add("Type mismatch: a " + typeName + " candidate, but the ontology knows '" + name
                    + "' only as " + kindsOf(matches) + ".");
            reasons.add("Sources: " + graphsOf(matches) + ".");
            return new Suggestion(Decision.REJECT, ReviewRationale.WRONG_CANDIDATE_TYPE, Confidence.MEDIUM, reasons);
        }

        if (agreement == Agreement.AGREES) {
            reasons.add("Ontology agrees: '" + name + "' is a " + expectedKind(candidate)
                    + " in " + graphsOf(matches) + ".");
            final String definition = firstDefinition(matches);
            if (definition != null) {
                reasons.add("Definition: " + definition);
            }
            return new Suggestion(Decision.APPROVE, null, Confidence.MEDIUM, reasons);
        }

        if (!matches.isEmpty()) {
            reasons.add("Ontology knows '" + name + "' as " + kindsOf(matches) + " (" + graphsOf(matches)
                    + "), but a " + typeName + " has no ontology counterpart to agree with.");
            return new Suggestion(Decision.APPROVE, null, Confidence.LOW, reasons);
        }

        reasons.add("No ontology term matches '" + name + "'.");
        reasons.add("The reference vocabularies are generic, so this is NOT evidence against the candidate.");
        if (lookup != null && lookup.message() != null && !lookup.message().isBlank()) {
            reasons.add(lookup.message());
        }
        return new Suggestion(Decision.UNCERTAIN, null, Confidence.LOW, reasons);
    }

    private static String kindsOf(final List<OntologyTerm> matches) {
        return matches.stream()
                .map(term -> String.valueOf(term.kind()))
                .distinct()
                .collect(Collectors.joining("/"));
    }

    private static String graphsOf(final List<OntologyTerm> matches) {
        return matches.stream()
                .map(OntologyTerm::graph)
                .filter(graph -> graph != null && !graph.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private static String firstDefinition(final List<OntologyTerm> matches) {
        return matches.stream()
                .map(OntologyTerm::definition)
                .filter(definition -> definition != null && !definition.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** How well the ontology's view of a term matches the candidate's type. */
    public enum Agreement {
        /** A match carries the kind the candidate type implies. */
        AGREES,
        /** Matches exist, but none has the implied kind. */
        MISMATCH,
        /** Nothing to compare: no matches, no type expectation, or the source was unavailable. */
        UNKNOWN
    }

    /** The advised action. */
    public enum Decision {
        APPROVE,
        REJECT,
        /** No signal worth acting on — the reviewer decides on other evidence. */
        UNCERTAIN
    }

    /** How much weight the suggestion deserves. */
    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }

    /**
     * An advisory suggestion.
     *
     * @param decision   what the evidence leans towards
     * @param rationale  the rejection rationale, set only when {@code decision} is {@link Decision#REJECT}
     * @param confidence how much weight to give it
     * @param reasons    the evidence behind the suggestion, in the order it was weighed
     */
    public record Suggestion(
            Decision decision,
            ReviewRationale rationale,
            Confidence confidence,
            List<String> reasons) {

        public Suggestion {
            reasons = List.copyOf(reasons);
        }
    }
}
