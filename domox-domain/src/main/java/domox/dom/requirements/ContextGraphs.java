package domox.dom.requirements;

import domox.DomainModule;
import domox.dom.crc.diagram.ContextMatching;
import domox.dom.crc.diagram.ContextMatching.SentenceOccurrence;
import domox.dom.crc.model.Candidate;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.SentenceRepository;
import domox.dom.nlp.TypedDependency;
import domox.dom.nlp.TypedDependencyRepository;
import domox.dom.rules.RuleMatch;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p>{@link #occurrencesFor(String)} is the occurrence-level view of the same evidence — the
 * matching rule ({@code ContextMatching}) applied to the repositories, without resolving the
 * sentences.</p>
 */
@Named(DomainModule.NAMESPACE + ".ContextGraphs")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class ContextGraphs {

    private final TypedDependencyRepository typedDependencyRepository;
    private final SentenceRepository sentenceRepository;

    @Inject
    public ContextGraphs(final TypedDependencyRepository typedDependencyRepository,
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
                .filter(sentence -> sentence != null && sentence.getId() != null)
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

    // --- use-case provenance (the evidence a sub-domain partition is built on) ---

    /**
     * The use cases a candidate's <em>own evidence</em> comes from, most-evidenced first.
     * <p>
     * Unlike {@link #sentencesFor(Candidate)} — which asks "where does this name occur at all" by
     * lemma — this walks the candidate's {@link RuleMatch rule matches} back to their sentences and
     * then to the {@link Document} each sentence was ingested from.  So it answers "which use cases
     * the rules actually fired on", which is the evidence a sub-domain partition rests on, and it is
     * derived from data already loaded for the candidate's review.
     *
     * @param candidate the candidate; {@code null} yields an empty list
     * @return one entry per use case the candidate's evidence comes from, ordered by descending
     *         sentence count (ties broken by document id), empty when the candidate has no evidence
     */
    @Programmatic
    public List<UseCaseProvenance> provenanceFor(final Candidate candidate) {
        return candidate == null ? List.of() : provenanceOf(candidate.getRuleMatches());
    }

    /**
     * {@link #provenanceFor(Candidate)} for many candidates at once, keyed by candidate id and in the
     * order the candidates were given — the bulk read a sub-domain pass starts from, so the partition
     * is built from one pass over the corpus rather than one call per candidate.
     */
    @Programmatic
    public Map<Long, List<UseCaseProvenance>> provenanceForAll(final List<? extends Candidate> candidates) {
        final Map<Long, List<UseCaseProvenance>> byCandidateId = new LinkedHashMap<>();
        if (candidates == null) {
            return byCandidateId;
        }
        for (final Candidate candidate : candidates) {
            if (candidate != null && candidate.getId() != null) {
                byCandidateId.put(candidate.getId(), provenanceFor(candidate));
            }
        }
        return byCandidateId;
    }

    private static List<UseCaseProvenance> provenanceOf(final List<RuleMatch> ruleMatches) {
        if (ruleMatches == null || ruleMatches.isEmpty()) {
            return List.of();
        }
        final Map<Long, Document> documentById = new LinkedHashMap<>();
        final Map<Long, Set<Long>> sentenceIdsByDocumentId = new LinkedHashMap<>();
        for (final RuleMatch ruleMatch : ruleMatches) {
            final Sentence sentence = sentenceOf(ruleMatch);
            final Document document = sentence == null ? null : sentence.getDocument();
            if (sentence == null || sentence.getId() == null || document == null || document.getId() == null) {
                continue;
            }
            documentById.putIfAbsent(document.getId(), document);
            sentenceIdsByDocumentId
                    .computeIfAbsent(document.getId(), key -> new LinkedHashSet<>())
                    .add(sentence.getId());
        }
        return sentenceIdsByDocumentId.entrySet().stream()
                .map(entry -> {
                    final Document document = documentById.get(entry.getKey());
                    return new UseCaseProvenance(
                            entry.getKey(),
                            document.getTitle(),
                            document.getFileName(),
                            entry.getValue().size());
                })
                .sorted(Comparator.comparingLong(UseCaseProvenance::sentenceCount).reversed()
                        .thenComparing(Comparator.comparingLong(UseCaseProvenance::documentId)))
                .toList();
    }

    private static Sentence sentenceOf(final RuleMatch ruleMatch) {
        if (ruleMatch == null) {
            return null;
        }
        final TypedDependency typedDependency = ruleMatch.getTypedDependency();
        return typedDependency == null ? null : typedDependency.getSentence();
    }
}

