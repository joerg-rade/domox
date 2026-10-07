package domox.dom.crc;

import domox.diagram.DiagramBuilder;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import org.apache.causeway.applib.value.Blob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link DomainModelDiagrams#renderDiagram(DomainModel)}:
 * <ul>
 *   <li>the diagram is seeded from the {@link DomainModel}'s <em>approved</em> candidates (those
 *       carrying at least one {@link ReviewStatus#APPROVED} review), expanded</li>
 *   <li>{@link DomainModel#getHopDepth()} hops deep (default {@code 1}) through the
 *       candidate-connection graph, keeping only typed dependencies whose governor <em>and</em>
 *       dependent lemma are both candidate words.</li>
 * </ul>
 * The Graphviz (DOT) is generated straight from the persisted {@link TypedDependency}s — no
 * SentenceTO/TokenTO reconstruction is involved.
 */
@ExtendWith(MockitoExtension.class)
class DomainModelDiagramsTest {

    @Mock
    DiagramBuilder diagramBuilder;

    DomainModelDiagrams domainModelDiagrams;

    @BeforeEach
    void setUp() {
        domainModelDiagrams = new DomainModelDiagrams(diagramBuilder);
    }

    @Test
    void rendersDiagramFromApprovedSeedsAtDefaultHopDepth() {
        // given — two approved candidates (customer, purchase) plus a non-approved neighbour
        // ('order') reachable through purchase, and an unrelated non-approved candidate ('catalog').
        // The default hop depth of 1 draws the approved seeds together with their direct neighbours.
        final DomainModel model = new DomainModel();

        final ClassCdd customer = candidateOf(new ClassCdd(), "Customer",
                dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ, "customer", PartOfSpeechType.NN), true);
        final ActionCdd purchase = candidateOf(new ActionCdd(), "Purchase",
                dep(TdType.OBJ, "purchase", PartOfSpeechType.VBZ, "order", PartOfSpeechType.NN), true);
        final ClassCdd order = candidateOf(new ClassCdd(), "Order", null, false);
        final ClassCdd catalog = candidateOf(new ClassCdd(), "Catalog", null, false);

        model.classList.add(customer);
        model.actionList.add(purchase);
        model.classList.add(order);
        model.classList.add(catalog);

        when(diagramBuilder.buildLexicalGraphDiagram(anyString())).thenReturn(new byte[]{1, 2, 3});

        // when
        final Blob result = domainModelDiagrams.renderDiagram(model);

        // then — a PDF containing the approved seeds and the neighbour connected through them
        assertNotNull(result);
        assertTrue(result.getMimeType().toString().contains("application/pdf"));

        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(diagramBuilder).buildLexicalGraphDiagram(captor.capture());
        final String dot = captor.getValue();

        assertTrue(dot.contains("\"customer\""), "approved seed 'customer' should be kept");
        assertTrue(dot.contains("\"purchase\""), "approved seed 'purchase' should be kept");
        assertTrue(dot.contains("\"order\""),
                "non-approved candidate directly connected to an approved seed ('order') should be drawn");
        assertFalse(dot.contains("\"catalog\""),
                "candidate unrelated to the approved seeds ('catalog') should be dropped");
    }

    @Test
    void increasingHopDepthDrawsSecondHopNeighbours() {
        // given — an approved seed 'a' connected to 'b', which is connected to 'c'.  At the default
        // depth of 1 only the direct neighbour 'b' is drawn; raising the hop depth to 2 brings 'c'.
        final DomainModel model = new DomainModel();

        final ClassCdd a = candidateOf(new ClassCdd(), "A",
                dep(TdType.NSUBJ, "b", PartOfSpeechType.NN, "a", PartOfSpeechType.NN), true);
        final ClassCdd b = candidateOf(new ClassCdd(), "B",
                dep(TdType.NSUBJ, "c", PartOfSpeechType.NN, "b", PartOfSpeechType.NN), false);
        final ClassCdd c = candidateOf(new ClassCdd(), "C", null, false);

        model.classList.add(a);
        model.classList.add(b);
        model.classList.add(c);

        when(diagramBuilder.buildLexicalGraphDiagram(anyString())).thenReturn(new byte[]{1, 2, 3});

        // when — first render at the default hop depth of 1
        domainModelDiagrams.renderDiagram(model);
        ArgumentCaptor<String> depth1Captor = ArgumentCaptor.forClass(String.class);
        verify(diagramBuilder).buildLexicalGraphDiagram(depth1Captor.capture());
        final String depth1 = depth1Captor.getValue();
        assertTrue(depth1.contains("\"a\""), "approved seed 'a' should be kept");
        assertTrue(depth1.contains("\"b\""), "first-hop neighbour 'b' should be drawn at depth 1");
        assertFalse(depth1.contains("\"c\""), "second-hop neighbour 'c' should NOT be drawn at depth 1");

        // when — the user increases the hop depth to 2 and re-renders
        model.setHopDepth(2);
        domainModelDiagrams.renderDiagram(model);
        // a fresh captor (not the one reused above) so it only captures this render's DOT
        final ArgumentCaptor<String> depth2Captor = ArgumentCaptor.forClass(String.class);
        verify(diagramBuilder, times(2)).buildLexicalGraphDiagram(depth2Captor.capture());
        final String depth2 = depth2Captor.getValue();
        assertTrue(depth2.contains("\"c\""), "second-hop neighbour 'c' should be drawn at depth 2");
    }

    @Test
    void returnsNullWhenModelIsNull() {
        assertNull(domainModelDiagrams.renderDiagram(null), "null model -> no diagram");
    }

    @Test
    void returnsNullWhenModelHasNoCandidates() {
        assertNull(domainModelDiagrams.renderDiagram(new DomainModel()),
                "a model with no candidates -> no diagram");
    }

    @Test
    void returnsNullWhenNoCandidateIsApproved() {
        // given — candidates exist but none carries an APPROVED review
        final DomainModel model = new DomainModel();
        model.classList.add(candidateOf(new ClassCdd(), "Customer", null, false));
        model.actionList.add(candidateOf(new ActionCdd(), "Purchase", null, false));

        // then — nothing approved to seed from
        assertNull(domainModelDiagrams.renderDiagram(model),
                "no approved candidate -> no diagram");
    }

    @Test
    void rendersLegendOnlyDiagramForLoneApprovedCandidateWithNoMatchingDependencies() {
        // given — a single approved candidate whose only dependency links to a word that is not
        // itself a candidate; no edge survives between two candidate words.
        final DomainModel model = new DomainModel();
        final ClassCdd customer = candidateOf(new ClassCdd(), "Customer",
                dep(TdType.OBJ, "purchase", PartOfSpeechType.VBZ, "order", PartOfSpeechType.NN), true);
        model.classList.add(customer);

        when(diagramBuilder.buildLexicalGraphDiagram(anyString())).thenReturn(new byte[]{1, 2, 3});

        // when
        final Blob result = domainModelDiagrams.renderDiagram(model);

        // then — generation still succeeds (the DOT is a legend-only graph) and yields a PDF blob
        assertNotNull(result, "a degenerate single-approved model should still yield a diagram blob");
        verify(diagramBuilder).buildLexicalGraphDiagram(anyString());
    }

    /** Builds a candidate with the given name and an optional typed dependency, attaching an
     *  {@link ReviewStatus#APPROVED} review when {@code approved}. */
    private static <C extends Candidate> C candidateOf(final C candidate,
                                                       final String name,
                                                       final TypedDependency td,
                                                       final boolean approved) {
        candidate.setCandidateName(name);
        candidate.setCandidateType(Candidate.class + "");
        if (td != null) {
            candidate.getRuleMatches().add(ruleMatchWith(td));
        }
        if (approved) {
            final Review review = new Review();
            review.setStatus(ReviewStatus.APPROVED);
            candidate.addReview(review);
        }
        return candidate;
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