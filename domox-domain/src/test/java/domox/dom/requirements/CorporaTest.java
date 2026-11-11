package domox.dom.requirements;

import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level guardrail for {@link Corpora} — the corpus aggregate's CRUD service.
 * <p>
 * The entity's own collections are {@code null} on a fresh instance (they are initialised by JPA), so
 * the collaborator is mocked rather than the entity.  The end-to-end path is covered by
 * {@code DeleteCorpusIntegTest} / {@code CorpusOntologyDiagramIntegTest} in {@code domox-webapp}.
 */
@ExtendWith(MockitoExtension.class)
class CorporaTest {

    @Mock RepositoryService repositoryService;
    @Mock FactoryService factoryService;

    private Corpora corpora;

    @BeforeEach
    void setUp() {
        corpora = new Corpora(repositoryService, factoryService);
    }

    @Test
    void listAllReturnsEveryPersistedCorpus() {
        final Corpus a = corpus("A");
        final Corpus b = corpus("B");
        when(repositoryService.allInstances(Corpus.class)).thenReturn(List.of(a, b));

        assertThat(corpora.listAll()).containsExactly(a, b);
    }

    /** {@code create} stamps the title and an analysed-at timestamp, then persists. */
    @Test
    void createStampsTitleAndTimestampAndPersists() {
        final Corpus fresh = new Corpus();
        when(factoryService.detachedEntity(Corpus.class)).thenReturn(fresh);

        final Corpus created = corpora.create("Pet Shop Use Cases");

        assertThat(created).isSameAs(fresh);
        assertThat(created.getTitle()).isEqualTo("Pet Shop Use Cases");
        assertThat(created.getAnalyzedAt()).isNotNull();
        verify(repositoryService).persistAndFlush(fresh);
    }

    @Test
    void findByTitleMatchesExactlyAndKeepsDuplicates() {
        when(repositoryService.allInstances(Corpus.class))
                .thenReturn(List.of(corpus("A"), corpus("B"), corpus("A")));

        assertThat(corpora.findByTitle("A")).hasSize(2)
                .allMatch(c -> "A".equals(c.getTitle()));
        assertThat(corpora.findByTitle("no-such-title")).isEmpty();
    }

    private static Corpus corpus(final String title) {
        final Corpus corpus = new Corpus();
        corpus.setTitle(title);
        return corpus;
    }
}
