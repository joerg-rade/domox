package domox.dom.crc.service;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.ClassType;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.model.ResponsibilityParameter;
import domox.dom.crc.model.Review;
import domox.dom.crc.model.ReviewRationale;
import domox.dom.crc.model.ReviewStatus;
import domox.dom.crc.model.SubDomain;
import domox.dom.crc.model.SubDomainClassification;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.value.Blob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainModelsTest {

    private DomainModels classUnderTest;

    @Mock
    RepositoryService mockRepositoryService;

    @Mock
    Reviews mockReviews;

    @BeforeEach
    public void setUp() {
        classUnderTest = new DomainModels(mockRepositoryService, mockReviews);
    }

    @Test
    void umlClassDiagram_drawsClassesAndAssociations() {
        // given — an approved class with approved members, and a material association to a second class
        final List<AttributeCdd> attributeList = new ArrayList<>();
        attributeList.add(approved(new AttributeCdd("property1", "int")));
        attributeList.add(approved(new AttributeCdd("property2", "Integer")));

        final List<ResponsibilityParameter> inputTypeList = new ArrayList<>();
        inputTypeList.add(new ResponsibilityParameter("venirIci", "String"));
        inputTypeList.add(new ResponsibilityParameter("toutSuit", "Boolean"));
        final String outputType = Integer.class.getSimpleName();
        final List<ResponsibilityCdd> responsibilityList = new ArrayList<>();
        responsibilityList.add(approved(new ResponsibilityCdd("ordre", inputTypeList, outputType)));

        final ClassCdd clazz1 = new ClassCdd("Alice", attributeList, responsibilityList, new ArrayList<>());
        approved(clazz1);
        final ClassCdd clazz2 = new ClassCdd("Bob", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        clazz2.setClassType(ClassType.ROLE);
        approved(clazz2);

        final DomainModel domainModel = classUnderTest.create();
        final CollaboratorCdd association = approved(collaboration(domainModel, "command", clazz1, clazz2));
        association.setTargetCardinality("2");
        domainModel.classList.add(clazz1);
        domainModel.classList.add(clazz2);

        // when
        final UmlClassDiagram result =
                classUnderTest.umlClassDiagram(domainModel, UmlClassDiagram.KIND_MODEL, null);

        // then — the payload the D3 panel draws: one box per class, the members in their compartments,
        // and the association as a link
        final String payload = result.getD3Graph();
        assertThat(domainModel.classList).hasSize(2);
        assertThat(payload).contains("\"force\":true");
        assertThat(payload).contains("\"id\":\"Alice\"").contains("\"id\":\"Bob\"");
        assertThat(payload).as("the approved attributes are the box's attribute lines")
                .contains("\"attributes\":[\"property1: int\",\"property2: Integer\"]");
        assertThat(payload).as("the approved responsibility is the box's method line")
                .contains("\"methods\":[\"ordre(venirIci: String, toutSuit: Boolean): Integer\"]");
        assertThat(payload).as("the association is drawn").contains("\"kind\":\"association\"");
        assertThat(result.namedD3Graph()).as("and the label says which diagram this is").isEqualTo("Whole model");
    }

    // --- one diagram per sub-domain, and the overview -------------------------

    @Test
    void umlClassDiagram_drawsOnlyThatSubDomain() {
        // given — one class in each of two sub-domains
        final DomainModel model = new DomainModel();
        model.classList.add(approved(withResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE))));
        model.classList.add(approved(withResponsibility("Till",
                subDomain("Checkout", SubDomainClassification.SUPPORTING))));

        // when
        final UmlClassDiagram result =
                classUnderTest.umlClassDiagram(model, UmlClassDiagram.KIND_SUB_DOMAIN, "Inventory");

        // then — the payload draws one box, and the label names the sub-domain the reader is looking at
        assertThat(result.getD3Graph()).contains("\"id\":\"Store\"").doesNotContain("\"id\":\"Till\"");
        assertThat(result.namedD3Graph()).isEqualTo("Sub-domain: Inventory");
    }

    @Test
    void umlClassDiagram_drawsSubDomainOverviewNodesAndCrossEdges() {
        // given — a relation that crosses from Inventory into Checkout
        final DomainModel model = new DomainModel();
        final ClassCdd store = approved(withResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE)));
        final ClassCdd till = approved(withResponsibility("Till",
                subDomain("Checkout", SubDomainClassification.SUPPORTING)));
        approved(collaboration(model, "has", store, till));
        model.classList.add(store);
        model.classList.add(till);

        // when
        final UmlClassDiagram result =
                classUnderTest.umlClassDiagram(model, UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW, null);

        // then — one node per sub-domain (labelled with its classification), one edge, labelled with the
        // number of collaborations it stands for
        final String payload = result.getD3Graph();
        assertThat(payload).contains("\"id\":\"Inventory (CORE)\"").contains("\"id\":\"Checkout (SUPPORTING)\"");
        assertThat(payload).contains("\"kind\":\"association\"").contains("\"label\":\"1\"");
        assertThat(result.namedD3Graph()).isEqualTo("Sub-domain overview");
    }

    // --- every diagram at once, one tab per sub-domain -------------------------

    @Test
    void showUmlDiagrams_offersTheOverviewOneTabPerSubDomainAndTheWholeModel() {
        // given — one class in each of two sub-domains, named out of order
        final DomainModel model = new DomainModel();
        model.classList.add(approved(withResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE))));
        model.classList.add(approved(withResponsibility("Till",
                subDomain("Checkout", SubDomainClassification.SUPPORTING))));

        // when
        final UmlDiagramSet result = classUnderTest.showUmlDiagrams(model);

        // then — the reader's order: the map first, then the sub-domains in name order, the whole model last
        assertThat(result.tabNames()).containsExactly(
                UmlDiagramSet.TAB_SUB_DOMAIN_OVERVIEW, "Checkout", "Inventory", UmlDiagramSet.TAB_WHOLE_MODEL);
        assertThat(result.title()).contains("UML class diagrams");
    }

    @Test
    void showUmlDiagrams_givesEachTabItsOwnDiagram() {
        // given — one class in each of two sub-domains
        final DomainModel model = new DomainModel();
        model.classList.add(approved(withResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE))));
        model.classList.add(approved(withResponsibility("Till",
                subDomain("Checkout", SubDomainClassification.SUPPORTING))));
        final UmlDiagramSet result = classUnderTest.showUmlDiagrams(model);

        // when / then — a sub-domain tab draws only its own class, and the overview draws the sub-domains
        assertThat(result.payloadOf("Inventory")).contains("\"id\":\"Store\"").doesNotContain("\"id\":\"Till\"");
        assertThat(result.payloadOf(UmlDiagramSet.TAB_SUB_DOMAIN_OVERVIEW))
                .contains("\"id\":\"Inventory (CORE)\"");

        // the whole model is drawn on its own tab AND on the property that is the fallback for the page
        final String wholeModel = result.payloadOf(UmlDiagramSet.TAB_WHOLE_MODEL);
        assertThat(wholeModel).contains("\"id\":\"Store\"").contains("\"id\":\"Till\"");
        assertThat(result.getWholeModelGraph()).as("the fallback property is the same payload as the last tab")
                .isEqualTo(wholeModel);
    }

    @Test
    void showUmlDiagrams_assemblesEachDiagramOnce() {
        // given — the page asks for a tab's payload from inside the tab, and a tab can be re-rendered
        final DomainModel model = new DomainModel();
        model.classList.add(approved(withResponsibility("Store", null)));
        final UmlDiagramSet result = classUnderTest.showUmlDiagrams(model);

        // when / then — the second request is the cached payload, not a second assembly
        assertThat(result.payloadOf(UmlDiagramSet.TAB_WHOLE_MODEL))
                .isSameAs(result.payloadOf(UmlDiagramSet.TAB_WHOLE_MODEL));
    }

    @Test
    void aDetachedSet_offersTheTwoTabsItCanDraw() {
        // given — a view model rebuilt from a memento that carries no model
        final UmlDiagramSet result = UmlDiagramSet.of(null, classUnderTest);

        // when / then — no sub-domain to name, and an empty but well-formed payload rather than a failure
        assertThat(result.tabNames())
                .containsExactly(UmlDiagramSet.TAB_SUB_DOMAIN_OVERVIEW, UmlDiagramSet.TAB_WHOLE_MODEL);
        assertThat(result.payloadOf("Inventory")).isEqualTo(EMPTY_GRAPH_PAYLOAD);
        assertThat(result.title()).contains("Domain model");
    }

    /** The empty payload both view models fall back to — well formed, so the D3 panel renders an empty canvas. */
    private static final String EMPTY_GRAPH_PAYLOAD = "{\"layout\":\"uml\",\"nodes\":[],\"links\":[],\"legend\":[]}";

    // --- the PlantUML export is a download, not a page member ------------------

    @Test
    void downloadPlantUml_servesTheAssembledSourceAsAFile() {
        // given — one approved class, so there is something to export
        final DomainModel model = new DomainModel();
        model.classList.add(approved(withResponsibility("Store", null)));

        // when — the whole model, which a blank kind means (so the common case needs no parameter)
        final Blob puml = classUnderTest.downloadPlantUml(model, null, null);

        // then — a .puml file carrying the assembled, review-aware source
        assertThat(puml.name()).isEqualTo("domain-model.puml");
        assertThat(puml.mimeType().toString()).contains("text/plain");
        final String source = new String(puml.bytes(), StandardCharsets.UTF_8);
        assertThat(source).startsWith("@startuml").endsWith("@enduml").contains("class \"Store\"");
    }

    @Test
    void downloadPlantUml_servesOneSubDomainWhenAskedForIt() {
        // given — one class in each of two sub-domains
        final DomainModel model = new DomainModel();
        model.classList.add(approved(withResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE))));
        model.classList.add(approved(withResponsibility("Till",
                subDomain("Checkout", SubDomainClassification.SUPPORTING))));

        // when
        final Blob puml = classUnderTest.downloadPlantUml(model, UmlClassDiagram.KIND_SUB_DOMAIN, "Inventory");

        // then — the file is named for the sub-domain, and holds only that sub-domain's classes
        assertThat(puml.name()).isEqualTo("domain-model-Inventory.puml");
        final String source = new String(puml.bytes(), StandardCharsets.UTF_8);
        assertThat(source).contains("class \"Store\"").doesNotContain("Till");
    }

    @Test
    void choices1DownloadPlantUml_listsTheThreeDiagramKinds() {
        // when / then — the kinds the diagram set draws, so a caller can ask for any of them
        assertThat(classUnderTest.choices1DownloadPlantUml())
                .containsExactly(UmlClassDiagram.KIND_MODEL, UmlClassDiagram.KIND_SUB_DOMAIN,
                        UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW);
    }

    // --- the "no empty boxes" sweep ------------------------------------------

    @Test
    void rejectEmptyClasses_rejectsApprovedClassesWithNoApprovedContent() {
        // given — an approved class with content beside an approved class with nothing
        final DomainModel model = new DomainModel();
        final ClassCdd store = approved(withResponsibility("Store", null));
        final ClassCdd ghost = approved(empty("Ghost"));
        model.classList.add(store);
        model.classList.add(ghost);

        // when
        final List<ClassCdd> rejected = classUnderTest.rejectEmptyClasses(model, "no empty boxes");

        // then — rejected as INSUFFICIENT_INFORMATION, and the class with content is left alone
        assertThat(rejected).containsExactly(ghost);
        verify(mockReviews).rejectAsAgent(ghost, ReviewRationale.INSUFFICIENT_INFORMATION, "no empty boxes");
    }

    @Test
    void rejectEmptyClasses_leavesAClassThatIsOnlyConnected() {
        // given — an empty-bodied class that is one end of a material relation
        final DomainModel model = new DomainModel();
        final ClassCdd store = approved(withResponsibility("Store", null));
        final ClassCdd till = approved(empty("Till"));
        approved(collaboration(model, "has", store, till));
        model.classList.add(store);
        model.classList.add(till);

        // when / then — a relation is content: nothing is rejected
        assertThat(classUnderTest.rejectEmptyClasses(model, "no empty boxes")).isEmpty();
    }

    @Test
    void rejectEmptyClassesInAllModels_walksEveryModel() {
        // given — one model, holding one empty class
        final DomainModel model = new DomainModel();
        final ClassCdd ghost = approved(empty("Ghost"));
        model.classList.add(ghost);
        when(mockRepositoryService.allInstances(DomainModel.class)).thenReturn(List.of(model));

        // when
        final List<ClassCdd> rejected = classUnderTest.rejectEmptyClassesInAllModels("no empty boxes");

        // then
        assertThat(rejected).containsExactly(ghost);
        verify(mockReviews).rejectAsAgent(ghost, ReviewRationale.INSUFFICIENT_INFORMATION, "no empty boxes");
    }

    // --- helpers --------------------------------------------------------------

    /** A class with no members at all. */
    private static ClassCdd empty(final String name) {
        return new ClassCdd(name, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /** A class whose single responsibility is approved — so the class has content of its own. */
    private static ClassCdd withResponsibility(final String name, final SubDomain subDomain) {
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