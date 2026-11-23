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
import domox.port.DiagramPort;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainModelsTest {

    /** Stands in for a rendered PDF — the port's bytes are not what these tests assert. */
    private static final byte[] PDF_BYTES = {'%', 'P', 'D', 'F'};

    private DomainModels classUnderTest;

    @Mock
    RepositoryService mockRepositoryService;

    @Mock
    Reviews mockReviews;

    @Mock
    DiagramPort mockDiagramPort;

    @BeforeEach
    public void setUp() {
        classUnderTest = new DomainModels(
                mockRepositoryService, mockReviews, mockDiagramPort);
    }

    /** The port is only the renderer; the diagram tests assert the view model around it. */
    private void stubDiagramRendering() {
        when(mockDiagramPort.buildPlantUmlDiagram(anyString())).thenReturn(PDF_BYTES);
    }

    @Test
    void generateUmlDiagramRendersClassesAndAssociations() {
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
        stubDiagramRendering();

        // when
        final UmlClassDiagram result = classUnderTest.generateUmlDiagram(domainModel);

        // then — the assembled PlantUML is still delivered ...
        final String plantUml = result.getPlantUml();
        assertThat(domainModel.classList).hasSize(2);
        assertThat(plantUml).as("@startuml tag missing").startsWith("@startuml");
        assertThat(plantUml).as("@enduml tag missing").endsWith("@enduml");
        assertThat(plantUml).contains("class \"Alice");
        assertThat(plantUml).as("the approved cardinality is quoted on the edge").contains("Alice->\"2\"Bob");
        // ... and the diagram itself is the RENDERED one, not the source text
        assertThat(result.getDiagram()).isNotNull();
        assertThat(result.getDiagram().mimeType().toString()).contains("application/pdf");
        assertThat(result.getMessage()).as("nothing to report on a successful render").isNull();
        assertThat(result.hideMessage()).isTrue();
    }

    // --- one diagram per sub-domain, and the overview -------------------------

    @Test
    void generateSubDomainUmlDiagramRendersOnlyThatSubDomain() {
        // given — one class in each of two sub-domains
        final DomainModel model = new DomainModel();
        model.classList.add(approved(withResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE))));
        model.classList.add(approved(withResponsibility("Till",
                subDomain("Checkout", SubDomainClassification.SUPPORTING))));
        stubDiagramRendering();

        // when
        final UmlClassDiagram result = classUnderTest.generateSubDomainUmlDiagram(model, "Inventory");

        // then
        assertThat(result.getPlantUml()).contains("class \"Store\"").doesNotContain("Till");
        assertThat(result.getDiagram()).as("rendered, not the source").isNotNull();
    }

    @Test
    void choices1GenerateSubDomainUmlDiagram_listsTheSubDomainsOfTheModel() {
        // given — the choices a reviewer picks from, in name order
        final DomainModel model = new DomainModel();
        model.classList.add(approved(withResponsibility("Till",
                subDomain("Checkout", SubDomainClassification.SUPPORTING))));
        model.classList.add(approved(withResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE))));

        // when / then
        assertThat(classUnderTest.choices1GenerateSubDomainUmlDiagram(model))
                .containsExactly("Checkout", "Inventory");
    }

    @Test
    void generateSubDomainOverviewDiagramRendersNodesAndCrossEdges() {
        // given — a relation that crosses from Inventory into Checkout
        final DomainModel model = new DomainModel();
        final ClassCdd store = approved(withResponsibility("Store",
                subDomain("Inventory", SubDomainClassification.CORE)));
        final ClassCdd till = approved(withResponsibility("Till",
                subDomain("Checkout", SubDomainClassification.SUPPORTING)));
        approved(collaboration(model, "has", store, till));
        model.classList.add(store);
        model.classList.add(till);
        stubDiagramRendering();

        // when
        final UmlClassDiagram result = classUnderTest.generateSubDomainOverviewDiagram(model);

        // then
        assertThat(result.getPlantUml()).contains("class \"Inventory (CORE)\" as SD0");
        assertThat(result.getPlantUml()).contains("SD0 --> SD1 : 1");
        assertThat(result.getDiagram()).as("rendered, not the source").isNotNull();
    }

    // --- rendering failures are reported, not thrown --------------------------

    @Test
    void generateUmlDiagramReportsARenderingFailureAndStillDeliversTheSource() {
        // given — an approved class to draw, and a Kroki that is down
        final DomainModel model = new DomainModel();
        model.classList.add(approved(withResponsibility("Store", null)));
        when(mockDiagramPort.buildPlantUmlDiagram(anyString()))
                .thenThrow(new IllegalStateException("connection refused"));

        // when
        final UmlClassDiagram result = classUnderTest.generateUmlDiagram(model);

        // then — no picture, but the source and the reason are both on the view model
        assertThat(result.getDiagram()).isNull();
        assertThat(result.getPlantUml()).as("the source survives a failed render").contains("class \"Store\"");
        assertThat(result.getMessage()).contains("connection refused");
        assertThat(result.hideMessage()).as("the message is shown when there is one").isFalse();
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