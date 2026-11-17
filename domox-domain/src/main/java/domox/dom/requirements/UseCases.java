package domox.dom.requirements;

import domox.DomainModule;
import domox.dom.crc.model.Candidate;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import jakarta.annotation.Priority;
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

/**
 * The <b>use-case provenance</b> service: which {@link Document use cases} a {@link Candidate}'s
 * evidence comes from — the "which part of the business talks about it" question, next to the
 * corpus-evidence service ({@link ContextEvidence}: the sentences themselves) and the lexical graph
 * ({@code CandidateD3Graph}: <em>which other words</em> co-occur with it).
 *
 * <p><b>What it answers.</b>  A {@link UseCaseProvenance} per use case the candidate's <em>own
 * evidence</em> comes from, most-evidenced first — the unit a sub-domain partition is built on.  A
 * candidate whose evidence comes from one use case is owned by that area, while one whose evidence
 * is spread over most use cases is a shared, cross-cutting concept rather than any one area's
 * property.</p>
 *
 * <p><b>Why it is its own service.</b>  Unlike {@link ContextEvidence#sentencesFor(Candidate)} —
 * which asks "where does this name occur at all" by lemma, and owns two repositories — this walks
 * the candidate's {@link RuleMatch rule matches} back to their sentences and then to the
 * {@link Document} each sentence was ingested from.  So it answers "which use cases the rules
 * actually fired on", it needs <em>no repository at all</em> (the matches are already loaded with
 * the candidate), and it changes when the rule-match chain changes and for no other reason.  That is
 * a different axis from the sentence look-up, hence a different class.</p>
 */
@Named(DomainModule.NAMESPACE + ".UseCases")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class UseCases {

    /**
     * The use cases a candidate's <em>own evidence</em> comes from, most-evidenced first.
     * <p>
     * The rule matches are walked to their sentences and then to the document each sentence was
     * ingested from, so this answers "which use cases the rules actually fired on" — the evidence a
     * sub-domain partition rests on — and it is derived from data already loaded for the candidate's
     * review.
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
        if (candidates != null) {
            for (final Candidate candidate : candidates) {
                if (candidate != null && candidate.getId() != null) {
                    byCandidateId.put(candidate.getId(), provenanceFor(candidate));
                }
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
                        .thenComparingLong(UseCaseProvenance::documentId))
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
