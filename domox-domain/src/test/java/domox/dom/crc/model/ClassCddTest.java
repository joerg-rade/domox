package domox.dom.crc.model;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The review-aware content predicates of {@link ClassCdd}: the single answer to "what is part of the
 * assembled model?", read by the export ({@code generate.ClassDiagram}) and by the empty-class sweep
 * ({@code DomainModels#rejectEmptyClasses}).
 *
 * <p>The point of these tests is the <em>asymmetry</em> they pin down: a class can be approved while
 * its members are not, and a class whose only members were rejected is empty.  That asymmetry is what
 * makes an approved class with an empty body something the pipeline can detect, omit from the diagram
 * and reject — instead of a class the review claims and the model does not contain.</p>
 */
class ClassCddTest {

    @Test
    void approvedAttributes_keepsOnlyApprovedOnes() {
        // given — one approved attribute and one that was never reviewed
        final ClassCdd clazz = empty("Store");
        clazz.attributeList.add(approved(new AttributeCdd("openingHours", "String")));
        clazz.attributeList.add(new AttributeCdd("draft", "String"));

        // when / then
        assertThat(clazz.approvedAttributes())
                .extracting(AttributeCdd::getCandidateName)
                .containsExactly("openingHours");
    }

    @Test
    void approvedResponsibilities_keepsOnlyApprovedOnes() {
        // given
        final ClassCdd clazz = empty("Store");
        clazz.responsibilityList.add(approved(new ResponsibilityCdd("browse", List.of(), "void")));
        clazz.responsibilityList.add(new ResponsibilityCdd("draft", List.of(), "void"));

        // when / then
        assertThat(clazz.approvedResponsibilities())
                .extracting(ResponsibilityCdd::getCandidateName)
                .containsExactly("browse");
    }

    @Test
    void hasOwnModelledContent_isFalseForAClassWhoseMembersWereAllRejected() {
        // given — approved, but every member was left unreviewed
        final ClassCdd clazz = approved(empty("Ghost"));
        clazz.attributeList.add(new AttributeCdd("draft", "String"));
        clazz.responsibilityList.add(new ResponsibilityCdd("draft", List.of(), "void"));

        // when / then — the asymmetry that makes an empty box detectable
        assertThat(clazz.hasOwnModelledContent()).isFalse();
    }

    @Test
    void hasOwnModelledContent_isTrueForEachKindOfOwnMember() {
        // given — one class per kind of own member
        final ClassCdd withAttribute = approved(empty("Store"));
        withAttribute.attributeList.add(approved(new AttributeCdd("openingHours", "String")));

        final ClassCdd withResponsibility = approved(empty("Till"));
        withResponsibility.responsibilityList.add(approved(new ResponsibilityCdd("ring", List.of(), "void")));

        // when / then — either one is enough
        assertThat(withAttribute.hasOwnModelledContent()).isTrue();
        assertThat(withResponsibility.hasOwnModelledContent()).isTrue();
    }

    @Test
    void hasOwnModelledContent_ignoresCollaborations() {
        // given — a class whose only member is a collaboration it owns
        final ClassCdd store = approved(empty("Store"));
        final ClassCdd till = approved(empty("Till"));
        store.addCollaborator(approved(new CollaboratorCdd("has", store, till)));

        // when / then — whether a class is connected is decided on the model (ModelContent), because
        // ingestion attaches a collaboration to its source alone
        assertThat(store.hasOwnModelledContent()).isFalse();
    }

    @Test
    void contentPredicates_treatNullListsAsEmpty() {
        // given — a detached instance whose collections were never initialised
        final ClassCdd clazz = approved(empty("Store"));
        clazz.attributeList = null;
        clazz.responsibilityList = null;

        // when / then — none instead of a NullPointerException
        assertThat(clazz.approvedAttributes()).isEmpty();
        assertThat(clazz.approvedResponsibilities()).isEmpty();
        assertThat(clazz.hasOwnModelledContent()).isFalse();
    }

    @Test
    void toPlantUmlString_rendersOnlyApprovedMembers() {
        // given
        final ClassCdd clazz = approved(empty("Store"));
        clazz.attributeList.add(approved(new AttributeCdd("openingHours", "String")));
        clazz.attributeList.add(new AttributeCdd("draft", "String"));
        clazz.responsibilityList.add(approved(new ResponsibilityCdd("browse", List.of(), "void")));

        // when
        final String plantUml = clazz.toPlantUmlString();

        // then
        assertThat(plantUml).contains("openingHours: String").contains("browse()");
        assertThat(plantUml).doesNotContain("draft");
    }

    // --- helpers --------------------------------------------------------------

    /** A class with no members at all. */
    private static ClassCdd empty(final String name) {
        return new ClassCdd(name, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /** Approves a candidate by attaching an {@code APPROVED} review to it. */
    private static <T extends Candidate> T approved(final T candidate) {
        final Review review = new Review();
        review.setCandidate(candidate);
        review.setStatus(ReviewStatus.APPROVED);
        review.setUser("agent");
        review.setCreatedAt(Timestamp.from(Instant.now()));
        candidate.getReviews().add(review);
        return candidate;
    }
}
