package domox.dom.crc.diagram;

import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ReviewStatus;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The <em>candidate-neighbourhood</em> algorithm shared by the three lexical diagram renderers
 * ({@link CandidateD3Graph}, {@link CandidateVegaGraph}, {@link DomainModelDiagramsD3}).
 * <p>
 * A lexical diagram is a node-link graph whose nodes are candidate words (keyed by their
 * lower-cased lemma) and whose links are the typed dependencies of the candidates'
 * {@link RuleMatch}es.  Its content is a breadth-first expansion of the candidate-connection graph
 * from a seed set — a single candidate for the per-candidate renderers, the model's approved
 * candidates for the model-wide one — keeping only dependencies whose governor <em>and</em>
 * dependent lemma both correspond to a candidate word, and dropping the synthetic
 * {@code "X_Y"} association-artifact compounds.
 * <p>
 * Each renderer used to carry its own copy of that algorithm: three byte-identical helper bodies
 * ({@code collectDependencies}, {@code addCandidatesForLemma}, {@code bothEndpointsAreCandidates})
 * and four more that differed only by a wrapping comment.  A change to the traversal — or to which
 * nodes are excluded — therefore had to be made three times, and the renderers could disagree on
 * which node a link attaches to.  This class is the single source of that behaviour, and being
 * {@code static} and free of any framework dependency it is unit-testable in isolation.
 */
public final class CandidateNeighbourhood {

    private CandidateNeighbourhood() {
    }

    /** Adds a {@code null}-safe candidate-list snapshot to {@code target}. */
    public static void addAll(final List<Candidate> target, final List<? extends Candidate> source) {
        if (source != null) {
            target.addAll(source);
        }
    }

    /** The lower-cased candidate names of the snapshot. */
    public static Set<String> candidateLemmas(final List<Candidate> allCandidates) {
        final Set<String> lemmas = new HashSet<>();
        allCandidates.forEach(c -> lemmas.add(DiagramLemmas.lower(c.getCandidateName())));
        return lemmas;
    }

    /** Lower-case candidate name → every candidate carrying that name. */
    public static Map<String, List<Candidate>> candidatesByLemma(final List<Candidate> allCandidates) {
        final Map<String, List<Candidate>> byLemma = new HashMap<>();
        allCandidates.forEach(c ->
                byLemma.computeIfAbsent(DiagramLemmas.lower(c.getCandidateName()), k -> new ArrayList<>()).add(c));
        return byLemma;
    }

    /** Lower-cased lemmas of approved candidates (bold border in the graph). */
    public static Set<String> approvedLemmas(final List<Candidate> candidates) {
        final Set<String> lemmas = new HashSet<>();
        for (final Candidate c : candidates) {
            if (c != null && c.getReviews() != null
                    && c.getReviews().stream().anyMatch(r -> r != null && r.getStatus() == ReviewStatus.APPROVED)) {
                lemmas.add(DiagramLemmas.lower(c.getCandidateName()));
            }
        }
        return lemmas;
    }

    /**
     * Lower-cased lemmas of synthetic {@code Association}-typed compound candidates ("X_Y").
     * Such candidates are products of candidate-extraction rules and are excluded from the diagram
     * because their derived lemma is uninformative.
     */
    public static Set<String> associationArtifactLemmas(final List<Candidate> candidates,
                                                        final Set<String> candidateLemmas) {
        final Set<String> artifactLemmas = new HashSet<>();
        for (final Candidate c : candidates) {
            if (c == null || c.getCandidateName() == null) continue;
            if (!"Association".equals(DiagramLemmas.typeLabel(c.getCandidateType()))) continue;
            final String lemma = DiagramLemmas.lower(c.getCandidateName());
            final int sep = lemma.indexOf('_');
            if (sep <= 0 || sep >= lemma.length() - 1) continue;
            final String left = lemma.substring(0, sep);
            final String right = lemma.substring(sep + 1);
            if (candidateLemmas.contains(left) && candidateLemmas.contains(right)) {
                artifactLemmas.add(lemma);
            }
        }
        return artifactLemmas;
    }

    /**
     * Breadth-first traversal of the candidate-connection graph starting from {@code seeds}, up to
     * {@code maxDepth} hops, returning the <em>distinct</em> candidate-restricted dependencies.
     * <p>
     * Round 0 expands the seeds themselves (their direct connections); round 1 expands each
     * directly-connected candidate (their connections); and so on.  Every dependency kept is
     * restricted to candidate words, and candidates already expanded in an earlier round are never
     * re-expanded, which bounds the traversal even when the candidate graph contains cycles.
     */
    public static List<TypedDependency> collectDependencies(final List<Candidate> seeds,
                                                            final Set<String> candidateLemmas,
                                                            final Map<String, List<Candidate>> candidatesByLemma,
                                                            final int maxDepth) {
        final List<TypedDependency> dependencies = new ArrayList<>();
        final Set<TypedDependency> seen = new HashSet<>();
        final Set<Candidate> visited = new HashSet<>();
        Set<Candidate> frontier = new LinkedHashSet<>(seeds);
        for (int hop = 0; hop < maxDepth && !frontier.isEmpty(); hop++) {
            final Set<Candidate> nextFrontier = new LinkedHashSet<>();
            for (final Candidate candidate : frontier) {
                visited.add(candidate);
                final List<RuleMatch> matches = candidate.getRuleMatches();
                if (matches == null) {
                    continue;
                }
                for (final RuleMatch ruleMatch : matches) {
                    final TypedDependency td = ruleMatch.getTypedDependency();
                    if (td == null || !bothEndpointsAreCandidates(td, candidateLemmas)) {
                        continue;
                    }
                    if (!seen.add(td)) {
                        continue;
                    }
                    dependencies.add(td);
                    // The counterpart candidates become the next round's frontier.
                    addCandidatesForLemma(td.getGovernorLemma(), candidatesByLemma, visited, nextFrontier);
                    addCandidatesForLemma(td.getDependentLemma(), candidatesByLemma, visited, nextFrontier);
                }
            }
            frontier = nextFrontier;
        }
        return dependencies;
    }

    /**
     * Removes every dependency whose governor or dependent lemma is an association-artifact compound:
     * the participant lemmas and their typed-dependency link are kept; only the compound is dropped.
     */
    public static void removeArtifactEdges(final List<TypedDependency> dependencies,
                                           final Set<String> artifactLemmas) {
        dependencies.removeIf(td -> td != null && (
                artifactLemmas.contains(DiagramLemmas.lower(td.getGovernorLemma())) ||
                artifactLemmas.contains(DiagramLemmas.lower(td.getDependentLemma()))));
    }

    /** Per-lemma metadata a diagram needs: the POS codes seen, and the summed rule-match count. */
    public record LemmaMetrics(Map<String, Set<String>> posByLemma, Map<String, Integer> matchCountByLemma) {}

    /**
     * Accumulates, per lower-cased lemma, the POS codes and the summed rule-match count of
     * {@code dependencies}.
     */
    public static LemmaMetrics lemmaMetrics(final List<TypedDependency> dependencies) {
        final Map<String, Set<String>> posByLemma = new LinkedHashMap<>();
        final Map<String, Integer> matchCountByLemma = new HashMap<>();
        for (final TypedDependency td : dependencies) {
            if (td == null) continue;
            DiagramLemmas.accumulatePos(posByLemma, DiagramLemmas.lower(td.getGovernorLemma()), td.getGovernorPos());
            DiagramLemmas.accumulatePos(posByLemma, DiagramLemmas.lower(td.getDependentLemma()), td.getDependentPos());
            final int matches = td.getRuleMatches() != null ? td.getRuleMatches().size() : 0;
            matchCountByLemma.merge(DiagramLemmas.lower(td.getGovernorLemma()), matches, Integer::sum);
            matchCountByLemma.merge(DiagramLemmas.lower(td.getDependentLemma()), matches, Integer::sum);
        }
        return new LemmaMetrics(posByLemma, matchCountByLemma);
    }

    /** Adds every candidate carrying {@code lemma} that has not yet been expanded to the next frontier. */
    private static void addCandidatesForLemma(final String lemma,
                                              final Map<String, List<Candidate>> candidatesByLemma,
                                              final Set<Candidate> visited,
                                              final Set<Candidate> nextFrontier) {
        for (final Candidate candidate : candidatesByLemma.getOrDefault(DiagramLemmas.lower(lemma), List.of())) {
            if (!visited.contains(candidate)) {
                nextFrontier.add(candidate);
            }
        }
    }

    /** Keeps only dependencies whose governor and dependent lemma are both candidate words. */
    private static boolean bothEndpointsAreCandidates(final TypedDependency td,
                                                      final Set<String> candidateLemmas) {
        return candidateLemmas.contains(DiagramLemmas.lower(td.getGovernorLemma()))
                && candidateLemmas.contains(DiagramLemmas.lower(td.getDependentLemma()));
    }
}
