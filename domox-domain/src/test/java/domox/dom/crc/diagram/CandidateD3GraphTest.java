package domox.dom.crc.diagram;

import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.model.Review;
import domox.dom.crc.model.ReviewStatus;
import domox.dom.crc.model.SubDomain;
import domox.dom.crc.service.AttributeCandidates;
import domox.dom.crc.service.ClassCandidates;
import domox.dom.crc.service.CollaboratorCandidates;
import domox.dom.crc.service.ResponsibilityCandidates;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link CandidateD3Graph#renderGraph(Candidate)} through its public API: null/empty guards, the
 * {@code nodes}/{@code links}/{@code legend} structure, candidate-word restriction, hop-depth expansion,
 * association-artifact filtering, approved-node marking, sub-domain decoration and the click-through
 * fields.
 * <p>
 * The three candidate-graph renderers share one assembler ({@link CandidateGraphModel}), so this class and
 * {@link CandidateVegaGraphTest} are deliberately parallel: they pin the <em>same</em> neighbourhood
 * semantics through each renderer's own JSON, which is what keeps the shared model from drifting away from
 * either output format.
 */
@ExtendWith(MockitoExtension.class)
class CandidateD3GraphTest {

    @Mock
    ClassCandidates classCandidates;
    @Mock
    ResponsibilityCandidates responsibilityCandidates;
    @Mock
    AttributeCandidates attributeCandidates;
    @Mock
    CollaboratorCandidates collaboratorCandidates;

    CandidateD3Graph service;

    @BeforeEach
    void setUp() {
        service = new CandidateD3Graph(
                classCandidates, responsibilityCandidates, attributeCandidates, collaboratorCandidates);
    }

    // ============ null / empty guards ============

    @Test
    void returnsNullWhenCandidateIsNull() {
        assertThat(service.renderGraph(null)).isNull();
    }

    @Test
    void rendersTheSeedNodeWhenAllRepositoriesAreEmpty() {
        stubEmptyLists();
        final ClassCdd candidate = new ClassCdd("Pet", List.of(), List.of(), List.of());
        candidate.setCandidateType("ClassCdd");

        final String json = service.renderGraph(candidate);

        assertThat(json).as("the candidate's own word is always a node").contains("\"id\":\"pet\"");
        assertThat(json).as("the display name keeps the candidate's own casing").contains("\"name\":\"Pet\"");
        assertThat(json).as("no dependencies -> no links").contains("\"links\":[]");
        assertThat(json).as("the three top-level keys are always present")
                .contains("\"nodes\"").contains("\"links\"").contains("\"legend\"");
    }

    @Test
    void handlesNullCandidateNameAndType() {
        stubEmptyLists();
        final ClassCdd candidate = new ClassCdd(null, List.of(), List.of(), List.of());
        candidate.setCandidateType(null);

        assertThat(service.renderGraph(candidate)).isNotNull();
    }

    // ============ candidate neighbourhood ============

    @Test
    void includesDependencyLinkBetweenCandidateWords() {
        final ClassCdd customer = classCandidate("Customer");
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN)));
        final ResponsibilityCdd purchase = actionCandidate("Purchase");

        when(classCandidates.listAll()).thenReturn(List.of(customer));
        when(responsibilityCandidates.listAll()).thenReturn(List.of(purchase));
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());

        final String json = service.renderGraph(customer);

        assertThat(json).as("both candidate words are nodes")
                .contains("\"id\":\"customer\"").contains("\"id\":\"purchase\"");
        assertThat(json).as("the typed dependency becomes a link").contains("\"source\":\"purchase\"");
        assertThat(json).as("the typed dependency becomes a link").contains("\"target\":\"customer\"");
        assertThat(json).as("the link carries the dependency type").contains("\"label\":\"nsubj\"");
    }

    @Test
    void dropsDependencyWhoseEndpointIsNotACandidateWord() {
        final ClassCdd customer = classCandidate("Customer");
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.DET, "customer", PartOfSpeechType.NN,
                "the", PartOfSpeechType.DT)));   // 'the' is a content word but not a candidate

        when(classCandidates.listAll()).thenReturn(List.of(customer));
        when(responsibilityCandidates.listAll()).thenReturn(List.of());
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());

        final String json = service.renderGraph(customer);

        assertThat(json).as("a non-candidate endpoint means no link at all").contains("\"links\":[]");
        assertThat(json).as("'the' must not become a node").doesNotContain("\"id\":\"the\"");
    }

    @Test
    void defaultHopDepthExcludesSecondLevelNeighbours() {
        // customer <-> purchase (direct), purchase <-> order (one hop further)
        final ClassCdd customer = classCandidate("Customer");
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN)));
        final ResponsibilityCdd purchase = actionCandidate("Purchase");
        purchase.getRuleMatches().add(ruleMatchWith(dep(TdType.OBJ, "purchase", PartOfSpeechType.VBZ,
                "order", PartOfSpeechType.NN)));
        final ClassCdd order = classCandidate("Order");

        when(classCandidates.listAll()).thenReturn(List.of(customer, order));
        when(responsibilityCandidates.listAll()).thenReturn(List.of(purchase));
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());

        final String json = service.renderGraph(customer);

        assertThat(json).as("the direct neighbour is drawn").contains("\"id\":\"purchase\"");
        assertThat(json).as("the second-level neighbour is not, at the default depth of 1")
                .doesNotContain("\"id\":\"order\"");
    }

    @Test
    void raisesTheDepthWhenHopDepthIsIncreased() {
        final ClassCdd customer = classCandidate("Customer");
        customer.setHopDepth(2);
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN)));
        final ResponsibilityCdd purchase = actionCandidate("Purchase");
        purchase.getRuleMatches().add(ruleMatchWith(dep(TdType.OBJ, "purchase", PartOfSpeechType.VBZ,
                "order", PartOfSpeechType.NN)));
        final ClassCdd order = classCandidate("Order");

        when(classCandidates.listAll()).thenReturn(List.of(customer, order));
        when(responsibilityCandidates.listAll()).thenReturn(List.of(purchase));
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());

        final String json = service.renderGraph(customer);

        assertThat(json).as("at depth 2 the second-level neighbour surfaces").contains("\"id\":\"order\"");
    }

    @Test
    void excludesAssociationArtifactCompoundNodes() {
        final ClassCdd coverage = classCandidate("Coverage");
        coverage.getRuleMatches().add(ruleMatchWith(dep(TdType.NSUBJ, "coverage", PartOfSpeechType.NN,
                "age", PartOfSpeechType.NN)));                       // kept
        coverage.getRuleMatches().add(ruleMatchWith(dep(TdType.OBJ, "coverage_age", PartOfSpeechType.NN,
                "coverage", PartOfSpeechType.NN)));                  // artifactual

        final AttributeCdd age = new AttributeCdd();
        age.setCandidateName("Age");
        age.setCandidateType("AttributeCdd");

        final CollaboratorCdd coverageAge = new CollaboratorCdd();
        coverageAge.setCandidateName("Coverage_Age");
        coverageAge.setCandidateType("CollaboratorCdd");

        when(classCandidates.listAll()).thenReturn(List.of(coverage));
        when(responsibilityCandidates.listAll()).thenReturn(List.of());
        when(attributeCandidates.listAll()).thenReturn(List.of(age));
        when(collaboratorCandidates.listAll()).thenReturn(List.of(coverageAge));

        final String json = service.renderGraph(coverage);

        assertThat(json).as("the participant lemmas and their link survive")
                .contains("\"source\":\"coverage\"").contains("\"target\":\"age\"");
        assertThat(json).as("the synthetic compound node is dropped")
                .doesNotContain("\"id\":\"coverage_age\"");
    }

    // ============ node decoration ============

    @Test
    void marksApprovedCandidatesAndLeavesTheRestUnmarked() {
        // the seed is approved; its neighbour in the same graph is not
        final ClassCdd customer = classCandidate("Customer");
        final Review approved = new Review();
        approved.setStatus(ReviewStatus.APPROVED);
        customer.getReviews().add(approved);
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN)));
        final ResponsibilityCdd purchase = actionCandidate("Purchase");

        when(classCandidates.listAll()).thenReturn(List.of(customer));
        when(responsibilityCandidates.listAll()).thenReturn(List.of(purchase));
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());

        final String json = service.renderGraph(customer);

        assertThat(json).as("the approved candidate is marked").contains("\"approved\":true");
        assertThat(json).as("the unapproved neighbour is not").contains("\"approved\":false");
    }

    @Test
    void decoratesAClassNodeWithItsSubDomain() {
        final ClassCdd store = classCandidate("Store");
        final SubDomain inventory = new SubDomain();
        inventory.setName("Inventory");
        store.setSubDomain(inventory);

        when(classCandidates.listAll()).thenReturn(List.of(store));
        when(responsibilityCandidates.listAll()).thenReturn(List.of());
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());

        assertThat(service.renderGraph(store)).contains("\"subdomain\":\"Inventory\"");
    }

    @Test
    void marksCandidateWordsWithTheirObjectTypeForClickThrough() {
        final ClassCdd customer = classCandidate("Customer");

        when(classCandidates.listAll()).thenReturn(List.of(customer));
        when(responsibilityCandidates.listAll()).thenReturn(List.of());
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());

        final String json = service.renderGraph(customer);

        assertThat(json).as("a candidate word carries the click-through marker")
                .contains("\"isCandidateColumn\":true");
        assertThat(json).as("and the Causeway object type of its entity")
                .contains("\"objectType\":\"domox.ClassCdd\"");
    }

    @Test
    void emitsALegendEntryForEachPosCategoryPresent() {
        final ClassCdd customer = classCandidate("Customer");
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN)));
        final ResponsibilityCdd purchase = actionCandidate("Purchase");

        when(classCandidates.listAll()).thenReturn(List.of(customer));
        when(responsibilityCandidates.listAll()).thenReturn(List.of(purchase));
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());

        final String json = service.renderGraph(customer);

        assertThat(json).as("the noun category is in the legend").contains("\"label\":\"Noun\"");
        assertThat(json).as("the verb category is in the legend").contains("\"label\":\"Verb\"");
        assertThat(json).as("the legend carries the noun colour").contains("#3498DB");
    }

    // ============ helper factories ============

    private void stubEmptyLists() {
        when(classCandidates.listAll()).thenReturn(List.of());
        when(responsibilityCandidates.listAll()).thenReturn(List.of());
        when(attributeCandidates.listAll()).thenReturn(List.of());
        when(collaboratorCandidates.listAll()).thenReturn(List.of());
    }

    private static ClassCdd classCandidate(final String name) {
        final ClassCdd c = new ClassCdd();
        c.setCandidateName(name);
        c.setCandidateType("ClassCdd");
        return c;
    }

    private static ResponsibilityCdd actionCandidate(final String name) {
        final ResponsibilityCdd a = new ResponsibilityCdd();
        a.setCandidateName(name);
        a.setCandidateType("ResponsibilityCdd");
        return a;
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
