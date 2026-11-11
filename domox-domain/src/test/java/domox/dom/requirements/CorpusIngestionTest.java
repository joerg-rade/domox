package domox.dom.requirements;

import domox.dom.UcResources;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.ClassType;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.service.ClassArchetypeClassifier;
import domox.dom.crc.service.DomainModels;
import domox.dom.nlp.Sentence;
import domox.dom.rules.CandidateResolver;
import domox.dom.rules.RuleMatches;
import domox.dom.rules.TypedDependencyRule;
import domox.port.DocumentTO;
import domox.port.NlpPort;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.value.Clob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level guardrail for {@link CorpusIngestion} — the requirements-ingestion pipeline
 * (NLP parse → rule matching → candidate creation → archetype classification → late-binding).
 *
 * <p>The document <em>file</em> is real (a bundled {@code UC*.md} resource, read through
 * {@code FileUtil}), while every collaborator is mocked, so the test pins the <em>orchestration</em>:
 * which phases run, and that a duplicate short-circuits before any of them.  The full analysis over
 * all 15 documents is covered by {@code CorpusCandidateDedupIntegTest} in {@code domox-webapp}.</p>
 */
@ExtendWith(MockitoExtension.class)
class CorpusIngestionTest {

    /** A bundled use-case document — {@code loadUcDocument} reads it from the classpath. */
    private static final String UC_FILE = "UC01_SellingPetProducts.md";

    private static final String CORPUS_TITLE = "Pet Shop Use Cases";

    @Mock RepositoryService repositoryService;
    @Mock Corpora corpora;
    @Mock Documents documents;
    @Mock DomainModels domainModels;
    @Mock ClassArchetypeClassifier archetypeClassifier;
    @Mock CandidateResolver candidateResolver;
    @Mock NlpPort nlpPort;
    @Mock RuleMatches ruleMatches;

    private CorpusIngestion ingestion;

    @BeforeEach
    void setUp() {
        ingestion = new CorpusIngestion(repositoryService, corpora, documents, List.of(),
                domainModels, archetypeClassifier, candidateResolver, nlpPort, ruleMatches);
    }

    @Test
    void loadingADocumentRunsTheWholePipeline() {
        final DomainModel model = new DomainModel();
        final Corpus corpus = stubCorpusLookup();
        when(corpus.getDomainModel()).thenReturn(model);
        final Document document = analysedDocument("The customer purchases a product.", corpus);
        stubPipeline(document, model, List.of());

        final boolean loaded = ingestion.loadUcDocument(UC_FILE);

        assertThat(loaded).isTrue();
        // the parsed text is the document's own content
        verify(nlpPort).parse("The customer purchases a product.");
        verify(documents).createSentences(eq(document), any());
        // candidates are created for THIS document, into the corpus's shared model
        verify(ruleMatches).createCandidatesFrom(anyList(), eq(model));
        verify(candidateResolver).resolve(eq(model), anyList());
        verify(repositoryService).persistAndFlush(document);
        verify(repositoryService).persistAndFlush(corpus);
        // the document is wired into the corpus and the model
        verify(document).setCorpus(corpus);
        verify(document).setFileName(UC_FILE);
        verify(corpus).addDocument(document);
        verify(document).setDomainModel(model);
        verify(document).setSentences(List.of());
    }

    /** Every TDR rule is applied to every sentence of the document. */
    @Test
    void everyRuleIsAppliedToEverySentenceOfTheDocument() {
        final TypedDependencyRule rule = mock(TypedDependencyRule.class);
        final CorpusIngestion withRules = new CorpusIngestion(repositoryService, corpora, documents,
                List.of(rule), domainModels, archetypeClassifier, candidateResolver, nlpPort, ruleMatches);

        final DomainModel model = new DomainModel();
        final Corpus corpus = stubCorpusLookup();
        when(corpus.getDomainModel()).thenReturn(model);
        final Sentence sentence = mock(Sentence.class);
        final Document document = mock(Document.class);
        when(document.getContent()).thenReturn("text");
        when(document.getSentences()).thenReturn(List.of(sentence));
        when(document.getCorpus()).thenReturn(corpus);
        stubPipeline(document, model, List.of());

        withRules.loadUcDocument(UC_FILE);

        verify(rule).analyzeAndMatch(sentence);
    }

    /** The single corpus is created on demand when it does not exist yet. */
    @Test
    void aMissingCorpusIsCreatedOnDemand() {
        final Corpus created = mock(Corpus.class);
        when(corpora.findByTitle(CORPUS_TITLE)).thenReturn(List.of());
        when(corpora.create(CORPUS_TITLE)).thenReturn(created);
        when(documents.existsByContent(anyString())).thenReturn(true);   // stop right after the lookup

        ingestion.loadUcDocument(UC_FILE);

        verify(corpora).create(CORPUS_TITLE);
    }

    /** An already-analysed document short-circuits before any pipeline work. */
    @Test
    void loadingADuplicateDocumentIsSkipped() {
        stubCorpusLookup();
        when(documents.existsByContent(anyString())).thenReturn(true);

        assertThat(ingestion.loadUcDocument(UC_FILE)).isFalse();

        verify(documents, never()).create(anyString(), anyString(), any(), anyList());
        verify(nlpPort, never()).parse(anyString());
        verify(repositoryService, never()).persistAndFlush(any());
    }

    /** A corpus that has no model yet gets one, and it becomes the corpus's shared model. */
    @Test
    void aCorpusWithoutAModelGetsOne() {
        final Corpus corpus = stubCorpusLookup();
        final DomainModel created = new DomainModel();
        when(domainModels.create()).thenReturn(created);
        final Document document = analysedDocument("some requirements", corpus);
        stubPipeline(document, created, List.of());

        ingestion.loadUcDocument(UC_FILE);

        verify(domainModels).create();
        verify(corpus).setDomainModel(created);
    }

    /** Every {@link ClassCdd} candidate is classified with a Coad archetype. */
    @Test
    void classCandidatesAreArchetypeClassified() {
        final DomainModel model = new DomainModel();
        final Corpus corpus = stubCorpusLookup();
        when(corpus.getDomainModel()).thenReturn(model);
        final Document document = analysedDocument("text", corpus);
        final ClassCdd clazz = new ClassCdd();
        stubPipeline(document, model, List.of(clazz));
        when(archetypeClassifier.suggestArchetype(clazz)).thenReturn(ClassType.ROLE);

        ingestion.loadUcDocument(UC_FILE);

        assertThat(clazz.getClassType()).isEqualTo(ClassType.ROLE);
    }

    /** The batch entry point visits every bundled {@code UC*.md} file. */
    @Test
    void loadSampleFilesChecksEveryBundledUseCase() {
        stubCorpusLookup();
        when(documents.existsByContent(anyString())).thenReturn(true);   // all already analysed

        assertThat(ingestion.loadSampleFiles()).isZero();
        verify(documents, times(UcResources.listUcFilenames().size())).existsByContent(anyString());
    }

    /** With nothing already loaded, every bundled use case is analysed and counted. */
    @Test
    void loadSampleFilesAnalysesEveryBundledUseCaseWhenNoneIsADuplicate() {
        final DomainModel model = new DomainModel();
        final Corpus corpus = stubCorpusLookup();
        when(corpus.getDomainModel()).thenReturn(model);
        final Document document = analysedDocument("text", corpus);
        stubPipeline(document, model, List.of());

        final int expected = UcResources.listUcFilenames().size();

        assertThat(ingestion.loadSampleFiles()).isEqualTo(expected);
        verify(repositoryService, times(expected)).persistAndFlush(corpus);
    }

    // -- helpers --------------------------------------------------------------

    private Corpus stubCorpusLookup() {
        final Corpus corpus = mock(Corpus.class);
        when(corpora.findByTitle(CORPUS_TITLE)).thenReturn(List.of(corpus));
        return corpus;
    }

    /** A mock document that behaves like one already created and attached to {@code corpus}. */
    private Document analysedDocument(final String content, final Corpus corpus) {
        final Document document = mock(Document.class);
        when(document.getContent()).thenReturn(content);
        when(document.getSentences()).thenReturn(List.of());
        when(document.getCorpus()).thenReturn(corpus);
        return document;
    }

    private void stubPipeline(final Document document, final DomainModel model,
                              final List<Candidate> candidates) {
        when(documents.existsByContent(anyString())).thenReturn(false);
        when(documents.create(anyString(), anyString(), any(Clob.class), anyList())).thenReturn(document);
        when(nlpPort.parse(anyString())).thenReturn(new DocumentTO(List.of()));
        when(documents.createSentences(eq(document), any())).thenReturn(List.of());
        when(ruleMatches.listAllForDocument(document)).thenReturn(List.of());
        when(ruleMatches.createCandidatesFrom(anyList(), eq(model))).thenReturn(candidates);
    }
}
