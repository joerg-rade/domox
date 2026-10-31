package domox.dom.crc.diagram;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.service.AttributeCandidates;
import domox.dom.crc.service.ClassCandidates;
import domox.dom.crc.service.CollaboratorCandidates;
import domox.dom.crc.service.ResponsibilityCandidates;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import domox.port.DiagramPort;
import org.apache.causeway.applib.value.Blob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link CandidateDiagrams#renderDiagram(Candidate)}:
 * <ul>
 *   <li>typed dependencies are drawn from the candidate's own {@link RuleMatch}es (a one-hop
 *       breadth-first expansion), and</li>
 *   <li>only dependencies whose governor and dependent lemma are <em>both</em> candidate words
 *       are kept — the candidate plus every related candidate looked up from the candidate
 *       lists.</li>
 * </ul>
 * The Graphviz (DOT) is generated straight from the persisted {@link TypedDependency}s — no
 * SentenceTO/TokenTO reconstruction is involved.
 */
@ExtendWith(MockitoExtension.class)
class CandidateDiagramsTest {

    @Mock
    DiagramPort diagramPort;
    @Mock
    ClassCandidates classCandidates;
    @Mock
    ResponsibilityCandidates responsibilityCandidates;
    @Mock
    AttributeCandidates attributeCandidates;
    @Mock
    CollaboratorCandidates collaboratorCandidates;

    CandidateDiagrams candidateDiagrams;

    @BeforeEach
    void setUp() {
        candidateDiagrams = new CandidateDiagrams(
                diagramPort, classCandidates, responsibilityCandidates, attributeCandidates, collaboratorCandidates);
    }

    @Test
    void restrictsDependenciesToCandidateWords() {
        // given — a ClassCdd candidate "Customer" whose rule matches carry typed dependencies
        final ClassCdd customer = new ClassCdd();
        customer.setCandidateName("Customer");
        customer.setCandidateType("ClassCdd");
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN)));          // both candidate words -> kept
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.AMOD, "customer", PartOfSpeechType.NN,
                "currency", PartOfSpeechType.NN)));          // both candidate words -> kept
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.OBJ, "purchase", PartOfSpeechType.VBZ,
                "catalog", PartOfSpeechType.NN)));             // 'catalog' is a content word but NOT a candidate -> dropped

        final ResponsibilityCdd purchase = new ResponsibilityCdd();
        purchase.setCandidateName("Purchase");
        when(responsibilityCandidates.listAll()).thenReturn(List.of(purchase));
        final AttributeCdd currency = new AttributeCdd();
        currency.setCandidateName("Currency");
        when(attributeCandidates.listAll()).thenReturn(List.of(currency));
        when(classCandidates.listAll()).thenReturn(List.of(customer));

        when(diagramPort.buildLexicalGraphDiagram(anyString())).thenReturn(new byte[]{1, 2, 3});

        // when
        final Blob result = candidateDiagrams.renderDiagram(customer);

        // then — diagram built from the candidate-restricted dependencies
        assertNotNull(result);
        assertTrue(result.mimeType().toString().contains("application/pdf"));

        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(diagramPort).buildLexicalGraphDiagram(captor.capture());
        final String dot = captor.getValue();

        assertTrue(dot.contains("\"customer\""), "the candidate itself should be kept");
        assertTrue(dot.contains("\"purchase\""), "related candidate 'purchase' should be kept");
        assertTrue(dot.contains("\"currency\""), "related candidate 'currency' should be kept");
        assertFalse(dot.contains("\"catalog\""),
                "content word that is not a candidate ('catalog') should be dropped");
    }

    @Test
    void doesNotExpandToSecondLevelNeighbours() {
        // given — "customer" connects to "purchase", and "purchase" connects to "order";
        // "order" is two hops from "customer" and must NOT surface at the default hop depth (1).
        final ClassCdd customer = new ClassCdd();
        customer.setCandidateName("Customer");
        customer.setCandidateType("ClassCdd");
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN)));      // customer <-> purchase (direct)

        final ResponsibilityCdd purchase = new ResponsibilityCdd();
        purchase.setCandidateName("Purchase");
        purchase.setCandidateType("ActionCdd");
        purchase.getRuleMatches().add(ruleMatchWith(dep(TdType.OBJ, "purchase", PartOfSpeechType.VBZ,
                "order", PartOfSpeechType.NN)));          // purchase <-> order (one hop further)
        purchase.getRuleMatches().add(ruleMatchWith(dep(TdType.OBJ, "purchase", PartOfSpeechType.VBZ,
                "receipt", PartOfSpeechType.NN)));        // 'receipt' not a candidate -> dropped

        final ClassCdd order = new ClassCdd();
        order.setCandidateName("Order");
        order.setCandidateType("ClassCdd");

        when(classCandidates.listAll()).thenReturn(List.of(customer, order));
        when(responsibilityCandidates.listAll()).thenReturn(List.of(purchase));
        when(diagramPort.buildLexicalGraphDiagram(anyString())).thenReturn(new byte[]{1, 2, 3});

        // when
        candidateDiagrams.renderDiagram(customer);

        // then — only the direct neighbour "purchase" is drawn; second-level "order" is not,
        // and neither is the non-candidate "receipt"
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(diagramPort).buildLexicalGraphDiagram(captor.capture());
        final String dot = captor.getValue();

        assertTrue(dot.contains("\"customer\""), "the candidate itself should be kept");
        assertTrue(dot.contains("\"purchase\""), "directly connected candidate 'purchase' should be kept");
        assertFalse(dot.contains("\"order\""),
                "second-level candidate 'order' should be dropped at the default hop depth (1)");
        assertFalse(dot.contains("\"receipt\""),
                "content word that is not a candidate ('receipt') should be dropped at any depth");
    }

    @Test
    void rebuildsDeeperDiagramWhenHopDepthIsRaised() {
        // given — "customer" -> "purchase" -> "order"; raising the attribute to 2 hops should
        // pull the second-level neighbour "order" into the rebuilt diagram.
        final ClassCdd customer = new ClassCdd();
        customer.setCandidateName("Customer");
        customer.setCandidateType("ClassCdd");
        customer.setHopDepth(2); // change triggers a deeper rebuild
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN)));      // customer <-> purchase (direct)

        final ResponsibilityCdd purchase = new ResponsibilityCdd();
        purchase.setCandidateName("Purchase");
        purchase.setCandidateType("ActionCdd");
        purchase.getRuleMatches().add(ruleMatchWith(dep(TdType.OBJ, "purchase", PartOfSpeechType.VBZ,
                "order", PartOfSpeechType.NN)));          // purchase <-> order (one hop further)
        purchase.getRuleMatches().add(ruleMatchWith(dep(TdType.OBJ, "purchase", PartOfSpeechType.VBZ,
                "receipt", PartOfSpeechType.NN)));        // 'receipt' not a candidate -> dropped

        final ClassCdd order = new ClassCdd();
        order.setCandidateName("Order");
        order.setCandidateType("ClassCdd");

        when(classCandidates.listAll()).thenReturn(List.of(customer, order));
        when(responsibilityCandidates.listAll()).thenReturn(List.of(purchase));
        when(diagramPort.buildLexicalGraphDiagram(anyString())).thenReturn(new byte[]{1, 2, 3});

        // when
        candidateDiagrams.renderDiagram(customer);

        // then — the second-level neighbour surfaces because the hop-depth attribute was raised,
        // while the non-candidate "receipt" is still dropped
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(diagramPort).buildLexicalGraphDiagram(captor.capture());
        final String dot = captor.getValue();

        assertTrue(dot.contains("\"order\""),
                "second-level candidate 'order' should surface once the hop depth is raised to 2");
        assertFalse(dot.contains("\"receipt\""),
                "content word that is not a candidate ('receipt') should be dropped at any depth");
    }

    @Test
    void returnsNullWhenCandidateIsNull() {
        final Blob result = candidateDiagrams.renderDiagram(null);
        assertNull(result, "null candidate -> no diagram");
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