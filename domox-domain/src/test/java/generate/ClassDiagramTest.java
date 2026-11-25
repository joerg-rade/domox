package generate;

import domox.dom.AbstractEntity;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.AssociationType;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.model.Review;
import domox.dom.crc.model.ReviewStatus;
import domox.dom.crc.model.SubDomain;
import domox.dom.crc.model.SubDomainClassification;
import domox.dom.rdf.UmlDiagramLayout;
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
        assertThat(plantUml).contains("Store -> Customer: has");
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
        assertThat(plantUml).contains("class \"Till\"").contains("Store -> Till: uses");
    }

    /**
     * The export never butts an arrow against a class name — PlantUML would read the name's first
     * letter as a direction specifier and draw a phantom class instead.
     *
     * <p>This is the defect the two diagram tabs disagreed about: {@code Address==Recipient} lost the
     * {@code R} to PlantUML's <em>right</em> direction and rendered {@code ecipient} — a class the D3
     * view does not have, with the association aimed at it.  {@link CollaboratorCdd#toPlantUmlString()}
     * pads the arrow for all seven symbols (its own unit test covers that); this one pins the text the
     * whole diagram is assembled from, which is what PlantUML is handed.</p>
     */
    @Test
    void build_neverButtsAnArrowAgainstAClassName() {
        // given — a headless SYNONYM aimed at a name that begins with PlantUML's "right" letter
        final ClassCdd address = withApprovedResponsibility("Address");
        final ClassCdd recipient = withApprovedResponsibility("Recipient");
        final DomainModel model = new DomainModel();
        final CollaboratorCdd synonym = approved(collaboration(model, "sameAs", address, recipient));
        synonym.setType(AssociationType.SYNONYM);
        model.classList.add(approved(address));
        model.classList.add(approved(recipient));

        // when
        final String plantUml = classUnderTest.build(model);

        // then — both classes are drawn, and the arrow stands alone between two spaces
        assertThat(plantUml).contains("class \"Address\"").contains("class \"Recipient\"");
        assertThat(plantUml).contains("Address == Recipient: sameAs");
        assertThat(plantUml).doesNotContain("==Recipient");
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
        assertThat(plantUml).contains("Store -> Till: has");
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

    // --- the D3 graph ---------------------------------------------------------

    /**
     * The D3 picture reads an {@code implements} the way it reads an {@code extends}: both are is-a edges,
     * so the implementing class is seeded <em>below</em> the interface it implements and carries the
     * arrowhead.  The PDF keeps PlantUML's dashed realization — it renders from {@code toPlantUmlString()},
     * not from this mapping.
     */
    @Test
    void graph_readsAnImplementationAsAnIsAEdge() {
        // given — an interface, and a class that implements it
        final ClassCdd identifiable = withApprovedResponsibility("Identifiable");
        final ClassCdd store = withApprovedResponsibility("Store");
        final DomainModel model = new DomainModel();
        final CollaboratorCdd implementsIt = approved(collaboration(model, "implements", store, identifiable));
        implementsIt.setType(AssociationType.IMPLEMENTATION);
        model.classList.add(approved(store));
        model.classList.add(approved(identifiable));

        // when
        final ClassDiagram.Graph graph = classUnderTest.graph(model);

        // then — the edge reads from the interface, as a generalization
        assertThat(graph.getEdges()).containsExactly(
                new UmlDiagramLayout.Edge("Identifiable", "Store", UmlDiagramLayout.GENERALIZATION, "implements"));
    }

    /**
     * A class box states the row it stands for — its id, and its <b>Causeway</b> object type — so the
     * picture can make the class name a click-through to that class's own page.  The object type is read
     * from {@link Candidate#getObjectType()} (the {@code @Named} value the framework knows), never from the
     * Java class name: a click-through URL that names the wrong type fails on the server, and it fails
     * silently — the box simply does not open.
     */
    @Test
    void graph_tiesEachClassBoxToTheRowItStandsFor() {
        // given — an approved class with an id, as the database would give it
        final ClassCdd store = withApprovedResponsibility("Store");
        setId(store, 42L);
        final DomainModel model = new DomainModel();
        model.classList.add(approved(store));

        // when
        final ClassDiagram.Graph graph = classUnderTest.graph(model);

        // then
        final UmlDiagramLayout.Node box = nodeNamed(graph, "Store");
        assertThat(box.candidateId()).isEqualTo(42L);
        assertThat(box.objectType()).isEqualTo("domox.ClassCdd");
    }

    // --- the sub-domain overview's D3 graph -----------------------------------

    /**
     * The overview's graph is marked as <b>packages</b>, so the client draws each node as a UML package
     * symbol rather than as a class box — the one thing that tells this picture apart from the other two,
     * which share every other field with it.  It is carried on the graph, not inferred from the title.
     */
    @Test
    void graphOverview_marksTheGraphAsPackages() {
        // given — two sub-domains with one relation between them
        final ClassCdd store = withApprovedResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE));
        final ClassCdd till = withApprovedResponsibility("Till",
                subDomain("Checkout", SubDomainClassification.SUPPORTING));
        final DomainModel model = new DomainModel();
        approved(collaboration(model, "has", store, till));
        model.classList.add(approved(store));
        model.classList.add(approved(till));

        // when
        final ClassDiagram.Graph graph = classUnderTest.graphOverview(model);

        // then
        assertThat(graph.getPackages()).isTrue();
        assertThat(graph.getTitle()).endsWith("sub-domain overview");
        assertThat(graph.getNodes()).extracting(UmlDiagramLayout.Node::id)
                .containsExactly("Inventory (CORE)", "Checkout (SUPPORTING)");
    }

    /**
     * A package lists the classes of its sub-domain that take part in a relation <em>leaving</em> it — the
     * classes the connectors between packages actually belong to, so a reader can see what an edge stands
     * for without opening the sub-domain's own diagram.  A class that relates only to its own neighbours
     * says nothing about how the packages fit together, so it is left out.
     */
    @Test
    void graphOverview_listsOnlyTheOutwardClassesOfEachPackage() {
        // given — Inventory: Store and Shelf, where only Store reaches out; Checkout: Till
        final ClassCdd store = withApprovedResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE));
        final ClassCdd shelf = withApprovedResponsibility("Shelf",
                subDomain("Inventory", SubDomainClassification.CORE));
        final ClassCdd till = withApprovedResponsibility("Till",
                subDomain("Checkout", SubDomainClassification.SUPPORTING));
        final DomainModel model = new DomainModel();
        approved(collaboration(model, "has", store, shelf));      // inside Inventory: not package content
        approved(collaboration(model, "has", store, till));       // leaves Inventory
        approved(collaboration(model, "reportsTo", till, store)); // leaves Checkout
        model.classList.add(approved(store));
        model.classList.add(approved(shelf));
        model.classList.add(approved(till));

        // when
        final ClassDiagram.Graph graph = classUnderTest.graphOverview(model);

        // then — each package carries its outward classes in the attributes slot, sorted; no methods, and
        // the aggregated edge is unchanged by any of it
        final UmlDiagramLayout.Node inventory = nodeNamed(graph, "Inventory (CORE)");
        assertThat(inventory.attributes()).as("Shelf relates only inside its own sub-domain")
                .containsExactly("Store");
        assertThat(inventory.methods()).isEmpty();
        assertThat(nodeNamed(graph, "Checkout (SUPPORTING)").attributes()).containsExactly("Till");
        assertThat(graph.getEdges()).containsExactly(new UmlDiagramLayout.Edge(
                "Inventory (CORE)", "Checkout (SUPPORTING)", UmlDiagramLayout.ASSOCIATION, "2"));
    }

    /**
     * The overview's packages are <em>sub-domains</em>, not rows: they carry no click-through target, so
     * the client draws them inert.  A package that linked somewhere would have to pick an arbitrary class
     * of the sub-domain to link to, which is not what the reader clicked.
     */
    @Test
    void graphOverview_leavesItsPackagesWithoutAClickThroughTarget() {
        // given — two sub-domains with one relation between them
        final ClassCdd store = withApprovedResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE));
        final ClassCdd till = withApprovedResponsibility("Till",
                subDomain("Checkout", SubDomainClassification.SUPPORTING));
        final DomainModel model = new DomainModel();
        approved(collaboration(model, "has", store, till));
        model.classList.add(approved(store));
        model.classList.add(approved(till));

        // when
        final ClassDiagram.Graph graph = classUnderTest.graphOverview(model);

        // then
        assertThat(graph.getNodes()).isNotEmpty().allSatisfy(node -> {
            assertThat(node.candidateId()).isNull();
            assertThat(node.objectType()).isNull();
        });
    }

    /**
     * Every line a package lists states the class row it stands for — the pair the picture links on — so a
     * click on a name in the package body opens that class's own page.  The <em>package</em> still carries
     * none: it is a sub-domain, and a click on it could mean no single row.  The object type is the
     * <b>Causeway</b> one, read back from {@code @Named}, or the URL would name a type the framework does
     * not know.
     */
    @Test
    void graphOverview_tiesEachContentLineToTheClassItStandsFor() {
        // given — Inventory reaches out to Checkout through Store, with ids as the database would give them
        final ClassCdd store = withApprovedResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE));
        final ClassCdd till = withApprovedResponsibility("Till",
                subDomain("Checkout", SubDomainClassification.SUPPORTING));
        setId(store, 42L);
        setId(till, 43L);
        final DomainModel model = new DomainModel();
        approved(collaboration(model, "has", store, till));
        model.classList.add(approved(store));
        model.classList.add(approved(till));

        // when
        final ClassDiagram.Graph graph = classUnderTest.graphOverview(model);

        // then
        final UmlDiagramLayout.Node inventory = nodeNamed(graph, "Inventory (CORE)");
        assertThat(inventory.contents()).hasSize(1);
        assertThat(inventory.contents().get(0).name()).isEqualTo("Store");
        assertThat(inventory.contents().get(0).objectType()).isEqualTo("domox.ClassCdd");
        assertThat(inventory.contents().get(0).candidateId()).isEqualTo(42L);
        final UmlDiagramLayout.Node checkout = nodeNamed(graph, "Checkout (SUPPORTING)");
        assertThat(checkout.contents()).hasSize(1);
        assertThat(checkout.contents().get(0).name()).isEqualTo("Till");
        assertThat(checkout.contents().get(0).candidateId()).isEqualTo(43L);
        assertThat(inventory.candidateId()).as("a package is a sub-domain, not a row").isNull();
        assertThat(inventory.objectType()).isNull();
    }

    // --- helpers --------------------------------------------------------------

    /** Gives a hand-built entity the id the database would have given it. */
    private static void setId(final ClassCdd entity, final long id) {
        try {
            final java.lang.reflect.Field field = AbstractEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (final Exception e) {
            throw new RuntimeException("failed to set id on " + entity, e);
        }
    }

    /** The graph's node with the given id — the overview labels each package with its sub-domain. */
    private static UmlDiagramLayout.Node nodeNamed(final ClassDiagram.Graph graph, final String id) {
        return graph.getNodes().stream().filter(node -> node.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no node " + id + " in " + graph.getTitle()));
    }

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
