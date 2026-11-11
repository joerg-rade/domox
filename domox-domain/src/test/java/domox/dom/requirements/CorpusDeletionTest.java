package domox.dom.requirements;

import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.rules.RuleMatches;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level guardrail for {@link CorpusDeletion} — the deletion <em>policy</em>.
 *
 * <p>What matters here is the <b>order</b>: the candidates of the shared {@link DomainModel} are
 * removed child-first (associations → responsibilities → attributes → classes), then the corpus's
 * documents, then the corpus itself, and the rule matches are purged last.  Removing the model before
 * its still-referencing children is what tripped the DB foreign keys
 * ({@code FK_ResponsibilityCdd_DOMAINMODEL_ID}) before this ordering was introduced.</p>
 *
 * <p>The end-to-end behaviour (an actual flush against Postgres) is covered by
 * {@code DeleteCorpusIntegTest} in {@code domox-webapp}.</p>
 */
@ExtendWith(MockitoExtension.class)
class CorpusDeletionTest {

    @Mock RepositoryService repositoryService;
    @Mock RuleMatches ruleMatches;

    private CorpusDeletion corpusDeletion;

    @BeforeEach
    void setUp() {
        corpusDeletion = new CorpusDeletion(repositoryService, ruleMatches);
    }

    @Test
    void deleteUnwindsCandidatesChildFirstThenDocumentsThenTheCorpus() {
        // given — a shared model holding one candidate of each type
        final DomainModel model = new DomainModel();
        final ClassCdd clazz = new ClassCdd();
        final AttributeCdd attribute = new AttributeCdd("age", "int");
        final ResponsibilityCdd responsibility = new ResponsibilityCdd();
        final CollaboratorCdd collaborator = new CollaboratorCdd("Customer_Store", clazz, clazz);
        model.classList.add(clazz);
        model.attributeList.add(attribute);
        model.responsibilityList.add(responsibility);
        model.collaboratorList.add(collaborator);

        final Corpus corpus = mock(Corpus.class);
        final Document document = mock(Document.class);
        when(corpus.getDomainModel()).thenReturn(model);
        when(corpus.getDocuments()).thenReturn(List.of(document));

        // when
        corpusDeletion.deleteCorpus(corpus);

        // then — the corpus releases the model (so its cascade cannot fire early) ...
        verify(corpus).setDomainModel(null);

        // ... every candidate is removed child-before-parent, then the document, then the corpus ...
        final InOrder inOrder = inOrder(repositoryService);
        inOrder.verify(repositoryService).remove(collaborator);
        inOrder.verify(repositoryService).remove(responsibility);
        inOrder.verify(repositoryService).remove(attribute);
        inOrder.verify(repositoryService).remove(clazz);
        inOrder.verify(repositoryService).remove(document);
        inOrder.verify(repositoryService).remove(corpus);

        // ... and the rule-match records go last
        verify(ruleMatches).deleteAll();
    }

    /** Every element of a collection is registered — not just the first. */
    @Test
    void deleteRemovesEveryCandidateOfAType() {
        final DomainModel model = new DomainModel();
        final ClassCdd one = new ClassCdd();
        final ClassCdd two = new ClassCdd();
        model.classList.add(one);
        model.classList.add(two);

        final Corpus corpus = mock(Corpus.class);
        when(corpus.getDomainModel()).thenReturn(model);
        when(corpus.getDocuments()).thenReturn(List.of());

        corpusDeletion.deleteCorpus(corpus);

        verify(repositoryService).remove(one);
        verify(repositoryService).remove(two);
    }

    /** A corpus with no model is still removed, and the rule matches are still purged. */
    @Test
    void deleteWithoutAModelStillRemovesTheCorpusAndPurgesRuleMatches() {
        final Corpus corpus = mock(Corpus.class);
        when(corpus.getDocuments()).thenReturn(List.of());

        corpusDeletion.deleteCorpus(corpus);

        verify(corpus, never()).setDomainModel(any());
        verify(repositoryService).remove(corpus);
        verify(ruleMatches).deleteAll();
    }

    @Test
    void choices0DeleteCorpusOffersEveryCorpus() {
        final Corpus corpus = mock(Corpus.class);
        when(repositoryService.allInstances(Corpus.class)).thenReturn(List.of(corpus));

        assertThat(corpusDeletion.choices0DeleteCorpus()).containsExactly(corpus);
    }
}
