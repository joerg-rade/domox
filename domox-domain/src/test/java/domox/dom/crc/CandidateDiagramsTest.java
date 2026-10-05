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
    DiagramBuilder diagramBuilder;
    @Mock
    ClassCandidates classCandidates;
    @Mock
    ActionCandidates actionCandidates;
    @Mock
    PropertyCandidates propertyCandidates;
    @Mock
    AssociationCandidates associationCandidates;

    CandidateDiagrams candidateDiagrams;

    @BeforeEach
    void setUp() {
        candidateDiagrams = new CandidateDiagrams(
                diagramBuilder, classCandidates, actionCandidates, propertyCandidates, associationCandidates);
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

        final ActionCdd purchase = new ActionCdd();
        purchase.setCandidateName("Purchase");
        when(actionCandidates.listAll()).thenReturn(List.of(purchase));
        final PropertyCdd currency = new PropertyCdd();
        currency.setCandidateName("Currency");
        when(propertyCandidates.listAll()).thenReturn(List.of(currency));
        when(classCandidates.listAll()).thenReturn(List.of(customer));

        when(diagramBuilder.buildLexicalGraphDiagram(anyString())).thenReturn(new byte[]{1, 2, 3});

        // when
        final Blob result = candidateDiagrams.renderDiagram(customer);

        // then — diagram built from the candidate-restricted dependencies
        assertNotNull(result);
        assertTrue(result.getMimeType().toString().contains("application/pdf"));

        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(diagramBuilder).buildLexicalGraphDiagram(captor.capture());
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
        // "order" is two hops from "customer" and must NOT surface with MAX_HOP_DEPTH = 1.
        final ClassCdd customer = new ClassCdd();
        customer.setCandidateName("Customer");
        customer.setCandidateType("ClassCdd");
        customer.getRuleMatches().add(ruleMatchWith(dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN)));      // customer <-> purchase (direct)

        final ActionCdd purchase = new ActionCdd();
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
        when(actionCandidates.listAll()).thenReturn(List.of(purchase));
        when(diagramBuilder.buildLexicalGraphDiagram(anyString())).thenReturn(new byte[]{1, 2, 3});

        // when
        candidateDiagrams.renderDiagram(customer);

        // then — only the direct neighbour "purchase" is drawn; second-level "order" is not,
        // and neither is the non-candidate "receipt"
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(diagramBuilder).buildLexicalGraphDiagram(captor.capture());
        final String dot = captor.getValue();

        assertTrue(dot.contains("\"customer\""), "the candidate itself should be kept");
        assertTrue(dot.contains("\"purchase\""), "directly connected candidate 'purchase' should be kept");
        assertFalse(dot.contains("\"order\""),
                "second-level candidate 'order' should be dropped at MAX_HOP_DEPTH = 1");
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