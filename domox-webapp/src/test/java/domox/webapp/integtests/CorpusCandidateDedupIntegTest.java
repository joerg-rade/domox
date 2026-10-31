package domox.webapp.integtests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import domox.dom.crc.repository.ResponsibilityCddRepository;
import domox.dom.crc.repository.CollaboratorCddRepository;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.repository.ClassCddRepository;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.repository.AttributeCddRepository;
import domox.dom.requirements.Corpora;
import domox.dom.requirements.Corpus;
import domox.dom.requirements.CorpusRepository;
import domox.dom.requirements.DocumentRepository;

/**
 * Focused regression test for the corpus-owned <em>shared</em> {@link DomainModel}.
 * <p>
 * Regression covered: before the fix every {@code analyzeDocument()} call created a
 * fresh {@code DomainModel} and re-materialised the whole global rule-match set into
 * it, so loading N documents produced N cumulative models and every candidate name
 * appeared exactly N times.  Now a {@code Corpus} owns exactly one {@link DomainModel}
 * that all its documents share; this test loads the full PetShop use-case suite
 * (all 15 {@code UC*.md} documents) and asserts that:
 * <ul>
 *   <li>exactly one {@code Document} is created per corpus file,</li>
 *   <li>the corpus owns one shared {@code DomainModel},</li>
 *   <li>every candidate of every type belongs to that single shared model, and</li>
 *   <li>no candidate name is ever duplicated within a candidate type (for
 *       {@code AttributeCdd}, one per owning class).</li>
 * </ul>
 * Requires the CoreNLP service (docker {@code domox-nlp-1}) on {@code localhost:8999},
 * and a fresh (empty) database so that no prior state trips the
 * {@code existsByContent} de-duplication guard.
 */
@Transactional
class CorpusCandidateDedupIntegTest extends ApplicationIntegTestAbstract {

    private static final String CORPUS_TITLE = "Pet Shop Use Cases";

    @Autowired
    private Corpora corpora;

    @Autowired
    private CorpusRepository corpusRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ClassCddRepository classCddRepository;

    @Autowired
    private AttributeCddRepository propertyCddRepository;

    @Autowired
    private ResponsibilityCddRepository responsibilityCddRepository;

    @Autowired
    private CollaboratorCddRepository associationCddRepository;

    @Test
    void loadingWholeCorpus_producesExactlyOneCandidatePerName_intoOneSharedDomainModel() {
        // when: run the full analysis over every UC* document of the PetShop corpus
        final int loaded = corpora.loadSampleFiles();

        // then: analysis really produced matches and one document per corpus file (15)
        assertThat(loaded).as("all 15 UC* corpus files are newly loaded").isEqualTo(15);
        assertThat(documentRepository.count()).as("all 15 UC* corpus files are loaded").isEqualTo(15L);

        // the whole corpus owns exactly one shared DomainModel
        final Corpus corpus = corpusRepository.findByTitle(CORPUS_TITLE);
        assertThat(corpus).as("PetShop corpus exists").isNotNull();
        final DomainModel sharedModel = corpus.getDomainModel();
        assertThat(sharedModel).as("corpus owns a single shared DomainModel").isNotNull();

        // EVERY candidate of every type must live inside that one shared model,
        // so the shared model's own collections must be exactly as large as each
        // per-type candidate table.  (Had any candidate fallen outside the shared
        // model, its table row count would be greater than the collection size.)
        assertThat(sharedModel.getClassList()).hasSize((int) classCddRepository.count());
        assertThat(sharedModel.getAttributeList()).hasSize((int) propertyCddRepository.count());
        assertThat(sharedModel.getResponsibilityList()).hasSize((int) responsibilityCddRepository.count());
        assertThat(sharedModel.getCollaboratorList()).hasSize((int) associationCddRepository.count());

        // THE core regression: exactly one candidate per name, per type (before the
        // fix every name appeared once per document, i.e. 15 times here).
        assertNeverDuplicated(classCddRepository.findAll());
        // Properties are scoped per owning ClassCdd (findByClassCddAndCandidateName),
        // so the same name may legitimately belong to several classes; the regression
        // metric is uniqueness within each owning class.
        assertPropertyNamesNeverDuplicatedPerOwner(propertyCddRepository.findAll());
        assertNeverDuplicated(responsibilityCddRepository.findAll());
        assertNeverDuplicated(associationCddRepository.findAll());
    }

    /**
     * Asserts that no candidate name occurs more than once within a single type.
     */
    private static void assertNeverDuplicated(final List<? extends Candidate> candidates) {
        final String type = candidates.isEmpty() ? "?" : candidates.get(0).getClass().getSimpleName();
        final List<String> duplicatedNames = candidates.stream()
                .collect(Collectors.groupingBy(Candidate::getCandidateName, Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1L)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        assertThat(duplicatedNames)
                .as("exactly one %s candidate per name across all corpus documents", type)
                .isEmpty();
    }

    /**
     * Asserts that no {@code (owning ClassCdd, property name)} pair is duplicated.
     * <p>
     * {@link AttributeCdd} lookups are scoped by owning class
     * ({@code findByClassCddAndCandidateName}), so cross-class name reuse is legal
     * (e.g. {@code Pet.size} vs {@code Collar.size}); the pre-fix bug instead produced
     * N rows for the same pair — one per cumulatively re-materialised DomainModel.
     */
    private static void assertPropertyNamesNeverDuplicatedPerOwner(final List<AttributeCdd> candidates) {
        final List<String> duplicatedKeys = candidates.stream()
                .collect(Collectors.groupingBy(
                        p -> (p.classCdd != null ? p.classCdd.getId() : "?") + "::" + p.getCandidateName(),
                        Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1L)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        assertThat(duplicatedKeys)
                .as("exactly one AttributeCdd per owning class and name across all corpus documents")
                .isEmpty();
    }

    }