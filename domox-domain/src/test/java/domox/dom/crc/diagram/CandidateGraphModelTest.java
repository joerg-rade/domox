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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit-level guardrail for {@link CandidateGraphModel} — the neighbourhood, node set and per-lemma
 * metadata the three lexical diagram renderers share.
 * <p>
 * The renderers' own tests ({@link CandidateD3GraphTest}, {@link CandidateVegaGraphTest},
 * {@code DomainModelDiagramsD3Test}) pin the assembled <em>JSON</em>; this class pins the model the JSON is
 * written from, including the branches that are awkward to reach through a renderer — the seed-word
 * fallback when the seed is itself an artifact compound, and the null-safe repository snapshot.
 */
@ExtendWith(MockitoExtension.class)
class CandidateGraphModelTest {

    @Mock
    ClassCandidates classCandidates;
    @Mock
    ResponsibilityCandidates responsibilityCandidates;
    @Mock
    AttributeCandidates attributeCandidates;
    @Mock
    CollaboratorCandidates collaboratorCandidates;

    // ============ the seed node ============

    @Test
    void aLoneSeedBecomesTheOnlyNodeWithItsOwnCasing() {
        final ClassCdd pet = classCandidate("Pet");

        final CandidateGraphModel model = CandidateGraphModel.of(List.of(pet), 1, List.of(pet));

        assertThat(model.nodeLemmas()).containsExactly("pet");
        assertThat(model.displayName()).containsEntry("pet", "Pet");
        assertThat(model.dependencies()).isEmpty();
        assertThat(model.subdomainByLemma()).containsEntry("pet", "");
        assertThat(model.usedPosCodes()).isEmpty();
    }

    @Test
    void theSeedNameIsKeptWhenItIsNotInTheSnapshot() {
        // a transient candidate that the repository snapshot does not contain
        final ClassCdd pet = classCandidate("Pet");

        final CandidateGraphModel model = CandidateGraphModel.of(List.of(pet), 1, List.of());

        assertThat(model.nodeLemmas()).containsExactly("pet");
        assertThat(model.displayName()).containsEntry("pet", "Pet");
    }

    // ============ the neighbourhood ============

    @Test
    void keepsOnlyDependenciesWhoseBothEndsAreCandidateWords() {
        final ClassCdd customer = classCandidate("Customer");
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN)));                 // both candidate words
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.DET, "customer", PartOfSpeechType.NN,
                "the", PartOfSpeechType.DT)));                      // 'the' is not a candidate
        final ResponsibilityCdd purchase = actionCandidate("Purchase");

        final CandidateGraphModel model = CandidateGraphModel.of(
                List.of(customer), 1, List.of(customer, purchase));

        assertThat(model.dependencies()).hasSize(1);
        assertThat(model.nodeLemmas()).containsExactlyInAnyOrder("customer", "purchase");
    }

    @Test
    void honoursTheHopDepth() {
        final ClassCdd customer = classCandidate("Customer");
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN)));
        final ResponsibilityCdd purchase = actionCandidate("Purchase");
        purchase.getRuleMatches().add(ruleMatchWith(dep(TdType.OBJ, "purchase", PartOfSpeechType.VBZ,
                "order", PartOfSpeechType.NN)));
        final ClassCdd order = classCandidate("Order");
        final List<Candidate> all = List.of(customer, purchase, order);

        assertThat(CandidateGraphModel.of(List.of(customer), 1, all).nodeLemmas())
                .as("one hop reaches the direct neighbour only")
                .containsExactlyInAnyOrder("customer", "purchase");
        assertThat(CandidateGraphModel.of(List.of(customer), 2, all).nodeLemmas())
                .as("two hops reach the second-level neighbour too")
                .containsExactlyInAnyOrder("customer", "purchase", "order");
    }

    @Test
    void excludesAssociationArtifactCompoundsButKeepsTheirParticipants() {
        final ClassCdd coverage = classCandidate("Coverage");
        coverage.getRuleMatches().add(ruleMatchWith(dep(TdType.NSUBJ, "coverage", PartOfSpeechType.NN,
                "age", PartOfSpeechType.NN)));
        coverage.getRuleMatches().add(ruleMatchWith(dep(TdType.OBJ, "coverage_age", PartOfSpeechType.NN,
                "coverage", PartOfSpeechType.NN)));
        final AttributeCdd age = propertyCandidate("Age");
        final CollaboratorCdd compound = associationCandidate("Coverage_Age");

        final CandidateGraphModel model = CandidateGraphModel.of(
                List.of(coverage), 1, List.of(coverage, age, compound));

        assertThat(model.nodeLemmas()).containsExactlyInAnyOrder("coverage", "age");
        assertThat(model.nodeLemmas()).as("the synthetic compound is not a node")
                .doesNotContain("coverage_age");
    }

    @Test
    void fallsBackToTheSeedWordWhenTheSeedIsItselfAnArtifactCompound() {
        // the seed is an "X_Y" association compound, so every edge touching it is removed and the node
        // set would be empty — the graph must still render its own word
        final CollaboratorCdd compound = associationCandidate("Coverage_Age");
        compound.getRuleMatches().add(ruleMatchWith(dep(TdType.OBJ, "coverage_age", PartOfSpeechType.NN,
                "coverage", PartOfSpeechType.NN)));
        final ClassCdd coverage = classCandidate("Coverage");
        final AttributeCdd age = propertyCandidate("Age");

        final CandidateGraphModel model = CandidateGraphModel.of(
                List.of(compound), 1, List.of(coverage, age, compound));

        assertThat(model.nodeLemmas()).containsExactly("coverage_age");
        assertThat(model.displayName()).containsEntry("coverage_age", "Coverage_Age");
    }

    // ============ per-lemma decoration ============

    @Test
    void mapsAClassNodeToItsSubDomainAndEveryNodeToItsCandidate() {
        final ClassCdd store = classCandidate("Store");
        final SubDomain inventory = new SubDomain();
        inventory.setName("Inventory");
        store.setSubDomain(inventory);

        final CandidateGraphModel model = CandidateGraphModel.of(List.of(store), 1, List.of(store));

        assertThat(model.subdomainByLemma()).containsEntry("store", "Inventory");
        assertThat(model.candidateIdByLemma()).containsKey("store");
        assertThat(model.objectTypeByLemma()).containsEntry("store", "domox.ClassCdd");
    }

    @Test
    void marksTheLemmasOfApprovedCandidates() {
        final ClassCdd approved = classCandidate("Customer");
        final Review review = new Review();
        review.setStatus(ReviewStatus.APPROVED);
        approved.getReviews().add(review);
        final ClassCdd pending = classCandidate("Shop");

        final CandidateGraphModel model = CandidateGraphModel.of(
                List.of(approved), 1, List.of(approved, pending));

        assertThat(model.approvedLemmas()).containsExactly("customer");
    }

    @Test
    void maxCountIsTheLargestPerLemmaMatchCount() {
        final ClassCdd customer = classCandidate("Customer");
        final TypedDependency purchaseCustomer = dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN);
        // the per-lemma count is the number of rule matches recorded on the typed dependency itself
        purchaseCustomer.setRuleMatches(List.of(new RuleMatch(), new RuleMatch()));
        customer.getRuleMatches().add(ruleMatchWith(purchaseCustomer));
        final ResponsibilityCdd purchase = actionCandidate("Purchase");

        final CandidateGraphModel model = CandidateGraphModel.of(
                List.of(customer), 1, List.of(customer, purchase));

        assertThat(model.countByLemma()).containsEntry("customer", 2);
        assertThat(model.countByLemma()).containsEntry("purchase", 2);
        assertThat(model.maxCount()).isEqualTo(2);
    }

    @Test
    void maxCountFallsBackToTheMinimumWhenNoDependencyCarriesRuleMatches() {
        final ClassCdd pet = classCandidate("Pet");

        assertThat(CandidateGraphModel.of(List.of(pet), 1, List.of(pet)).maxCount())
                .isEqualTo(DiagramNodeSizing.MIN_MATCH_COUNT);
    }

    @Test
    void usedPosCodesUnionsTheCodesOfEveryDependencyEndpoint() {
        final ClassCdd customer = classCandidate("Customer");
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN)));
        final ResponsibilityCdd purchase = actionCandidate("Purchase");

        final CandidateGraphModel model = CandidateGraphModel.of(
                List.of(customer), 1, List.of(customer, purchase));

        assertThat(model.usedPosCodes()).containsExactlyInAnyOrder("NN", "VBZ");
    }

    // ============ the repository snapshot ============

    @Test
    void snapshotCollectsEveryCandidateType() {
        final ClassCdd c = classCandidate("Customer");
        final ResponsibilityCdd a = actionCandidate("Purchase");
        final AttributeCdd p = propertyCandidate("Price");
        final CollaboratorCdd l = associationCandidate("Owns");
        when(classCandidates.listAll()).thenReturn(List.of(c));
        when(responsibilityCandidates.listAll()).thenReturn(List.of(a));
        when(attributeCandidates.listAll()).thenReturn(List.of(p));
        when(collaboratorCandidates.listAll()).thenReturn(List.of(l));

        assertThat(CandidateGraphModel.snapshot(classCandidates, responsibilityCandidates,
                attributeCandidates, collaboratorCandidates)).containsExactly(c, a, p, l);
    }

    @Test
    void snapshotToleratesARepositoryReturningNull() {
        when(classCandidates.listAll()).thenReturn(null);
        when(responsibilityCandidates.listAll()).thenReturn(List.of(actionCandidate("Purchase")));
        when(attributeCandidates.listAll()).thenReturn(null);
        when(collaboratorCandidates.listAll()).thenReturn(null);

        assertThat(CandidateGraphModel.snapshot(classCandidates, responsibilityCandidates,
                attributeCandidates, collaboratorCandidates))
                .extracting(Candidate::getCandidateName)
                .containsExactly("Purchase");
    }

    // ============ helper factories ============

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

    private static AttributeCdd propertyCandidate(final String name) {
        final AttributeCdd p = new AttributeCdd();
        p.setCandidateName(name);
        p.setCandidateType("AttributeCdd");
        return p;
    }

    private static CollaboratorCdd associationCandidate(final String name) {
        final CollaboratorCdd a = new CollaboratorCdd();
        a.setCandidateName(name);
        a.setCandidateType("CollaboratorCdd");
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
