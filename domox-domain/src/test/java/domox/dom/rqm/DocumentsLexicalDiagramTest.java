package domox.dom.rqm;

import domox.diagram.DiagramBuilder;
import domox.dom.crc.ActionCdd;
import domox.dom.crc.ClassCdd;
import domox.dom.crc.DomainModel;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.Sentences;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
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
 * Verifies {@link Documents#renderLexicalDiagram(Document, int)}:
 * <ul>
 *   <li>typed dependencies are restricted to those whose governor and dependent lemma
 *       are both candidate words, and</li>
 *   <li>class candidates only count when their rule-match count is at least the threshold.</li>
 * </ul>
 * The PlantUML is generated straight from the persisted {@link TypedDependency}s — no
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

    Documents documents;

    @BeforeEach
    void setUp() {
        documents = new Documents(mockRepositoryService, sentences, diagramBuilder, messageService);
    }

    @Test
    void restrictsDependenciesToCandidateWordsAndAppliesClassThreshold() {
        // given — a domain model with a strong class, a weak class and an action candidate
        final DomainModel model = new DomainModel();
        final ClassCdd strongClass = classCandidate("Customer", 5);
        final ClassCdd weakClass = classCandidate("Currency", 1); // below threshold 2
        model.classList.add(strongClass);
        model.classList.add(weakClass);
        final ActionCdd purchase = new ActionCdd();
        purchase.setCandidateName("Purchase");
        model.actionList.add(purchase);

        final Document document = new Document();
        document.setTitle("PetShop");
        document.setDomainModel(model);

        // a persisted sentence whose typed dependencies carry the lemma/POS data
        final Sentence sentence = new Sentence();
        sentence.addTypedDependency(dep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN));   // both candidate words -> kept
        sentence.addTypedDependency(dep(TdType.AMOD, "customer", PartOfSpeechType.NN,
                "currency", PartOfSpeechType.NN));   // weak class (< threshold) -> dropped
        sentence.addTypedDependency(dep(TdType.DET, "customer", PartOfSpeechType.NN,
                "the", PartOfSpeechType.DT));        // no candidate -> dropped
        when(sentences.findByDocument(document)).thenReturn(List.of(sentence));

        when(diagramBuilder.buildLexicalGraphDiagram(anyString())).thenReturn(new byte[]{1, 2, 3});

        // when
        final Blob result = documents.renderLexicalDiagram(document, 2);

        // then — diagram built from the candidate-restricted dependencies
        assertNotNull(result);
        assertTrue(result.getMimeType().toString().contains("application/pdf"));

        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(diagramBuilder).buildLexicalGraphDiagram(captor.capture());
        final String puml = captor.getValue();

        assertTrue(puml.contains("component [customer] as customer <<NN>>"),
                "strong class word should be kept");
        assertTrue(puml.contains("component [purchase] as purchase <<VBZ>>"),
                "action candidate word should be kept");
        assertFalse(puml.contains("component [currency]"),
                "weak class word should be dropped by threshold");
        assertFalse(puml.contains("as the <<DT>>"),
                "non-candidate word should be dropped");
    }

    @Test
    void returnsNullWhenDocumentHasNoDomainModel() {
        final Document document = new Document();
        document.setTitle("Unanalysed");

        final Blob result = documents.renderLexicalDiagram(document, 1);

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
