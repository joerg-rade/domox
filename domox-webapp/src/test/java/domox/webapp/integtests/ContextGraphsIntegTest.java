package domox.webapp.integtests;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.List;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import org.apache.causeway.applib.Identifier;
import org.apache.causeway.applib.services.metamodel.MetaModelService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.services.xactn.TransactionService;

import domox.dom.crc.diagram.ContextMatching.SentenceOccurrence;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.service.DomainModels;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import domox.dom.nlp.TypedDependencyRepository;
import domox.dom.requirements.ContextGraphs;
import domox.dom.requirements.ContextSentence;
import domox.dom.requirements.Corpus;

/**
 * Guards the <em>container</em> half of the candidate's context evidence — the three things the
 * container-free {@code ContextMatchingTest} cannot see:
 * <ul>
 *   <li>that {@link TypedDependencyRepository#findByLemma} really resolves (Spring Data parses the
 *       JPQL at bootstrap, so a typo there is a start-up failure, not a compile error) and really
 *       runs against the database, matching a lemma as governor or dependent, case-insensitively;</li>
 *   <li>that {@link ContextGraphs} really is injected on a <em>persisted</em> candidate — a
 *       {@code null} collaborator would silently yield the empty list the
 *       {@code getContextSentences()} guard returns, i.e. an empty tab with no error;</li>
 *   <li>that the whole chain (candidate → service → repositories → JPQL → rows) really produces the
 *       {@link ContextSentence} rows the candidate's <em>Context Sentences</em> tab lists — with the
 *       words the sentence matched on highlighted as the sentence <em>writes</em> them ({@code Petz}
 *       where the lemma is {@code petz}), which is only observable end-to-end.</li>
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

    /** The surface forms of those three words, as {@link #SENTENCE_TEXT} writes them. */
    private static final String ACRONYM_GLOSS = "Ctxzz";
    private static final String NOUN_GLOSS = "Petz";
    private static final String VERB_GLOSS = "Shopz";

    /**
     * The sentence as it is written: the name's words in their <em>surface</em> forms, capitalised,
     * so that a row highlighting the lemma rather than the word as written shows up in the assertion
     * below ({@code Petz} is marked, {@code petz} is not).
     */
    private static final String SENTENCE_TEXT = "The Ctxzz Petz Shopz record.";

    /** A lemma that is deliberately not a word of {@link #CANDIDATE_NAME}. */
    private static final String FILLER = "record";

    @Inject private ContextGraphs contextGraphs;
    @Inject private TypedDependencyRepository typedDependencyRepository;
    @Inject private DomainModels domainModels;
    @Inject private MetaModelService metaModelService;
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
    void contextSentences_ofAPersistedCandidate_listsEverySentenceCarryingItsName() {
        // given: a corpus owning a model with the candidate, and the sentence carrying all its words
        final Sentence complete = sentence(SENTENCE_TEXT);
        dependency(complete, ACRONYM, ACRONYM_GLOSS, PartOfSpeechType.NN, FILLER, PartOfSpeechType.NN);
        dependency(complete, NOUN, NOUN_GLOSS, PartOfSpeechType.NN, FILLER, PartOfSpeechType.NN);
        dependency(complete, VERB, VERB_GLOSS, PartOfSpeechType.VB, FILLER, PartOfSpeechType.NN);
        sentence("An unrelated sentence.");

        final Corpus corpus = new Corpus();
        corpus.setTitle("Context Sentences");
        corpus.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));
        final DomainModel domainModel = domainModels.create();
        final ClassCdd candidate = classCdd(CANDIDATE_NAME, domainModel);
        final ClassCdd unevidenced = classCdd("CtxzzMissingWordz", domainModel);
        corpus.setDomainModel(domainModel);
        repositoryService.persistAndFlush(corpus);
        transactionService.flushTransaction();

        // when: the entity's own collection — i.e. the injected collaborator, not the service directly
        assertThat(candidate.getId()).as("candidate persisted").isNotNull();
        final List<ContextSentence> rows = candidate.getContextSentences();

        // then: exactly the sentence that carries every word of the name, one row per sentence, each
        // row carrying the sentence itself — not a copy of its text (the tab lists the sentence as
        // that text, so the sentence itself is not one of the columns)
        assertThat(rows).extracting(row -> row.getSentence().getId()).containsExactly(complete.getId());

        // and the words the sentence matched on are highlighted where the sentence writes them
        assertThat(rows.getFirst().getText().html())
                .isEqualTo("The <mark>Ctxzz</mark> <mark>Petz</mark> <mark>Shopz</mark> record.");

        // and a candidate whose name occurs nowhere lists no sentences — an empty tab, not an error
        assertThat(unevidenced.getContextSentences()).as("context sentences without evidence").isEmpty();
    }

    /**
     * The <em>Context Sentences</em> tab lists each sentence as its own (highlighted) text, so the
     * sentence itself is not one of that table's columns — {@code ContextSentence#sentence} is hidden
     * in tables.  Asked through {@link MetaModelService#parentedAssociationsForColumnRendering} — the
     * very call the Wicket viewer makes to build a parented table's columns — so the assertion is
     * about the rendered table and not merely about the annotation on the property.
     */
    @Test
    void contextSentencesTab_listsTheSentenceAsTextOnly() {
        // given: a candidate, whose type owns the collection the tab renders
        final ClassCdd candidate = classCdd(CANDIDATE_NAME, domainModels.create());

        // when: the columns the parented table renders for that collection
        final List<String> columns = metaModelService
                .parentedAssociationsForColumnRendering(
                        candidate,
                        Identifier.collectionIdentifier(
                                metaModelService.lookupLogicalTypeByName("domox.ClassCdd").orElseThrow(),
                                "contextSentences"),
                        MetaModelService.AssociationsLookup.ENABLED)
                .map(Identifier::memberLogicalName)
                .toList();

        // then: the text is the only column — the sentence, which the text already is, is not repeated
        assertThat(columns).containsExactly("text");
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
        return dependency(sentence, governorLemma, governorLemma, governorPos, dependentLemma, dependentPos);
    }

    /**
     * The same, with the governor's <em>surface form</em> — the word the sentence writes, which is
     * what a row highlights ({@code Petz} where the lemma is {@code petz}).  The dependent is written
     * as its lemma, so the gloss that matters is the governor's.
     */
    private TypedDependency dependency(final Sentence sentence, final String governorLemma,
                                       final String governorGloss, final PartOfSpeechType governorPos,
                                       final String dependentLemma, final PartOfSpeechType dependentPos) {
        final TypedDependency td = new TypedDependency();
        td.setType(TdType.NSUBJ);
        td.setSentence(sentence);
        td.setGovernorIndex(1);
        td.setGovernorLemma(governorLemma);
        td.setGovernorGloss(governorGloss);
        td.setGovernorPos(governorPos);
        td.setDependentIndex(2);
        td.setDependentLemma(dependentLemma);
        td.setDependentGloss(dependentLemma);
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
