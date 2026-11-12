package domox.dom.nlp;

import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.Review;
import domox.dom.crc.model.ReviewStatus;
import domox.dom.rules.RuleMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level guardrail for the candidate-derived border styling: which lemmas are emphasised (bold,
 * approved), which are dashed (part of a synonym pair), and how a malformed snapshot degrades.
 */
class LexicalGraphBordersTest {

    @Test
    void approvedLemmasAreLowerCasedAndOnlyCoverApprovedCandidates() {
        assertThat(LexicalGraphBorders.approvedLemmas(List.of(
                approved("Customer"), rejected("Order"))))
                .containsExactly("customer");
    }

    @Test
    void approvedLemmasTolerateNullCandidatesAndNullNames() {
        final ClassCdd unnamed = new ClassCdd();
        final List<Candidate> snapshot = new java.util.ArrayList<>();
        snapshot.add(null);
        snapshot.add(unnamed);
        snapshot.add(approved("Customer"));

        assertThat(LexicalGraphBorders.approvedLemmas(snapshot)).containsExactly("customer");
    }

    @Test
    void aCandidateWithNoReviewsIsNotApproved() {
        final ClassCdd candidate = new ClassCdd();
        candidate.setCandidateName("Customer");

        assertThat(LexicalGraphBorders.approvedLemmas(List.of(candidate))).isEmpty();
    }

    @Test
    void synonymLemmasCoverBothSidesOfASynonymMatch() {
        assertThat(LexicalGraphBorders.synonymLemmas(List.of(synonym("Customer", "Client"))))
                .containsExactlyInAnyOrder("customer", "client");
    }

    @Test
    void synonymLemmasIgnoreNonSynonymMatchesAndNullCandidates() {
        final ClassCdd other = new ClassCdd();
        other.setCandidateName("Order");
        other.getRuleMatches().add(match("ClassCdd", "Order", null));

        final List<Candidate> snapshot = new java.util.ArrayList<>();
        snapshot.add(null);
        snapshot.add(other);

        assertThat(LexicalGraphBorders.synonymLemmas(snapshot)).isEmpty();
    }

    @Test
    void theBorderAttributesAreTheOnesTheGeneratorEmits() {
        assertThat(LexicalGraphBorders.SYNONYM_BORDER_STYLE).isEqualTo("rounded,filled,dashed");
        assertThat(LexicalGraphBorders.APPROVED_BORDER_PENWIDTH).isEqualTo(3.0);
    }

    // --- helpers ---

    private static ClassCdd approved(final String name) {
        final ClassCdd candidate = new ClassCdd();
        candidate.setCandidateName(name);
        final Review review = new Review();
        review.setStatus(ReviewStatus.APPROVED);
        candidate.getReviews().add(review);
        return candidate;
    }

    private static ClassCdd rejected(final String name) {
        final ClassCdd candidate = new ClassCdd();
        candidate.setCandidateName(name);
        final Review review = new Review();
        review.setStatus(ReviewStatus.REJECTED);
        candidate.getReviews().add(review);
        return candidate;
    }

    private static ClassCdd synonym(final String name, final String partner) {
        final ClassCdd candidate = new ClassCdd();
        candidate.setCandidateName(name);
        candidate.getRuleMatches().add(match("SynonymCdd", name, partner));
        return candidate;
    }

    private static RuleMatch match(final String type, final String name, final String related) {
        final RuleMatch match = new RuleMatch();
        match.setCandidateType(type);
        match.setCandidateName(name);
        match.setRelatedCandidateName(related);
        return match;
    }
}
