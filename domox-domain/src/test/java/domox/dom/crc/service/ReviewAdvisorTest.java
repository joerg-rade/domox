package domox.dom.crc.service;

import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.ReviewRationale;
import domox.dom.rdf.OntologyLookup;
import domox.dom.rdf.OntologyTerm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardrail for {@link ReviewAdvisor} — the advisory suggestion behind the {@code suggestReview} MCP
 * tool.
 * <p>
 * The point of these tests is that the advice stays <em>conservative</em>: an ontology that simply does
 * not know a term must never be turned into a rejection, because the reference vocabularies are
 * generic.
 */
class ReviewAdvisorTest {

    private final ReviewAdvisor advisor = new ReviewAdvisor();

    private static ClassCdd classCandidate(final String name) {
        final ClassCdd candidate = new ClassCdd();
        candidate.setCandidateName(name);
        candidate.setCandidateType("ClassCdd");
        return candidate;
    }

    private static AttributeCdd attributeCandidate(final String name) {
        final AttributeCdd candidate = new AttributeCdd();
        candidate.setCandidateName(name);
        candidate.setCandidateType("PropertyCdd");
        return candidate;
    }

    private static OntologyTerm term(final OntologyTerm.Kind kind) {
        return new OntologyTerm("https://schema.org/", "https://schema.org/Person", "Person",
                kind, "Thing", "A person.", List.of());
    }

    private static OntologyLookup lookupOf(final OntologyTerm... terms) {
        return new OntologyLookup("x", "test-source", List.of(terms), true, null);
    }

    private static OntologyLookup unavailable() {
        return new OntologyLookup("x", "test-source", List.of(), false, "Could not query test-source: boom");
    }

    private static OntologyLookup noMatch() {
        return new OntologyLookup("x", "test-source", List.of(), true, "No ontology term matches 'x'.");
    }

    @Test
    void aClassCandidateTheOntologyKnowsAsAClassIsApproved() {
        final ReviewAdvisor.Suggestion suggestion = advisor.suggest(
                classCandidate("Person"), lookupOf(term(OntologyTerm.Kind.CLASS)), List.of());

        assertEquals(ReviewAdvisor.Decision.APPROVE, suggestion.decision());
        assertEquals(ReviewAdvisor.Confidence.MEDIUM, suggestion.confidence());
        assertEquals(null, suggestion.rationale());
        assertTrue(suggestion.reasons().get(0).contains("Ontology agrees"), suggestion.reasons().toString());
    }

    @Test
    void aClassCandidateTheOntologyKnowsOnlyAsAPropertyIsARejectedTypeMismatch() {
        final ReviewAdvisor.Suggestion suggestion = advisor.suggest(
                classCandidate("Person"), lookupOf(term(OntologyTerm.Kind.PROPERTY)), List.of());

        assertEquals(ReviewAdvisor.Decision.REJECT, suggestion.decision());
        assertEquals(ReviewRationale.WRONG_CANDIDATE_TYPE, suggestion.rationale());
        assertEquals(ReviewAdvisor.Confidence.MEDIUM, suggestion.confidence());
    }

    @Test
    void aPropertyCandidateTheOntologyKnowsAsAPropertyAgrees() {
        assertEquals(ReviewAdvisor.Agreement.AGREES, advisor.agreement(
                attributeCandidate("Person"), lookupOf(term(OntologyTerm.Kind.PROPERTY))));
    }

    @Test
    void anAlreadyApprovedNamesakeIsAHighConfidenceDuplicate() {
        final ReviewAdvisor.Suggestion suggestion = advisor.suggest(
                classCandidate("Pet"), lookupOf(term(OntologyTerm.Kind.CLASS)), List.of(classCandidate("Pet")));

        assertEquals(ReviewAdvisor.Decision.REJECT, suggestion.decision());
        assertEquals(ReviewRationale.DUPLICATE, suggestion.rationale());
        assertEquals(ReviewAdvisor.Confidence.HIGH, suggestion.confidence());
    }

    @Test
    void anUnknownTermYieldsUncertainNotARejection() {
        final ReviewAdvisor.Suggestion suggestion = advisor.suggest(
                classCandidate("PetGroomingBundle"), noMatch(), List.of());

        assertEquals(ReviewAdvisor.Decision.UNCERTAIN, suggestion.decision());
        assertEquals(null, suggestion.rationale());
        assertEquals(ReviewAdvisor.Confidence.LOW, suggestion.confidence());
        assertTrue(suggestion.reasons().stream().anyMatch(r -> r.contains("NOT evidence against")),
                suggestion.reasons().toString());
    }

    @Test
    void anUnavailableSourceYieldsUncertain() {
        final ReviewAdvisor.Suggestion suggestion = advisor.suggest(
                classCandidate("Pet"), unavailable(), List.of());

        assertEquals(ReviewAdvisor.Decision.UNCERTAIN, suggestion.decision());
        assertTrue(suggestion.reasons().stream().anyMatch(r -> r.contains("unavailable")),
                suggestion.reasons().toString());
    }

    @Test
    void expectedKindMapsOnlyTheTypesTheOntologiesModel() {
        assertEquals(OntologyTerm.Kind.CLASS, advisor.expectedKind(classCandidate("Pet")));
        assertEquals(OntologyTerm.Kind.PROPERTY, advisor.expectedKind(attributeCandidate("age")));
    }

    @Test
    void agreementIsUnknownWhenThereIsNothingToCompare() {
        // no matches at all
        assertEquals(ReviewAdvisor.Agreement.UNKNOWN,
                advisor.agreement(classCandidate("Pet"), noMatch()));
        // matches, but the source could not be queried
        assertEquals(ReviewAdvisor.Agreement.UNKNOWN,
                advisor.agreement(classCandidate("Pet"), unavailable()));
    }
}
