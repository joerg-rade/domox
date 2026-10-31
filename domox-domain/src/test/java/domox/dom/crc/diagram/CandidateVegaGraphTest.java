package domox.dom.crc.diagram;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.model.Review;
import domox.dom.crc.model.ReviewStatus;
import domox.dom.crc.service.AttributeCandidates;
import domox.dom.crc.service.ClassCandidates;
import domox.dom.crc.service.CollaboratorCandidates;
import domox.dom.crc.service.ResponsibilityCandidates;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import org.apache.causeway.valuetypes.vega.applib.value.Vega;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link CandidateVegaGraph#renderGraph(Candidate)} through its
 * public API, covering null/empty guards, schema classification (must be
 * {@link Vega.Schema#VEGA}, never {@code NONE}), force-directed node-link
 * structure, candidate-word restriction, hop-depth expansion,
 * association-artifact filtering, approved-node marking, and POS-colour
 * legend.
 * <p>
 * The test mocks the four candidate-repository services (mirroring
 * {@link CandidateDiagramsTest}) and sets up {@link TypedDependency}s
 * through {@link RuleMatch#setTypedDependency(TypedDependency)}.
 */
@ExtendWith(MockitoExtension.class)
class CandidateVegaGraphTest {

    @Mock
    ClassCandidates classCandidates;
    @Mock
    ResponsibilityCandidates responsibilityCandidates;
    @Mock
    AttributeCandidates attributeCandidates;
    @Mock
    CollaboratorCandidates collaboratorCandidates;

    CandidateVegaGraph service;

    @BeforeEach
    void setUp() {
        service = new CandidateVegaGraph(
                classCandidates, responsibilityCandidates, attributeCandidates, collaboratorCandidates);
    }

    // ============ null / empty guards ============

    @Test
    void returnsNullWhenCandidateIsNull() {
        assertNull(service.renderGraph(null));
    }

    @Test
    void returnsVegaWhenAllRepositoriesAreEmpty() {
        stubEmptyLists();
        final ClassCdd candidate = new ClassCdd("Pet", List.of(), List.of(), List.of());
        candidate.setCandidateType("ClassCdd");
        assertNotNull(service.renderGraph(candidate));
    }

    @Test
    void returnsVegaWhenRuleMatchesAreNull() {
        stubEmptyLists();
        final ClassCdd candidate = new ClassCdd("Pet", List.of(), List.of(), List.of());
        candidate.setCandidateType("ClassCdd");
        candidate.setRuleMatches(null);
        assertNotNull(service.renderGraph(candidate));
    }

    @Test
    void handlesNullCandidateType() {
        stubEmptyLists();
        final ClassCdd candidate = new ClassCdd("Pet", List.of(), List.of(), List.of());
        candidate.setCandidateType(null);
        assertNotNull(service.renderGraph(candidate));
    }

    @Test
    void handlesNullCandidateName() {
        stubEmptyLists();
        final ClassCdd candidate = new ClassCdd(null, List.of(), List.of(), List.of());
        candidate.setCandidateType("ClassCdd");
        assertNotNull(service.renderGraph(candidate));
    }

    // ============ schema / structure ============

    @Test
    void usesFullVegaV6Schema() {
        stubEmptyLists();
        final ClassCdd candidate = new ClassCdd("Pet", List.of(), List.of(), List.of());
        candidate.setCandidateType("ClassCdd");
        final Vega vega = service.renderGraph(candidate);
        assertNotNull(vega);
        assertSame(Vega.Schema.VEGA, vega.getSchema(),
                "Must be classified VEGA (not NONE) — NONE renders nothing in the Wicket viewer");
        final String json = vega.getJson();
        assertTrue(json.contains("https://vega.github.io/schema/vega/v6.json"),
                "Schema URL must point to Vega v6");
    }

    @Test
    void vegaSpecHasForceDirectedStructure() {
        stubEmptyLists();
        final ClassCdd candidate = new ClassCdd("Pet", List.of(), List.of(), List.of());
        candidate.setCandidateType("ClassCdd");
        final String json = service.renderGraph(candidate).getJson();
        assertTrue(json.startsWith("{"), "Vega spec must be a JSON object");
        assertTrue(json.endsWith("}"), "Vega spec must be a JSON object");
        assertTrue(json.contains("\"force\""), "Spec must contain the force transform");
        assertTrue(json.contains("\"node-data\""), "Spec must define node-data");
        assertTrue(json.contains("\"link-data\""), "Spec must define link-data");
        assertTrue(json.indexOf("\"link-data\"") < json.indexOf("\"node-data\""),
                "link-data must appear before node-data in the data array so Vega " +
                "processes it before the force transform in node-data references it");
        assertTrue(json.contains("\"type\": \"rect\""), "Spec must contain rect marks for rounded-square nodes");
        assertFalse(json.contains("vconcat"), "Spec must not use vconcat (bar-chart structure)");
        assertFalse(json.contains("vega-lite"), "Spec must not reference Vega-Lite");
    }

    @Test
    void vegaSpecTitleContainsCandidateTypeAndName() {
        stubEmptyLists();
        final ClassCdd candidate = new ClassCdd("Pet", List.of(), List.of(), List.of());
        candidate.setCandidateType("ClassCdd");
        final String json = service.renderGraph(candidate).getJson();
        assertTrue(json.contains("ClassCdd: Pet"),
                "Title should be '<candidateType>: <candidateName>'");
    }

    // ============ candidate neighbourhood ============

    @Test
    void drawsSingleNodeWhenNoDependencies() {
        stubEmptyLists();
        final ClassCdd candidate = new ClassCdd("Pet", List.of(), List.of(), List.of());
        candidate.setCandidateType("ClassCdd");
        final String json = service.renderGraph(candidate).getJson();
        assertTrue(json.contains("\"id\":\"pet\""),
                "Node-data must contain the candidate's lemma as a node");
        assertFalse(json.contains("\"source\":"),
                "No link-data when there are no typed dependencies");
    }

    @Test
    void includesDependencyLinkBetweenCandidateWords() {
        final ClassCdd customer = new ClassCdd();
        customer.setCandidateName("Customer");
        customer.setCandidateType("ClassCdd");
        customer.getRuleMatches().add(ruleMatchWith(
                dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                    "customer", PartOfSpeechType.NN)));

        final ResponsibilityCdd purchase = new ResponsibilityCdd();
        purchase.setCandidateName("Purchase");
        purchase.setCandidateType("ActionCdd");

        when(classCandidates.listAll()).thenReturn(List.of(customer));
        when(responsibilityCandidates.listAll()).thenReturn(List.of(purchase));
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());

        final String json = service.renderGraph(customer).getJson();
        assertTrue(json.contains("\"source\":\"purchase\""),
                "Link source must be the governor lemma");
        assertTrue(json.contains("\"target\":\"customer\""),
                "Link target must be the dependent lemma");
        assertTrue(json.contains("\"label\":\"nsubj\""),
                "Link must carry the TdType code as label");
        assertTrue(json.contains("\"name\":\"Purchase\""),
                "Node-data must include the linked candidate 'Purchase'");
        assertTrue(json.contains("\"name\":\"Customer\""),
                "Node-data must include the linked candidate 'Customer'");
    }

    @Test
    void restrictsDependenciesToCandidateWords() {
        final ClassCdd customer = new ClassCdd();
        customer.setCandidateName("Customer");
        customer.setCandidateType("ClassCdd");
        customer.getRuleMatches().add(ruleMatchWith(
                dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                    "customer", PartOfSpeechType.NN)));       // kept
        customer.getRuleMatches().add(ruleMatchWith(
                dep(TdType.OBJ, "purchase", PartOfSpeechType.VBZ,
                    "catalog", PartOfSpeechType.NN)));          // dropped

        final ResponsibilityCdd purchase = new ResponsibilityCdd();
        purchase.setCandidateName("Purchase");
        purchase.setCandidateType("ActionCdd");

        when(classCandidates.listAll()).thenReturn(List.of(customer));
        when(responsibilityCandidates.listAll()).thenReturn(List.of(purchase));
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());

        final String json = service.renderGraph(customer).getJson();
        assertTrue(json.contains("\"source\":\"purchase\""),
                "The candidate-word link purchase→customer must be present");
        assertTrue(json.contains("\"target\":\"customer\""),
                "The candidate-word link purchase→customer must be present");
        assertFalse(json.contains("\"catalog\""),
                "Non-candidate word 'catalog' must be excluded from the graph");
    }

    @Test
    void deepensNeighbourhoodAtHopDepthTwo() {
        final ClassCdd customer = new ClassCdd();
        customer.setCandidateName("Customer");
        customer.setCandidateType("ClassCdd");
        customer.setHopDepth(2);
        customer.getRuleMatches().add(ruleMatchWith(
                dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                    "customer", PartOfSpeechType.NN)));

        final ResponsibilityCdd purchase = new ResponsibilityCdd();
        purchase.setCandidateName("Purchase");
        purchase.setCandidateType("ActionCdd");
        purchase.getRuleMatches().add(ruleMatchWith(
                dep(TdType.OBJ, "purchase", PartOfSpeechType.VBZ,
                    "order", PartOfSpeechType.NN)));

        final ClassCdd order = new ClassCdd();
        order.setCandidateName("Order");
        order.setCandidateType("ClassCdd");

        when(classCandidates.listAll()).thenReturn(List.of(customer, order));
        when(responsibilityCandidates.listAll()).thenReturn(List.of(purchase));
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());

        final String json = service.renderGraph(customer).getJson();
        assertTrue(json.contains("\"name\":\"Order\""),
                "Second-level candidate 'Order' should surface at hop depth 2");
        assertTrue(json.contains("\"source\":\"purchase\""),
                "The purchase→order link must be present");
        assertTrue(json.contains("\"target\":\"order\""),
                "The purchase→order link must be present");
    }

    @Test
    void defaultDepthOneExcludesSecondHopNodes() {
        final ClassCdd customer = new ClassCdd();
        customer.setCandidateName("Customer");
        customer.setCandidateType("ClassCdd");
        // hopDepth stays at default 1
        customer.getRuleMatches().add(ruleMatchWith(
                dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                    "customer", PartOfSpeechType.NN)));

        final ResponsibilityCdd purchase = new ResponsibilityCdd();
        purchase.setCandidateName("Purchase");
        purchase.setCandidateType("ActionCdd");
        purchase.getRuleMatches().add(ruleMatchWith(
                dep(TdType.OBJ, "purchase", PartOfSpeechType.VBZ,
                    "order", PartOfSpeechType.NN)));

        final ClassCdd order = new ClassCdd();
        order.setCandidateName("Order");
        order.setCandidateType("ClassCdd");

        when(classCandidates.listAll()).thenReturn(List.of(customer, order));
        when(responsibilityCandidates.listAll()).thenReturn(List.of(purchase));
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());

        final String json = service.renderGraph(customer).getJson();
        assertFalse(json.contains("\"name\":\"Order\""),
                "Second-level candidate 'Order' must NOT appear at the default depth of 1");
    }

    @Test
    void excludesAssociationArtifactNodes() {
        final ClassCdd coverage = new ClassCdd();
        coverage.setCandidateName("Coverage");
        coverage.setCandidateType("ClassCdd");
        coverage.getRuleMatches().add(ruleMatchWith(
                dep(TdType.NSUBJ, "coverage", PartOfSpeechType.NN,
                    "age", PartOfSpeechType.NN)));                     // kept
        coverage.getRuleMatches().add(ruleMatchWith(
                dep(TdType.OBJ, "coverage_age", PartOfSpeechType.NN,
                    "coverage", PartOfSpeechType.NN)));                // artifactual

        final AttributeCdd age = new AttributeCdd();
        age.setCandidateName("Age");
        age.setCandidateType("PropertyCdd");

        final CollaboratorCdd coverageAge = new CollaboratorCdd();
        coverageAge.setCandidateName("Coverage_Age");
        coverageAge.setCandidateType("AssociationCdd");

        when(classCandidates.listAll()).thenReturn(List.of(coverage));
        when(responsibilityCandidates.listAll()).thenReturn(List.of());
        when(attributeCandidates.listAll()).thenReturn(List.of(age));
        when(collaboratorCandidates.listAll()).thenReturn(List.of(coverageAge));

        final String json = service.renderGraph(coverage).getJson();
        assertTrue(json.contains("\"source\":\"coverage\""),
                "The kept link coverage→age must have source 'coverage'");
        assertTrue(json.contains("\"target\":\"age\""),
                "The kept link coverage→age must have target 'age'");
        assertFalse(json.contains("coverage_age"),
                "The synthetic compound lemma 'coverage_age' must be excluded from the graph");
    }

    // ============ node metadata ============

    @Test
    void marksApprovedNodes() {
        final ClassCdd customer = new ClassCdd();
        customer.setCandidateName("Customer");
        customer.setCandidateType("ClassCdd");
        customer.getRuleMatches().add(ruleMatchWith(
                dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                    "customer", PartOfSpeechType.NN)));
        final Review approved = new Review();
        approved.setStatus(ReviewStatus.APPROVED);
        customer.addReview(approved);

        final ResponsibilityCdd purchase = new ResponsibilityCdd();
        purchase.setCandidateName("Purchase");
        purchase.setCandidateType("ActionCdd");

        when(classCandidates.listAll()).thenReturn(List.of(customer));
        when(responsibilityCandidates.listAll()).thenReturn(List.of(purchase));
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());

        final String json = service.renderGraph(customer).getJson();
        assertTrue(json.contains("\"approved\":true"),
                "Approved candidate must have 'approved': true");
        assertTrue(json.contains("\"approved\":false"),
                "Non-approved candidate must have 'approved': false");
    }

    @Test
    void addsPosCategoryColourScaleAndLegend() {
        final ClassCdd customer = new ClassCdd();
        customer.setCandidateName("Customer");
        customer.setCandidateType("ClassCdd");
        customer.getRuleMatches().add(ruleMatchWith(
                dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                    "customer", PartOfSpeechType.NN)));

        final ResponsibilityCdd purchase = new ResponsibilityCdd();
        purchase.setCandidateName("Purchase");
        purchase.setCandidateType("ActionCdd");

        when(classCandidates.listAll()).thenReturn(List.of(customer));
        when(responsibilityCandidates.listAll()).thenReturn(List.of(purchase));
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());

        final String json = service.renderGraph(customer).getJson();
        assertTrue(json.contains("\"Noun\""), "Scale domain must include 'Noun' for NN");
        assertTrue(json.contains("\"Verb\""), "Scale domain must include 'Verb' for VBZ");
        assertTrue(json.contains("#3498DB"), "Range must include the Noun colour");
        assertTrue(json.contains("#E74C3C"), "Range must include the Verb colour");
        assertTrue(json.contains("\"legends\""), "Spec must declare a top-level legends array");
        assertTrue(json.contains("Part of Speech"), "Legend title must be 'Part of Speech'");
    }

    // ============ helper factories ============

    private void stubEmptyLists() {
        when(classCandidates.listAll()).thenReturn(List.of());
        when(responsibilityCandidates.listAll()).thenReturn(List.of());
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());
    }

    private static RuleMatch ruleMatchWith(final TypedDependency td) {
        final RuleMatch rm = new RuleMatch();
        rm.setTypedDependency(td);
        return rm;
    }

    private static TypedDependency dep(final TdType type,
                                       final String governorLemma,
                                       final PartOfSpeechType governorPos,
                                       final String dependentLemma,
                                       final PartOfSpeechType dependentPos) {
        final TypedDependency td = new TypedDependency();
        td.setType(type);
        td.setGovernorIndex(1);
        td.setGovernorLemma(governorLemma);
        td.setGovernorPos(governorPos);
        td.setDependentIndex(2);
        td.setDependentLemma(dependentLemma);
        td.setDependentPos(dependentPos);
        return td;
    }
}
