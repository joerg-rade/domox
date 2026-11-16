package domox.webapp.integtests;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.List;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.services.xactn.TransactionService;

import domox.dom.crc.diagram.ContextGraphJson.SentenceOccurrence;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.service.DomainModels;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import domox.dom.nlp.TypedDependencyRepository;
import domox.dom.requirements.ContextGraphs;
import domox.dom.requirements.Corpus;

/**
 * Guards the <em>container</em> half of the context graph — the three things the container-free
 * {@code ContextGraphJsonTest} cannot see:
 * <ul>
 *   <li>that {@link TypedDependencyRepository#findByLemma} really resolves (Spring Data parses the
 *       JPQL at bootstrap, so a typo there is a start-up failure, not a compile error) and really
 *       runs against the database, matching a lemma as governor or dependent, case-insensitively;</li>
 *   <li>that {@link ContextGraphs} really is injected on a <em>persisted</em> candidate — a
 *       {@code null} collaborator would silently yield the {@code null} property the
 *       {@code getContextGraph()} guard returns, i.e. an empty column with no error;</li>
 *   <li>that the whole chain (candidate → service → repository → JPQL → payload) produces the
 *       hub-plus-sentence-nodes contract the shared D3 client renders.</li>
 * </ul>
 * The lemmas are unique to this class, so the exact-count assertions cannot be tripped by rows
 * another test left behind.
 */
@Transactional
class ContextGraphsIntegTest extends ApplicationIntegTestAbstract {

    /** A name whose three words are {@code ctxzz}, {@code petz}, {@code shopz}. */
    private static final String CANDIDATE_NAME = "CtxzzPetzShopz";

    /** The name's words, one unique lemma each — no other row in the database carries them. */
    private static final String ACRONYM = "ctxzz";
    private static final String NOUN = "petz";
    private static final String VERB = "shopz";

    /** A lemma that is deliberately not a word of {@link #CANDIDATE_NAME}. */
    private static final String FILLER = "record";

    @Inject private ContextGraphs contextGraphs;
    @Inject private TypedDependencyRepository typedDependencyRepository;
    @Inject private DomainModels domainModels;
    @Inject private RepositoryService repositoryService;
    @Inject private TransactionService transactionService;

    @Test
    void findByLemma_matchesAGovernorOrDependentLemma_ignoringCase() {
        // given: two sentences; the unique noun lemma occurs in both, as a dependent and as a governor
        final Sentence both = sentence("The " + ACRONYM + " " + NOUN + " " + VERB + " " + FILLER + ".");
        final TypedDependency asDependent = dependency(both, ACRONYM, PartOfSpeechType.NN, NOUN, PartOfSpeechType.NN);
        final TypedDependency asGovernor = dependency(both, NOUN, PartOfSpeechType.NN, FILLER, PartOfSpeechType.NN);
        final Sentence other = sentence("A " + NOUN + " alone is not enough.");
        final TypedDependency elsewhere = dependency(other, NOUN, PartOfSpeechType.NN, FILLER, PartOfSpeechType.JJ);

        // when: the lemma is passed capitalised — the query lower-cases both sides
        final List<TypedDependency> found = typedDependencyRepository.findByLemma(NOUN.toUpperCase());

        // then: both roles match, in id order, and nothing that does not carry the lemma
        assertThat(found).extracting(TypedDependency::getId)
                .containsExactly(asDependent.getId(), asGovernor.getId(), elsewhere.getId());

        // and the acronym is found through the dependency that only carries it as a governor
        assertThat(typedDependencyRepository.findByLemma(ACRONYM))
                .extracting(TypedDependency::getId)
                .containsExactly(asDependent.getId());
    }

    @Test
    void occurrencesFor_returnsOnlyTheSentencesWhereEveryWordOfTheNameOccurs() {
        // given: the first sentence carries all three words of the name, the second only two
        final Sentence complete = sentence("The " + ACRONYM + " " + NOUN + " " + VERB + " " + FILLER + ".");
        dependency(complete, ACRONYM, PartOfSpeechType.NN, FILLER, PartOfSpeechType.NN);
        dependency(complete, NOUN, PartOfSpeechType.NN, FILLER, PartOfSpeechType.NN);
        dependency(complete, VERB, PartOfSpeechType.VB, FILLER, PartOfSpeechType.NN);
        final Sentence partial = sentence("A " + ACRONYM + " " + NOUN + " without the third word.");
        dependency(partial, ACRONYM, PartOfSpeechType.NN, NOUN, PartOfSpeechType.NN);

        // when
        final List<SentenceOccurrence> occurrences = contextGraphs.occurrencesFor(CANDIDATE_NAME);

        // then: one occurrence — the sentence where every word of the name is present
        assertThat(occurrences).hasSize(1);
        final SentenceOccurrence occurrence = occurrences.getFirst();
        assertThat(occurrence.id()).isEqualTo(complete.getId());

        // sized by the number of matching dependencies, and POS-tagged with the head word's code
        assertThat(occurrence.count()).isEqualTo(3);
        assertThat(occurrence.pos()).isEqualTo(PartOfSpeechType.VB.getCode());
    }

    @Test
    void contextGraph_ofAPersistedCandidate_rendersTheHubAndOneNodePerOccurringSentence() {
        // given: a corpus owning a model with the candidate, and the sentence carrying all its words
        final Sentence complete = sentence("The " + ACRONYM + " " + NOUN + " " + VERB + " " + FILLER + ".");
        dependency(complete, ACRONYM, PartOfSpeechType.NN, FILLER, PartOfSpeechType.NN);
        dependency(complete, NOUN, PartOfSpeechType.NN, FILLER, PartOfSpeechType.NN);
        dependency(complete, VERB, PartOfSpeechType.VB, FILLER, PartOfSpeechType.NN);
        sentence("An unrelated sentence.");

        final Corpus corpus = new Corpus();
        corpus.setTitle("Context Graph");
        corpus.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));
        final DomainModel domainModel = domainModels.create();
        final ClassCdd candidate = classCdd(CANDIDATE_NAME, domainModel);
        final ClassCdd unevidenced = classCdd("CtxzzMissingWordz", domainModel);
        corpus.setDomainModel(domainModel);
        repositoryService.persistAndFlush(corpus);
        transactionService.flushTransaction();

        // when: the entity's own property — i.e. the injected collaborator, not the service directly
        assertThat(candidate.getId()).as("candidate persisted").isNotNull();
        final String json = candidate.getContextGraph();

        // then: the hub carries the candidate's name, id, object type and (unreviewed) status
        assertThat(json).as("context graph of %s", CANDIDATE_NAME)
                .contains("\"id\":\"candidate\"")
                .contains("\"name\":\"" + CANDIDATE_NAME + "\"")
                .contains("\"approved\":false")
                .contains("\"isCandidateColumn\":true")
                .contains("\"candidateId\":" + candidate.getId())
                .contains("\"objectType\":\"domox.ClassCdd\"")
                // one sentence node, wired to the hub by exactly one link
                .contains("\"id\":\"sentence:" + complete.getId() + "\"")
                .contains("\"links\":[{\"source\":\"candidate\",\"target\":\"sentence:" + complete.getId() + "\"}]");

        // and a candidate whose name occurs nowhere still renders — as a lone hub, not as null
        final String unevidencedJson = unevidenced.getContextGraph();
        assertThat(unevidencedJson).as("context graph without evidence")
                .contains("\"name\":\"CtxzzMissingWordz\"")
                .contains("\"links\":[]");
    }

    private Sentence sentence(final String text) {
        final Sentence sentence = new Sentence();
        sentence.setText(text);
        repositoryService.persistAndFlush(sentence);
        return sentence;
    }

    /** A dependency carrying {@code governorLemma} and {@code dependentLemma}, in {@code sentence}. */
    private TypedDependency dependency(final Sentence sentence, final String governorLemma,
                                       final PartOfSpeechType governorPos, final String dependentLemma,
                                       final PartOfSpeechType dependentPos) {
        final TypedDependency td = new TypedDependency();
        td.setType(TdType.NSUBJ);
        td.setSentence(sentence);
        td.setGovernorIndex(1);
        td.setGovernorLemma(governorLemma);
        td.setGovernorPos(governorPos);
        td.setDependentIndex(2);
        td.setDependentLemma(dependentLemma);
        td.setDependentPos(dependentPos);
        repositoryService.persistAndFlush(td);
        return td;
    }

    /**
     * A persisted {@code ClassCdd} in {@code domainModel}.
     * <p>
     * Persisted <em>explicitly</em>, not left to the cascade from the corpus: Causeway injects an
     * entity's {@code @Inject} collaborators in {@code postPersist}, so a candidate that only ever
     * reaches the database through a cascade would still have a {@code null} collaborator — and its
     * graph property would render as {@code null} rather than fail.
     */
    private ClassCdd classCdd(final String name, final DomainModel domainModel) {
        final ClassCdd candidate = new ClassCdd();
        candidate.setCandidateName(name);
        candidate.setCandidateType("ClassCdd");
        candidate.domainModel = domainModel;
        domainModel.classList.add(candidate);
        repositoryService.persistAndFlush(candidate);
        return candidate;
    }
}
