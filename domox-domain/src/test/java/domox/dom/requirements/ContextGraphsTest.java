package domox.dom.requirements;

import domox.dom.AbstractEntity;
import domox.dom.crc.model.ClassCdd;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.SentenceRepository;
import domox.dom.nlp.TypedDependency;
import domox.dom.nlp.TypedDependencyRepository;
import domox.dom.rules.RuleMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies the candidate-level contract of the corpus-evidence service: the guard clauses, and that
 * the matched sentence ids really become the rows the candidate's <em>Context Sentences</em> tab
 * lists — deduplicated, in ascending id order, without a row for an id whose sentence is gone, and
 * with the words the sentence matched on highlighted in the row's text.
 * <p>
 * The neighbours cover the ends of the chain: {@code ContextMatchingTest} the pure matching rule,
 * {@code ContextHighlightTest} the pure highlighting rule, and {@code ContextGraphsIntegTest} the
 * JPQL against a real database.  What is pinned here is the mapping in between, with both
 * repositories mocked — no container, no database.
 */
@ExtendWith(MockitoExtension.class)
class ContextGraphsTest {

    @Mock TypedDependencyRepository typedDependencyRepository;
    @Mock SentenceRepository sentenceRepository;

    private ContextGraphs classUnderTest;

    @BeforeEach
    void setUp() {
        classUnderTest = new ContextGraphs(typedDependencyRepository, sentenceRepository);
    }

    @Test
    void sentencesFor_ofNoCandidate_isEmpty() {
        assertThat(classUnderTest.sentencesFor(null)).isEmpty();
    }

    @Test
    void sentencesFor_ofACandidateWithoutAName_isEmpty() {
        assertThat(classUnderTest.sentencesFor(classCdd(null))).isEmpty();
    }

    @Test
    void sentencesFor_ofANameWithoutAnyWord_isEmpty() {
        // "---" splits into no words at all, so there is nothing to look up
        assertThat(classUnderTest.sentencesFor(classCdd("---"))).isEmpty();
    }

    @Test
    void sentencesFor_resolvesTheMatchedSentencesInAscendingIdOrder() {
        // given: both words of "PetShop" occur in both sentences — one written later than the other
        final Sentence later = sentence(9L, "The pet shop sells pet food.");
        final Sentence earlier = sentence(3L, "A shop that sells a pet.");
        when(typedDependencyRepository.findByLemma("pet"))
                .thenReturn(List.of(dependency(later, "pet"), dependency(earlier, "pet")));
        when(typedDependencyRepository.findByLemma("shop"))
                .thenReturn(List.of(dependency(later, "shop"), dependency(earlier, "shop")));
        // the repository returns them in whatever order it likes
        when(sentenceRepository.findAllById(any())).thenReturn(List.of(later, earlier));

        // when
        final List<ContextSentence> rows = classUnderTest.sentencesFor(classCdd("PetShop"));

        // then: both matching sentences, once each, oldest first — each row carrying its sentence
        assertThat(rows).extracting(ContextSentence::getSentence).containsExactly(earlier, later);
    }

    @Test
    void sentencesFor_highlightsTheWordsTheSentenceMatchedOn() {
        // given: the sentence writes the word as "Pets", while the candidate's word lemmatises to "pet"
        final Sentence sentence = sentence(4L, "Pets are groomed by a pet shop.");
        when(typedDependencyRepository.findByLemma("pet"))
                .thenReturn(List.of(dependency(sentence, "pet", "Pets")));
        when(sentenceRepository.findAllById(any())).thenReturn(List.of(sentence));

        // when
        final List<ContextSentence> rows = classUnderTest.sentencesFor(classCdd("Pet"));

        // then: the word as written is marked — not the lemma, which the sentence also contains
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getText().html())
                .isEqualTo("<mark>Pets</mark> are groomed by a pet shop.");

        // and the row still carries the sentence itself, so the table links to it and not to a copy
        assertThat(rows.getFirst().getSentence()).isSameAs(sentence);
    }

    @Test
    void sentencesFor_skipsAMatchedSentenceThatIsNoLongerThere() {
        // given: the only match is a sentence the repository no longer returns (deleted meanwhile)
        final Sentence vanished = sentence(7L, "A pet shop.");
        when(typedDependencyRepository.findByLemma("pet")).thenReturn(List.of(dependency(vanished, "pet")));
        when(sentenceRepository.findAllById(any())).thenReturn(List.of());

        // when
        final List<ContextSentence> rows = classUnderTest.sentencesFor(classCdd("Pet"));

        // then: no hole in the table
        assertThat(rows).isEmpty();
    }

    // ============ use-case provenance ============

    @Test
    void provenanceFor_ofNoCandidate_isEmpty() {
        assertThat(classUnderTest.provenanceFor(null)).isEmpty();
    }

    @Test
    void provenanceFor_groupsTheEvidenceBySourceUseCase_mostEvidencedFirst() {
        // given: three rule matches - two in the selling use case, one in the grooming one
        final Document selling = document(2L, "UC01_SellingPetProducts.md");
        final Document grooming = document(1019L, "UC02_PetGroomingServices.md");
        final ClassCdd candidate = classCdd("Pet");
        candidate.addMatchingRule(ruleMatch(sentence(1L, "A pet is sold.", selling), "sold"));
        candidate.addMatchingRule(ruleMatch(sentence(2L, "The pet is priced.", selling), "priced"));
        candidate.addMatchingRule(ruleMatch(sentence(3L, "A pet is groomed.", grooming), "groomed"));

        // when
        final List<UseCaseProvenance> provenance = classUnderTest.provenanceFor(candidate);

        // then: the best-evidenced use case first, with only the counts exposed
        assertThat(provenance).extracting(UseCaseProvenance::documentId).containsExactly(2L, 1019L);
        assertThat(provenance).extracting(UseCaseProvenance::sentenceCount).containsExactly(2L, 1L);
        assertThat(provenance.getFirst().fileName()).isEqualTo("UC01_SellingPetProducts.md");
        assertThat(provenance.getFirst().documentTitle()).isEqualTo("UC01_SellingPetProducts.md");
    }

    @Test
    void provenanceFor_countsASentenceOnceEvenWhenSeveralRulesFiredInIt() {
        // given: two DIFFERENT rule matches, both on the same sentence of the same use case
        final Document selling = document(2L, "UC01_SellingPetProducts.md");
        final Sentence shared = sentence(1L, "A pet is sold to a customer.", selling);
        final ClassCdd candidate = classCdd("Pet");
        candidate.addMatchingRule(ruleMatch(shared, "sold"));
        candidate.addMatchingRule(ruleMatch(shared, "customer"));

        // when/then: one sentence, not two — the unit of provenance is the sentence
        assertThat(classUnderTest.provenanceFor(candidate))
                .extracting(UseCaseProvenance::sentenceCount)
                .containsExactly(1L);
    }

    @Test
    void provenanceFor_skipsEvidenceWithNoSentenceAndEvidenceWithNoDocument() {
        // given: one match whose sentence is gone, and one whose sentence has no source document
        final ClassCdd candidate = classCdd("Pet");
        candidate.addMatchingRule(ruleMatch(null, "no sentence"));
        candidate.addMatchingRule(ruleMatch(sentence(2L, "A pet.", null), "no document"));

        // when/then: neither can be attributed to a use case
        assertThat(classUnderTest.provenanceFor(candidate)).isEmpty();
    }

    @Test
    void provenanceForAll_keysTheResultByCandidateId_includingThoseWithoutEvidence() {
        final ClassCdd first = classCdd("Pet");
        setId(first, 11L);
        first.addMatchingRule(ruleMatch(sentence(1L, "A pet.", document(2L, "UC01_SellingPetProducts.md")), "sold"));
        final ClassCdd second = classCdd("Groomer");
        setId(second, 22L);

        // when
        final Map<Long, List<UseCaseProvenance>> all = classUnderTest.provenanceForAll(List.of(first, second));

        // then: both candidates are present, the unevidenced one with an empty list
        assertThat(all).containsOnlyKeys(11L, 22L);
        assertThat(all.get(11L)).hasSize(1);
        assertThat(all.get(22L)).isEmpty();
    }

    // ============ fixtures ============

    private static ClassCdd classCdd(final String name) {
        return new ClassCdd(name, List.of(), List.of(), List.of());
    }

    /** A document with a database id — the use case a sentence was ingested from. */
    private static Document document(final long id, final String fileName) {
        final Document document = new Document();
        document.setTitle(fileName);
        document.setFileName(fileName);
        setId(document, id);
        return document;
    }

    /** The same sentence, attributed to the use case it came from. */
    private static Sentence sentence(final long id, final String text, final Document document) {
        final Sentence sentence = sentence(id, text);
        sentence.setDocument(document);
        return sentence;
    }

    /**
     * A rule match on {@code sentence}, distinguished from another by its description — the part of
     * the signature {@code Candidate#addMatchingRule} de-duplicates on, so two matches inside one
     * sentence stay two matches.
     */
    private static RuleMatch ruleMatch(final Sentence sentence, final String description) {
        final RuleMatch ruleMatch = new RuleMatch();
        ruleMatch.setTypedDependency(dependency(sentence, "pet"));
        ruleMatch.setRuleClassName("test.Rule");
        ruleMatch.setCandidateType("ClassCdd");
        ruleMatch.setCandidateName("Pet");
        ruleMatch.setDescription(description);
        return ruleMatch;
    }

    /** A sentence with a database id, assigned reflectively as the sequence generator would. */
    private static Sentence sentence(final long id, final String text) {
        final Sentence sentence = new Sentence();
        sentence.setText(text);
        setId(sentence, id);
        return sentence;
    }

    /** A typed dependency in {@code sentence} carrying {@code lemma} as its governor. */
    private static TypedDependency dependency(final Sentence sentence, final String lemma) {
        return dependency(sentence, lemma, lemma);
    }

    /**
     * A typed dependency in {@code sentence} whose governor has lemma {@code lemma} and is
     * <em>written</em> {@code gloss} — the surface form the row highlights.
     */
    private static TypedDependency dependency(final Sentence sentence, final String lemma, final String gloss) {
        final TypedDependency td = new TypedDependency();
        td.setSentence(sentence);
        td.setGovernorLemma(lemma);
        td.setGovernorGloss(gloss);
        td.setGovernorPos(PartOfSpeechType.NN);
        td.setDependentLemma("other");
        td.setDependentPos(PartOfSpeechType.NN);
        return td;
    }

    /** Assigns the private JPA {@code id} field (normally set by the sequence generator). */
    private static void setId(final AbstractEntity entity, final long id) {
        try {
            final Field field = AbstractEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (final Exception e) {
            throw new IllegalStateException("failed to set id on " + entity, e);
        }
    }
}
