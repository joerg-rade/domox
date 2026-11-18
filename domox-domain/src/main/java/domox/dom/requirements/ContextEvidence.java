package domox.dom.requirements;

import domox.DomainModule;
import domox.dom.crc.diagram.ContextMatching;
import domox.dom.crc.diagram.ContextMatching.SentenceOccurrence;
import domox.dom.crc.model.Candidate;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.SentenceRepository;
import domox.dom.nlp.TypedDependencyRepository;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The <b>corpus-evidence</b> service: the {@link Sentence}s in which a {@link Candidate}'s name
 * occurs — the "where is this candidate actually talked about" question, next to the lexical graph
 * ({@code CandidateD3Graph}: <em>which other words</em> co-occur with it) and the ontology graph
 * (<em>which reference vocabulary</em> covers it).
 *
 * <p><b>What it answers.</b>  The sentences that back a candidate are what a reviewer reads when
 * deciding whether the evidence really supports it, so they are listed as a table on the
 * candidate's <em>Context Sentences</em> tab — {@link #sentencesFor(Candidate)}.  Each row is a
 * {@link ContextSentence}: the sentence, with the words the candidate matched on highlighted, so the
 * reviewer does not have to hunt for the candidate in a sentence that may be hundreds of characters
 * long.</p>
 *
 * <p><b>How the sentences are found.</b>  Through {@link TypedDependencyRepository#findByLemma}:
 * the candidate name is split into its words ({@code "PetShop"} → {@code pet}, {@code shop}) and a
 * sentence qualifies only when <em>every</em> word occurs in it as a governor or dependent lemma.
 * Lemma matching (rather than {@code Sentence.text} containment) is what makes "occurs as a word"
 * true — the tokens were lemmatised and POS-tagged by the parser, so {@code "pet"} does not match
 * {@code "petrol"}.</p>
 *
 * <p><b>Why it is its own service.</b>  This is the corpus-evidence axis: it changes when the
 * matching rule, the sentence look-up or the presentation of the evidence changes, and for no other
 * reason.  The rule itself is pure and lives in {@link ContextMatching} (tested without a
 * container); this class only owns the repositories and the {@link Candidate} → evidence
 * mapping.</p>
 *
 * <p><b>What it deliberately does not answer.</b>  <em>Which use case</em> a candidate's evidence
 * comes from is a different question — it follows the candidate's rule matches back to the document
 * each sentence was ingested from, needs no repository at all, and changes when the rule-match chain
 * changes.  It is therefore a separate service, {@link UseCases}.</p>
 *
 * <p>{@link #occurrencesFor(String)} is the occurrence-level view of the same evidence — the
 * matching rule ({@code ContextMatching}) applied to the repositories, without resolving the
 * sentences.</p>
 */
@Named(DomainModule.NAMESPACE + ".ContextEvidence")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class ContextEvidence {

    private final TypedDependencyRepository typedDependencyRepository;
    private final SentenceRepository sentenceRepository;

    @Inject
    public ContextEvidence(final TypedDependencyRepository typedDependencyRepository,
                           final SentenceRepository sentenceRepository) {
        this.typedDependencyRepository = typedDependencyRepository;
        this.sentenceRepository = sentenceRepository;
    }

    /**
     * The sentences in which {@code candidate}'s name occurs, in ascending sentence-id order — the
     * rows of the candidate's <em>Context Sentences</em> tab.
     * <p>
     * The matching half is {@link #occurrencesFor(String)}; this method only resolves the matched
     * sentence ids to the sentences the table renders, and wraps each in a {@link ContextSentence} —
     * the sentence's text, with the words this candidate matched on highlighted, for the sentence the
     * id resolved to.  The row carries the real sentence and not a copy of its text (the sentence is
     * the row's title, and what the row's own page shows), but the tab lists only that text: a column
     * repeating the sentence would add nothing to read.  A matched id whose sentence is no longer
     * there is skipped rather than rendered as a hole.
     *
     * @param candidate the candidate whose evidence is listed; {@code null}, or a candidate without
     *                  a name, yields an empty list
     */
    @Programmatic
    public List<ContextSentence> sentencesFor(final Candidate candidate) {
        if (candidate == null || candidate.getCandidateName() == null) {
            return List.of();
        }
        final List<SentenceOccurrence> occurrences = occurrencesFor(candidate.getCandidateName());
        if (occurrences.isEmpty()) {
            return List.of();
        }
        final List<Long> ids = occurrences.stream()
                .map(SentenceOccurrence::id)
                .distinct()
                .sorted()
                .toList();
        final Map<Long, Sentence> byId = sentenceRepository.findAllById(ids).stream()
                // the look-up is by id, so a row that carries none — or a null row — cannot be keyed
                // and is dropped here rather than rendered as a hole in the table
                .filter(Objects::nonNull)
                .filter(sentence -> sentence.getId() != null)
                .collect(Collectors.toMap(Sentence::getId, Function.identity()));
        return occurrences.stream()
                .filter(occurrence -> byId.containsKey(occurrence.id()))
                .map(occurrence -> ContextSentence.of(byId.get(occurrence.id()), occurrence.terms()))
                .toList();
    }

    /**
     * The sentences in which {@code name} occurs, in ascending sentence-id order — the matching half
     * of {@link #sentencesFor(Candidate)}, exposed separately so the rule can be exercised without
     * resolving the sentences to entities.
     *
     * @param name the candidate name; {@code null} or blank yields an empty list
     */
    @Programmatic
    public List<SentenceOccurrence> occurrencesFor(final String name) {
        final List<String> parts = ContextMatching.wordParts(name);
        if (parts.isEmpty()) {
            return List.of();
        }
        return ContextMatching.occurrences(parts, typedDependencyRepository::findByLemma);
    }
}
