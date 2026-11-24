
package domox.dom.crc.service;

import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.model.Review;
import domox.dom.crc.model.ReviewStatus;
import domox.dom.crc.model.SubDomain;
import domox.dom.crc.model.SubDomainClassification;
import domox.dom.rdf.UmlDiagramLayout;
import generate.ClassDiagram;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level guardrail for {@link DiagramKind} — <b>the one place that knows what differs</b> between the
 * three class diagrams.
 *
 * <p><b>Why this needs pinning.</b>  Everything the kind decides used to be re-switched in five methods
 * across three classes ({@link DomainModels}, {@link UmlClassDiagram}, {@link UmlDiagramSet}), so the
 * behaviour could be — and was — changed in one place and not the others.  Each test below names one of
 * those decisions, so a change is a visible edit here rather than a diagram that quietly draws the wrong
 * thing: the assembler call ({@link DiagramKind#graph}, {@link DiagramKind#plantUml}), the two wordings of
 * the name ({@link DiagramKind#label}, {@link DiagramKind#describe}), and the download's file name
 * ({@link DiagramKind#fileName}).</p>
 *
 * <p>No mocks and no Spring: the enum is a pure function of the model, and the model is three entities.
 * That the <em>actions</em> wire to the right kind is {@code DomainModelsTest}'s subject; this class checks
 * the kind table itself.</p>
 */
class DiagramKindTest {

    @Test
    void everyKeyReadsBackAsItsOwnKind() {
        assertThat(DiagramKind.of("model")).isEqualTo(DiagramKind.MODEL);
        assertThat(DiagramKind.of("sub-domain")).isEqualTo(DiagramKind.SUB_DOMAIN);
        assertThat(DiagramKind.of("sub-domain-overview")).isEqualTo(DiagramKind.SUB_DOMAIN_OVERVIEW);
    }

    /**
     * The fallback is what makes the kind safe to carry around: the action parameter is optional, and a
     * bookmark can name a kind a later release removed.
     */
    @Test
    void anUnsetBlankOrUnknownKeyReadsAsTheWholeModel() {
        assertThat(DiagramKind.of(null)).isEqualTo(DiagramKind.MODEL);
        assertThat(DiagramKind.of("")).isEqualTo(DiagramKind.MODEL);
        assertThat(DiagramKind.of("   ")).isEqualTo(DiagramKind.MODEL);
        assertThat(DiagramKind.of("no-such-diagram")).isEqualTo(DiagramKind.MODEL);
    }

    @Test
    void theKeysAreTheThreeConstantsTheMementoCarries() {
        assertThat(DiagramKind.keys()).containsExactly(
                UmlClassDiagram.KIND_MODEL, UmlClassDiagram.KIND_SUB_DOMAIN,
                UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW);
        assertThat(DiagramKind.values()).extracting(DiagramKind::key)
                .as("a kind added to the enum joins the choices without a second edit")
                .containsExactlyElementsOf(DiagramKind.keys());
    }

    @Test
    void theLabelNamesTheDiagramAndCarriesTheSubDomain() {
        assertThat(DiagramKind.MODEL.label(null)).isEqualTo("Whole model");
        assertThat(DiagramKind.SUB_DOMAIN.label("Inventory")).isEqualTo("Sub-domain: Inventory");
        assertThat(DiagramKind.SUB_DOMAIN.label(null))
                .as("an unnamed sub-domain still says which view this is").isEqualTo("Sub-domain: (unnamed)");
        assertThat(DiagramKind.SUB_DOMAIN_OVERVIEW.label("Inventory"))
                .as("the overview is not of one sub-domain").isEqualTo("Sub-domain overview");
    }

    @Test
    void theDescriptionIsTheSameNameAsASentenceFragment() {
        assertThat(DiagramKind.MODEL.describe(null)).isEqualTo("whole model");
        assertThat(DiagramKind.SUB_DOMAIN.describe("Inventory")).isEqualTo("sub-domain Inventory");
        assertThat(DiagramKind.SUB_DOMAIN.describe(null)).isEqualTo("sub-domain (unnamed)");
        assertThat(DiagramKind.SUB_DOMAIN_OVERVIEW.describe(null)).isEqualTo("sub-domain overview");
    }

    @Test
    void theFileNameNamesTheDownloadForTheDiagram() {
        assertThat(DiagramKind.MODEL.fileName("Inventory")).isEqualTo("domain-model.puml");
        assertThat(DiagramKind.SUB_DOMAIN.fileName("Inventory")).isEqualTo("domain-model-Inventory.puml");
        assertThat(DiagramKind.SUB_DOMAIN.fileName(null)).isEqualTo("domain-model-sub-domain.puml");
        assertThat(DiagramKind.SUB_DOMAIN_OVERVIEW.fileName(null)).isEqualTo("domain-model-sub-domains.puml");
    }

    // --- the two forms, over a model that has all three diagrams ------------------

    @Test
    void theGraphIsTheDiagramTheKindStandsFor() {
        // given — one class in each of two sub-domains
        final DomainModel model = twoSubDomains();

        // when / then — the whole model draws both, one sub-domain draws only its own, and the overview
        // draws the sub-domains themselves rather than their classes
        assertThat(nodeIds(DiagramKind.MODEL.graph(model, null))).containsExactlyInAnyOrder("Store", "Till");
        assertThat(nodeIds(DiagramKind.SUB_DOMAIN.graph(model, "Inventory"))).containsExactly("Store");
        assertThat(nodeIds(DiagramKind.SUB_DOMAIN_OVERVIEW.graph(model, null)))
                .containsExactlyInAnyOrder("Inventory (CORE)", "Checkout (SUPPORTING)");
    }

    @Test
    void thePlantUmlIsTheTextTheKindStandsFor() {
        final DomainModel model = twoSubDomains();

        assertThat(DiagramKind.MODEL.plantUml(model, null))
                .startsWith("@startuml").endsWith("@enduml")
                .contains("class \"Store\"").contains("class \"Till\"");
        assertThat(DiagramKind.SUB_DOMAIN.plantUml(model, "Inventory"))
                .contains("class \"Store\"").doesNotContain("Till");
        assertThat(DiagramKind.SUB_DOMAIN_OVERVIEW.plantUml(model, null))
                .as("the overview draws one node per sub-domain").contains("Inventory (CORE)");
    }

    @Test
    void theSubDomainsAreTheOnesThatWouldProduceADiagram() {
        assertThat(DiagramKind.subDomainNames(twoSubDomains())).containsExactly("Checkout", "Inventory");
    }

    // --- helpers --------------------------------------------------------------

    /** Two sub-domains with one class each — the smallest model that has all three diagrams. */
    private static DomainModel twoSubDomains() {
        final DomainModel model = new DomainModel();
        model.classList.add(approved(
                withResponsibility("Store", subDomain("Inventory", SubDomainClassification.CORE))));
        model.classList.add(approved(
                withResponsibility("Till", subDomain("Checkout", SubDomainClassification.SUPPORTING))));
        return model;
    }

    /** The ids of a graph's boxes, in the order the layout emits them. */
    private static List<String> nodeIds(final ClassDiagram.Graph graph) {
        return graph.getNodes().stream().map(UmlDiagramLayout.Node::id).toList();
    }

    /** A class with no members at all. */
    private static ClassCdd empty(final String name) {
        return new ClassCdd(name, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /** A class whose single responsibility is approved — so the class has content, and is drawn. */
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
