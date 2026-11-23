package domox.dom.crc.model;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The assembled model: which classes and which collaborations are part of it.
 *
 * <p>The load-bearing test here is
 * {@link #includedClasses_keepsAClassThatIsOnlyTheTargetOfAMaterialCollaboration()} — the case that
 * made this class necessary.  Ingestion attaches a {@link CollaboratorCdd} to its <em>source</em>
 * only, so a class that is merely the target of a relation has an empty {@code collaboratorList} of
 * its own; asking the class "do you have content?" would drop a box that is unmistakably in the
 * diagram, because it is one end of a drawn edge.</p>
 */
class ModelContentTest {

    @Test
    void materialCollaborations_requiresAnApprovedCollaborationBetweenApprovedClasses() {
        // given — one material collaboration, one never approved, one with an unapproved end
        final DomainModel model = new DomainModel();
        final ClassCdd store = approved(empty("Store"));
        final ClassCdd till = approved(empty("Till"));
        final ClassCdd draft = empty("Draft");
        final CollaboratorCdd material = collaboration(model, "has", store, till);
        approved(material);
        collaboration(model, "maybe", store, till);
        approved(collaboration(model, "drafts", store, draft));

        // when / then
        assertThat(ModelContent.materialCollaborations(model)).containsExactly(material);
    }

    @Test
    void includedClasses_requiresTheClassItselfToBeApproved() {
        // given — approved content, but the class itself was never reviewed
        final DomainModel model = new DomainModel();
        final ClassCdd store = empty("Store");
        store.responsibilityList.add(approved(new ResponsibilityCdd("browse", List.of(), "void")));
        model.classList.add(store);

        // when / then
        assertThat(ModelContent.includedClasses(model)).isEmpty();
    }

    @Test
    void includedClasses_keepsAClassThatIsOnlyTheTargetOfAMaterialCollaboration() {
        // given — "Till" has no members and owns no collaboration (ingestion attached the relation to
        //         its source), yet it is one end of a drawn edge
        final DomainModel model = new DomainModel();
        final ClassCdd store = approved(empty("Store"));
        store.responsibilityList.add(approved(new ResponsibilityCdd("browse", List.of(), "void")));
        final ClassCdd till = approved(empty("Till"));
        approved(collaboration(model, "has", store, till));
        model.classList.add(store);
        model.classList.add(till);

        // when / then
        assertThat(ModelContent.includedClasses(model)).containsExactly(store, till);
    }

    @Test
    void emptyClasses_isTheComplementOfIncludedClasses() {
        // given — one class with content, one only connected, one with neither, one not approved
        final DomainModel model = new DomainModel();
        final ClassCdd store = approved(empty("Store"));
        store.responsibilityList.add(approved(new ResponsibilityCdd("browse", List.of(), "void")));
        final ClassCdd till = approved(empty("Till"));
        final ClassCdd ghost = approved(empty("Ghost"));
        final ClassCdd draft = empty("Draft");
        approved(collaboration(model, "has", store, till));
        model.classList.add(store);
        model.classList.add(till);
        model.classList.add(ghost);
        model.classList.add(draft);

        // when / then — the unapproved class is in neither list
        assertThat(ModelContent.includedClasses(model)).containsExactly(store, till);
        assertThat(ModelContent.emptyClasses(model)).containsExactly(ghost);
    }

    @Test
    void aNullModelOrListYieldsNothingInsteadOfThrowing() {
        assertThat(ModelContent.includedClasses(null)).isEmpty();
        assertThat(ModelContent.emptyClasses(null)).isEmpty();
        assertThat(ModelContent.materialCollaborations(null)).isEmpty();

        final DomainModel bare = new DomainModel();
        bare.classList = null;
        bare.collaboratorList = null;
        assertThat(ModelContent.includedClasses(bare)).isEmpty();
        assertThat(ModelContent.emptyClasses(bare)).isEmpty();
        assertThat(ModelContent.materialCollaborations(bare)).isEmpty();
    }

    @Test
    void aNullEntryInTheListsIsIgnored() {
        // given — a half-built list
        final DomainModel model = new DomainModel();
        model.classList.add(null);
        model.collaboratorList.add(null);

        // when / then — no list reports the null, and none throws
        assertThat(ModelContent.includedClasses(model)).isEmpty();
        assertThat(ModelContent.emptyClasses(model)).isEmpty();
        assertThat(ModelContent.materialCollaborations(model)).isEmpty();
    }

    // --- helpers --------------------------------------------------------------

    /** A class with no members at all. */
    private static ClassCdd empty(final String name) {
        return new ClassCdd(name, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /** Registers a collaboration the way ingestion does — on its source, and on the model. */
    private static CollaboratorCdd collaboration(final DomainModel model, final String name,
                                                 final ClassCdd source, final ClassCdd target) {
        final CollaboratorCdd collaboration = new CollaboratorCdd(name, source, target);
        source.addCollaborator(collaboration);
        model.collaboratorList.add(collaboration);
        return collaboration;
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
