package domox.dom.rqm;

import domox.diagram.DiagramBuilder;
import domox.dom.AbstractEntity;
import domox.dom.crc.ActionCdd;
import domox.dom.crc.ClassCdd;
import domox.dom.crc.DomainModel;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.Sentences;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import domox.dom.rules.RuleMatches;
import org.apache.causeway.applib.services.message.MessageService;
import org.apache.causeway.applib.services.repository.RepositoryService;
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
 * Verifies {@link Documents#renderDiagram(Document)}:
 * <ul>
 *   <li>typed dependencies are restricted to those whose governor and dependent lemma
 *       are both candidate words, and</li>
 *   <li>class candidates only count when their rule-match count is at least the
 *       default lexical threshold.</li>
 * </ul>
 * The Graphviz (DOT) is generated straight from the persisted {@link TypedDependency}s — no
 * SentenceTO/TokenTO reconstruction is involved.
 */
@ExtendWith(MockitoExtension.class)
class DocumentsLexicalDiagramTest {

    @Mock
    RepositoryService mockRepositoryService;
    @Mock
    Sentences sentences;
    @Mock
    DiagramBuilder diagramBuilder;
    @Mock
    MessageService messageService;
    @Mock
    RuleMatches ruleMatches;

    Documents documents;

    @BeforeEach
    void setUp() {
        documents = new Documents(mockRepositoryService, sentences, diagramBuilder, messageService, ruleMatches);
    }

    @Test
    void restrictsDependenciesToCandidateWordsAndAppliesClassThreshold() {
        // given — a domain model with a strong class, a weak class and an action candidate
        final DomainModel model = new DomainModel();
        final ClassCdd strongClass = classCandidate("Customer", 5);
        final ClassCdd weakClass = classCandidate("Currency", 0); // below the default threshold of 1
        model.classList.add(strongClass);
        model.classList.add(weakClass);
        final ActionCdd purchase = new ActionCdd();
        purchase.setCandidateName("Purchase");
        model.actionList.add(purchase);

        final Document document = new Document();
        document.setTitle("PetShop");
        document.setDomainModel(model);
        assignId(document, 1001L); // renderDiagram logs the persisted id

        // a persisted sentence whose typed dependencies carry the lemma/POS data
        final Sentence sentence = new Sentence();
        sentence.addTypedDependency(dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN));   // both candidate words -> kept
        sentence.addTypedDependency(dep(TdType.AMOD, "customer", PartOfSpeechType.NN,
                "currency", PartOfSpeechType.NN));   // weak class (0 matches < default 1) -> dropped
        sentence.addTypedDependency(dep(TdType.DET, "customer", PartOfSpeechType.NN,
                "the", PartOfSpeechType.DT));        // no candidate -> dropped
        when(sentences.findByDocument(document)).thenReturn(List.of(sentence));

        when(diagramBuilder.buildLexicalGraphDiagram(anyString())).thenReturn(new byte[]{1, 2, 3});

        // when — renderDiagram uses the fixed DEFAULT_LEXICAL_THRESHOLD (1)
        final Blob result = documents.renderDiagram(document);

        // then — diagram built from the candidate-restricted dependencies
        assertNotNull(result);
        assertTrue(result.getMimeType().toString().contains("application/pdf"));

        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(diagramBuilder).buildLexicalGraphDiagram(captor.capture());
        final String dot = captor.getValue();

        assertTrue(dot.contains("\"customer\" [label=\"customer\\n«NN»\", fillcolor=\"#3498DB\""),
                "strong class word should be kept");
        assertTrue(dot.contains("\"purchase\" [label=\"purchase\\n«VBZ»\", fillcolor=\"#E74C3C\""),
                "action candidate word should be kept");
        assertFalse(dot.contains("currency"),
                "weak class word should be dropped by the default threshold");
        assertFalse(dot.contains("«DT»"),
                "non-candidate word ('the', a determiner) should be dropped");
    }

    @Test
    void returnsNullWhenDocumentHasNoDomainModel() {
        final Document document = new Document();
        document.setTitle("Unanalysed");

        final Blob result = documents.renderDiagram(document);

        assertNull(result, "no model -> no diagram");
    }

    private static ClassCdd classCandidate(final String name, final int ruleMatches) {
        final ClassCdd cc = new ClassCdd();
        cc.setCandidateName(name);
        for (int i = 0; i < ruleMatches; i++) {
            cc.getRuleMatches().add(new RuleMatch());
        }
        return cc;
    }

    /**
     * {@link AbstractEntity#getId()} is generated (no setter), so give a transient test
     * entity a surrogate id — {@link Documents#renderDiagram(Document)} logs it.
     */
    private static void assignId(final Document document, final long id) {
        try {
            final java.lang.reflect.Field field = AbstractEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(document, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not assign id " + id + " to transient Document", e);
        }
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
