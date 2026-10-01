package domox.webapp.integtests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import domox.dom.rqm.Documents;
import domox.dom.crc.ActionCddRepository;
import domox.dom.crc.AssociationCddRepository;
import domox.dom.crc.Candidate;
import domox.dom.crc.ClassCddRepository;
import domox.dom.crc.DomainModel;
import domox.dom.crc.PropertyCdd;
import domox.dom.crc.PropertyCddRepository;
import domox.dom.rqm.Corpus;
import domox.dom.rqm.CorpusRepository;
import domox.dom.rqm.DocumentRepository;
import domox.dom.rules.RuleMatch;

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
 *       {@code PropertyCdd}, one per owning class).</li>
 * </ul>
 * Requires the CoreNLP service (docker {@code domox-nlp-1}) on {@code localhost:8999},
 * and a fresh (empty) database so that no prior state trips the
 * {@code existsByContent} de-duplication guard.
 */
@ContextConfiguration(initializers = CorpusCandidateDedupIntegTest.Initializer.class)
@Transactional
class CorpusCandidateDedupIntegTest extends ApplicationIntegTestAbstract {

    private static final String CORPUS_TITLE = "Pet Shop Use Cases";

    @Autowired
    private Documents documents;

    @Autowired
    private CorpusRepository corpusRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ClassCddRepository classCddRepository;

    @Autowired
    private PropertyCddRepository propertyCddRepository;

    @Autowired
    private ActionCddRepository actionCddRepository;

    @Autowired
    private AssociationCddRepository associationCddRepository;

    @Test
    void loadingWholeCorpus_producesExactlyOneCandidatePerName_intoOneSharedDomainModel() {
        // when: run the full analysis over every UC* document of the PetShop corpus
        final List<RuleMatch> ruleMatches = wrap(documents).loadFileSample();

        // then: analysis really produced matches and one document per corpus file (15)
        assertThat(ruleMatches).isNotEmpty();
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
        assertThat(sharedModel.getPropertyList()).hasSize((int) propertyCddRepository.count());
        assertThat(sharedModel.getActionList()).hasSize((int) actionCddRepository.count());
        assertThat(sharedModel.getAssociationList()).hasSize((int) associationCddRepository.count());

        // THE core regression: exactly one candidate per name, per type (before the
        // fix every name appeared once per document, i.e. 15 times here).
        assertNeverDuplicated(classCddRepository.findAll());
        // Properties are scoped per owning ClassCdd (findByClassCddAndCandidateName),
        // so the same name may legitimately belong to several classes; the regression
        // metric is uniqueness within each owning class.
        assertPropertyNamesNeverDuplicatedPerOwner(propertyCddRepository.findAll());
        assertNeverDuplicated(actionCddRepository.findAll());
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
     * {@link PropertyCdd} lookups are scoped by owning class
     * ({@code findByClassCddAndCandidateName}), so cross-class name reuse is legal
     * (e.g. {@code Pet.size} vs {@code Collar.size}); the pre-fix bug instead produced
     * N rows for the same pair — one per cumulatively re-materialised DomainModel.
     */
    private static void assertPropertyNamesNeverDuplicatedPerOwner(final List<PropertyCdd> candidates) {
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
                .as("exactly one PropertyCdd per owning class and name across all corpus documents")
                .isEmpty();
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(final ConfigurableApplicationContext context) {
            if (ApplicationIntegTestAbstract.postgres != null && ApplicationIntegTestAbstract.postgres.isRunning()) {
                TestPropertyValues.of(
                        "spring.datasource.url=" + ApplicationIntegTestAbstract.postgres.getJdbcUrl(),
                        "spring.datasource.username=" + ApplicationIntegTestAbstract.postgres.getUsername(),
                        "spring.datasource.password=" + ApplicationIntegTestAbstract.postgres.getPassword()
                ).applyTo(context.getEnvironment());
            }
        }
    }
}