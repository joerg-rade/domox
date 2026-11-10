package domox.dom.crc.service;

import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.ReviewRationale;
import domox.dom.rdf.OntologyLookup;
import domox.dom.rdf.OntologyTerm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(suggestion.decision()).isEqualTo(ReviewAdvisor.Decision.APPROVE);
        assertThat(suggestion.confidence()).isEqualTo(ReviewAdvisor.Confidence.MEDIUM);
        assertThat(suggestion.rationale()).isNull();
        assertThat(suggestion.reasons().getFirst()).as(suggestion.reasons().toString()).contains("Ontology agrees");
    }

    @Test
    void aClassCandidateTheOntologyKnowsOnlyAsAPropertyIsARejectedTypeMismatch() {
        final ReviewAdvisor.Suggestion suggestion = advisor.suggest(
                classCandidate("Person"), lookupOf(term(OntologyTerm.Kind.PROPERTY)), List.of());

        assertThat(suggestion.decision()).isEqualTo(ReviewAdvisor.Decision.REJECT);
        assertThat(suggestion.rationale()).isEqualTo(ReviewRationale.WRONG_CANDIDATE_TYPE);
        assertThat(suggestion.confidence()).isEqualTo(ReviewAdvisor.Confidence.MEDIUM);
    }

    @Test
    void aPropertyCandidateTheOntologyKnowsAsAPropertyAgrees() {
        assertThat(advisor.agreement(
                attributeCandidate("Person"), lookupOf(term(OntologyTerm.Kind.PROPERTY))))
                .isEqualTo(ReviewAdvisor.Agreement.AGREES);
    }

    @Test
    void anAlreadyApprovedNamesakeIsAHighConfidenceDuplicate() {
        final ReviewAdvisor.Suggestion suggestion = advisor.suggest(
                classCandidate("Pet"), lookupOf(term(OntologyTerm.Kind.CLASS)), List.of(classCandidate("Pet")));

        assertThat(suggestion.decision()).isEqualTo(ReviewAdvisor.Decision.REJECT);
        assertThat(suggestion.rationale()).isEqualTo(ReviewRationale.DUPLICATE);
        assertThat(suggestion.confidence()).isEqualTo(ReviewAdvisor.Confidence.HIGH);
    }

    @Test
    void anUnknownTermYieldsUncertainNotARejection() {
        final ReviewAdvisor.Suggestion suggestion = advisor.suggest(
                classCandidate("PetGroomingBundle"), noMatch(), List.of());

        assertThat(suggestion.decision()).isEqualTo(ReviewAdvisor.Decision.UNCERTAIN);
        assertThat(suggestion.rationale()).isNull();
        assertThat(suggestion.confidence()).isEqualTo(ReviewAdvisor.Confidence.LOW);
        assertThat(suggestion.reasons()).as(suggestion.reasons().toString())
                .anyMatch(r -> r.contains("NOT evidence against"));
    }

    @Test
    void anUnavailableSourceYieldsUncertain() {
        final ReviewAdvisor.Suggestion suggestion = advisor.suggest(
                classCandidate("Pet"), unavailable(), List.of());

        assertThat(suggestion.decision()).isEqualTo(ReviewAdvisor.Decision.UNCERTAIN);
        assertThat(suggestion.reasons()).as(suggestion.reasons().toString())
                .anyMatch(r -> r.contains("unavailable"));
    }

    @Test
    void expectedKindMapsOnlyTheTypesTheOntologiesModel() {
        assertThat(advisor.expectedKind(classCandidate("Pet"))).isEqualTo(OntologyTerm.Kind.CLASS);
        assertThat(advisor.expectedKind(attributeCandidate("age"))).isEqualTo(OntologyTerm.Kind.PROPERTY);
    }

    @Test
    void agreementIsUnknownWhenThereIsNothingToCompare() {
        // no matches at all
        assertThat(advisor.agreement(classCandidate("Pet"), noMatch()))
                .isEqualTo(ReviewAdvisor.Agreement.UNKNOWN);
        // matches, but the source could not be queried
        assertThat(advisor.agreement(classCandidate("Pet"), unavailable()))
                .isEqualTo(ReviewAdvisor.Agreement.UNKNOWN);
    }
}
