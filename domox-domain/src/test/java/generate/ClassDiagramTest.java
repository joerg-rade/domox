package generate;

import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.model.Review;
import domox.dom.crc.model.ReviewStatus;
import domox.dom.crc.model.SubDomain;
import domox.dom.crc.model.SubDomainClassification;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The review-aware export: what {@link ClassDiagram} draws, and what it refuses to draw.
 *
 * <p>Three rules are asserted here, because each one used to be missing from the export:</p>
 * <ul>
 *   <li>a class the review did not approve is not drawn;</li>
 *   <li>an approved class with no approved member is not drawn either — the "no empty boxes" rule
 *       ({@code ClassCdd#hasModelledContent()});</li>
 *   <li>a member, or an edge whose other end is unapproved, is not drawn.</li>
 * </ul>
 *
 * <p>The entities are built by hand (no database, no Causeway container): the predicates the exporter
 * relies on are plain state — a review with status {@code APPROVED}, a sub-domain reference — so a
 * unit test can establish them directly and assert on the rendered PlantUML text.</p>
 */
class ClassDiagramTest {

    private final ClassDiagram classUnderTest = new ClassDiagram();

    // --- the whole-model diagram ---------------------------------------------

    @Test
    void build_omitsClassesTheReviewDidNotApprove() {
        // given — one approved class and one with no review at all
        final DomainModel model = new DomainModel();
        model.classList.add(approved(withApprovedResponsibility("Store")));
        model.classList.add(withApprovedResponsibility("Draft"));

        // when
        final String plantUml = classUnderTest.build(model);

        // then
        assertThat(plantUml).contains("class \"Store\"");
        assertThat(plantUml).doesNotContain("Draft");
    }

    @Test
    void build_omitsApprovedClassesWithNoApprovedContent() {
        // given — approved, but nothing approved inside it: an empty rectangle, so not drawn
        final DomainModel model = new DomainModel();
        model.classList.add(approved(empty("Ghost")));

        // when
        final String plantUml = classUnderTest.build(model);

        // then — a well-formed but empty diagram
        assertThat(plantUml).isEqualTo("@startuml\n@enduml");
    }

    @Test
    void build_omitsMembersTheReviewDidNotApprove() {
        // given — one approved and one unapproved member of each kind
        final ClassCdd store = empty("Store");
        store.attributeList.add(approved(new AttributeCdd("openingHours", "String")));
        store.attributeList.add(new AttributeCdd("draftAttribute", "String"));
        store.responsibilityList.add(approved(new ResponsibilityCdd("browse", List.of(), "void")));
        store.responsibilityList.add(new ResponsibilityCdd("draftOperation", List.of(), "void"));
        final DomainModel model = new DomainModel();
        model.classList.add(approved(store));

        // when
        final String plantUml = classUnderTest.build(model);

        // then
        assertThat(plantUml).contains("openingHours: String").contains("browse()");
        assertThat(plantUml).doesNotContain("draftAttribute").doesNotContain("draftOperation");
    }

    @Test
    void build_drawsAnEdgeOnlyBetweenTwoIncludedClasses() {
        // given — a material edge to Customer, and one to a class that was rejected
        final ClassCdd store = withApprovedResponsibility("Store");
        final ClassCdd customer = withApprovedResponsibility("Customer");
        final ClassCdd rejected = withApprovedResponsibility("Rejected");
        final DomainModel model = new DomainModel();
        approved(collaboration(model, "has", store, customer));
        approved(collaboration(model, "ignores", store, rejected));
        model.classList.add(approved(store));
        model.classList.add(approved(customer));
        model.classList.add(rejected);

        // when
        final String plantUml = classUnderTest.build(model);

        // then
        assertThat(plantUml).contains("Store->Customer: has");
        assertThat(plantUml).doesNotContain("Rejected");
    }

    @Test
    void build_omitsAnEdgeWhoseCollaboratorWasNotApproved() {
        // given — both ends approved, but the association itself rejected
        final ClassCdd store = withApprovedResponsibility("Store");
        final ClassCdd customer = withApprovedResponsibility("Customer");
        final DomainModel model = new DomainModel();
        collaboration(model, "has", store, customer);
        model.classList.add(approved(store));
        model.classList.add(approved(customer));

        // when
        final String plantUml = classUnderTest.build(model);

        // then — both boxes, no edge
        assertThat(plantUml).contains("class \"Store\"").contains("class \"Customer\"");
        assertThat(plantUml).doesNotContain("->");
    }

    @Test
    void build_includesAClassWhoseOnlyContentIsARelation() {
        // given — an empty body, but a material edge: the class is part of the model
        final ClassCdd store = withApprovedResponsibility("Store");
        final ClassCdd till = empty("Till");
        final DomainModel model = new DomainModel();
        approved(collaboration(model, "uses", store, till));
        model.classList.add(approved(store));
        model.classList.add(approved(till));

        // when
        final String plantUml = classUnderTest.build(model);

        // then
        assertThat(plantUml).contains("class \"Till\"").contains("Store->Till: uses");
    }

    // --- one diagram per sub-domain -------------------------------------------

    @Test
    void buildForSubDomain_drawsOnlyThatSubDomainsClasses() {
        // given — one class in each of two sub-domains
        final DomainModel model = new DomainModel();
        model.classList.add(approved(withApprovedResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE))));
        model.classList.add(approved(withApprovedResponsibility("Till",
                subDomain("Checkout", SubDomainClassification.SUPPORTING))));

        // when
        final String plantUml = classUnderTest.buildForSubDomain(model, "Inventory");

        // then
        assertThat(plantUml).contains("class \"Store\"");
        assertThat(plantUml).doesNotContain("Till");
    }

    @Test
    void buildForSubDomain_matchesTheNameCaseInsensitivelyAndTrimmed() {
        // given
        final DomainModel model = new DomainModel();
        model.classList.add(approved(withApprovedResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE))));

        // when / then — the name as a reviewer would type it, not as it is stored
        assertThat(classUnderTest.buildForSubDomain(model, "  inventory "))
                .contains("class \"Store\"");
    }

    @Test
    void buildForSubDomain_dropsEdgesThatLeaveTheSubDomain() {
        // given — one edge inside Inventory and one that crosses into Checkout
        final ClassCdd store = withApprovedResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE));
        final ClassCdd till = withApprovedResponsibility("Till",
                subDomain("Inventory", SubDomainClassification.CORE));
        final ClassCdd customer = withApprovedResponsibility("Customer",
                subDomain("Checkout", SubDomainClassification.SUPPORTING));
        final DomainModel model = new DomainModel();
        approved(collaboration(model, "has", store, till));
        approved(collaboration(model, "serves", store, customer));
        model.classList.add(approved(store));
        model.classList.add(approved(till));
        model.classList.add(approved(customer));

        // when
        final String plantUml = classUnderTest.buildForSubDomain(model, "Inventory");

        // then — the inside edge is drawn; the crossing one belongs to the overview
        assertThat(plantUml).contains("Store->Till: has");
        assertThat(plantUml).doesNotContain("Customer").doesNotContain("serves");
    }

    @Test
    void buildForSubDomain_withABlankName_drawsNothing() {
        // given — a blank name must select nothing, not everything
        final DomainModel model = new DomainModel();
        model.classList.add(approved(withApprovedResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE))));

        // when / then
        assertThat(classUnderTest.buildForSubDomain(model, "  ")).isEqualTo("@startuml\n@enduml");
        assertThat(classUnderTest.buildForSubDomain(model, null)).isEqualTo("@startuml\n@enduml");
    }

    @Test
    void subDomainNames_listsTheSubDomainsOfIncludedClasses_sortedAndDistinct() {
        // given — two classes in Checkout, one in Inventory, one approved-but-empty (no sub-domain)
        //         and one whose sub-domain belongs to a class that was not approved
        final DomainModel model = new DomainModel();
        model.classList.add(approved(withApprovedResponsibility("Till",
                subDomain("Checkout", SubDomainClassification.SUPPORTING))));
        model.classList.add(approved(withApprovedResponsibility("Receipt",
                subDomain("Checkout", SubDomainClassification.SUPPORTING))));
        model.classList.add(approved(withApprovedResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE))));
        model.classList.add(approved(empty("Ghost")));
        model.classList.add(withApprovedResponsibility("Draft",
                subDomain("Draft Area", SubDomainClassification.UNCLASSIFIED)));

        // when / then — only the sub-domains that actually produce a diagram, in name order
        assertThat(classUnderTest.subDomainNames(model)).containsExactly("Checkout", "Inventory");
    }

    // --- the sub-domain overview ----------------------------------------------

    @Test
    void buildSubDomainOverview_aggregatesCrossSubDomainRelations() {
        // given — two collaborations Inventory -> Checkout and one the other way
        final ClassCdd store = withApprovedResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE));
        final ClassCdd till = withApprovedResponsibility("Till",
                subDomain("Checkout", SubDomainClassification.SUPPORTING));
        final DomainModel model = new DomainModel();
        approved(collaboration(model, "has", store, till));
        approved(collaboration(model, "serves", store, till));
        approved(collaboration(model, "reportsTo", till, store));
        model.classList.add(approved(store));
        model.classList.add(approved(till));

        // when
        final String plantUml = classUnderTest.buildSubDomainOverview(model);

        // then — one node per sub-domain, and one undirected edge carrying the count
        assertThat(plantUml).contains("class \"Inventory (CORE)\" as SD0");
        assertThat(plantUml).contains("class \"Checkout (SUPPORTING)\" as SD1");
        assertThat(plantUml).contains("SD0 --> SD1 : 3");
        assertThat(plantUml).doesNotContain("SD1 --> SD0");
    }

    @Test
    void buildSubDomainOverview_omitsRelationsInsideOneSubDomain() {
        // given — an edge between two classes of the same sub-domain
        final ClassCdd store = withApprovedResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE));
        final ClassCdd shelf = withApprovedResponsibility("Shelf",
                subDomain("Inventory", SubDomainClassification.CORE));
        final DomainModel model = new DomainModel();
        approved(collaboration(model, "has", store, shelf));
        model.classList.add(approved(store));
        model.classList.add(approved(shelf));

        // when
        final String plantUml = classUnderTest.buildSubDomainOverview(model);

        // then — one node, no edge: that relation belongs to the Inventory diagram
        assertThat(plantUml).contains("class \"Inventory (CORE)\" as SD0");
        assertThat(plantUml).doesNotContain("-->");
    }

    @Test
    void buildSubDomainOverview_showsClassesWithoutASubDomainAsUnassigned() {
        // given — one partitioned class and one the sub-domain pass has not reached
        final ClassCdd store = withApprovedResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE));
        final ClassCdd pet = withApprovedResponsibility("Pet");
        final DomainModel model = new DomainModel();
        approved(collaboration(model, "caresFor", store, pet));
        model.classList.add(approved(store));
        model.classList.add(approved(pet));

        // when
        final String plantUml = classUnderTest.buildSubDomainOverview(model);

        // then — the gap is visible rather than silent
        assertThat(plantUml).contains("class \"Unassigned\" as SD1");
        assertThat(plantUml).contains("SD0 --> SD1 : 1");
    }

    // --- helpers --------------------------------------------------------------

    /** A class with no members at all. */
    private static ClassCdd empty(final String name) {
        return new ClassCdd(name, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /** A class whose single responsibility is approved — so the class has modelled content. */
    private static ClassCdd withApprovedResponsibility(final String name) {
        return withApprovedResponsibility(name, null);
    }

    /** As above, assigned to {@code subDomain} (which may be {@code null}). */
    private static ClassCdd withApprovedResponsibility(final String name, final SubDomain subDomain) {
        final ClassCdd clazz = empty(name);
        clazz.setSubDomain(subDomain);
        clazz.responsibilityList.add(approved(new ResponsibilityCdd("run", List.of(), "void")));
        return clazz;
    }

    /** A sub-domain reference; {@code classification} may be {@code null} on a detached instance. */
    private static SubDomain subDomain(final String name, final SubDomainClassification classification) {
        final SubDomain subDomain = new SubDomain();
        subDomain.setName(name);
        subDomain.setClassification(classification);
        return subDomain;
    }

    /**
     * Registers a collaboration the way ingestion does — on its <em>source</em> and on the
     * <em>model</em> — so the test model mirrors the real one ({@code CollaboratorCandidates}).  The
     * model's list is what {@code ModelContent} reads: it is the only place both ends are visible.
     */
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
