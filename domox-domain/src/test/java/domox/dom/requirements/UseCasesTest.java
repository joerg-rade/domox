package domox.dom.requirements;

import domox.dom.AbstractEntity;
import domox.dom.crc.model.ClassCdd;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the use-case-provenance service: that a candidate's rule matches are attributed to the
 * use cases their sentences were ingested from, most-evidenced first, with a sentence counted once
 * however many rules fired in it — and that evidence which cannot be attributed (no sentence, no
 * source document, no id) is skipped rather than guessed at.
 * <p>
 * The service is pure — it walks the rule-match chain already loaded with the candidate and touches
 * no repository — so it is exercised here directly, with no mocks and no container.
 */
class UseCasesTest {

    private final UseCases classUnderTest = new UseCases();

    @Test
    void provenanceFor_ofNoCandidate_isEmpty() {
        assertThat(classUnderTest.provenanceFor(null)).isEmpty();
    }

    @Test
    void provenanceFor_ofACandidateWithoutEvidence_isEmpty() {
        assertThat(classUnderTest.provenanceFor(classCdd("Pet"))).isEmpty();
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
    void provenanceFor_breaksATieOnSentenceCount_byDocumentId() {
        // given: two use cases with one sentence each - the tie must resolve, and deterministically
        final ClassCdd candidate = classCdd("Pet");
        candidate.addMatchingRule(ruleMatch(sentence(1L, "A pet is sold.", document(2L, "UC01.md")), "sold"));
        candidate.addMatchingRule(ruleMatch(sentence(2L, "A pet is groomed.", document(9L, "UC02.md")), "groomed"));

        // when/then: equal counts, so the lower document id comes first
        assertThat(classUnderTest.provenanceFor(candidate))
                .extracting(UseCaseProvenance::documentId)
                .containsExactly(2L, 9L);
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
    void provenanceFor_skipsAnUnsavedSentenceAndAnUnsavedDocument() {
        // given: an id-less sentence, and a sentence of an id-less document — neither is a use case yet
        final ClassCdd candidate = classCdd("Pet");
        candidate.addMatchingRule(ruleMatch(sentence(null, "A pet."), "no id"));
        candidate.addMatchingRule(ruleMatch(sentence(1L, "A pet.", document(null, "UC01.md")), "no doc id"));

        // when/then: an id is what makes the provenance addressable, so both are skipped
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

    @Test
    void provenanceForAll_ofNoCandidates_isEmpty() {
        assertThat(classUnderTest.provenanceForAll(null)).isEmpty();
        assertThat(classUnderTest.provenanceForAll(List.of())).isEmpty();
    }

    @Test
    void provenanceForAll_skipsANullCandidateAndOneWithoutAnId() {
        final ClassCdd unsaved = classCdd("Unsaved");

        // when: a null entry and an id-less candidate cannot be keyed
        final Map<Long, List<UseCaseProvenance>> all =
                classUnderTest.provenanceForAll(Arrays.asList(null, unsaved));

        // then
        assertThat(all).isEmpty();
    }

    // ============ fixtures ============

    private static ClassCdd classCdd(final String name) {
        return new ClassCdd(name, List.of(), List.of(), List.of());
    }

    /** A document with a database id — the use case a sentence was ingested from. */
    private static Document document(final Long id, final String fileName) {
        final Document document = new Document();
        document.setTitle(fileName);
        document.setFileName(fileName);
        if (id != null) {
            setId(document, id);
        }
        return document;
    }

    /** The same sentence, attributed to the use case it came from. */
    private static Sentence sentence(final Long id, final String text, final Document document) {
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
    private static Sentence sentence(final Long id, final String text) {
        final Sentence sentence = new Sentence();
        sentence.setText(text);
        if (id != null) {
            setId(sentence, id);
        }
        return sentence;
    }

    /** A typed dependency in {@code sentence} carrying {@code lemma} as its governor. */
    private static TypedDependency dependency(final Sentence sentence, final String lemma) {
        final TypedDependency td = new TypedDependency();
        td.setSentence(sentence);
        td.setGovernorLemma(lemma);
        td.setGovernorGloss(lemma);
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
