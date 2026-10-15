package domox.dom.crc;

import domox.DomainModule;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import jakarta.annotation.Priority;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Renders a {@link DomainModel}-wide <em>lexical dependency</em> summary as a D3 force-directed
 * graph JSON seeded from the model's <em>approved</em> candidates, comparable to the per-candidate
 * {@link CandidateDiagrams#renderDiagram(Candidate)} but scoped to the whole analysis run.
 * <p>
 * The graph is built by a breadth-first expansion of the candidate-connection graph starting
 * from every approved candidate of the model (a candidate is approved when it carries at least
 * one {@link Review} whose status is {@link ReviewStatus#APPROVED}), up to
 * {@link DomainModel#getHopDepth()} hops deep (default {@code 1}).  Seeding from the approved set
 * keeps the starting graph deliberately small so it renders reliably; the depth is increased by
 * the user when a wider view of the model is wanted.  From each frontier candidate's own
 * {@link RuleMatch}es, only dependencies whose governor <em>and</em> dependent lemma both
 * correspond to a candidate word are kept, so the retained edges draw the approved candidates
 * together with every candidate directly (or, at higher depths, transitively) connected to them.
 * <p>
 * The result is a {@link String} containing a JSON object with {@code nodes} (each with an
 * {@code id}) and {@code links} (each with a {@code source} and {@code target}) in the format
 * expected by the Causeway D3 extension.  The diagram is regenerated on <em>every</em> call —
 * it is never cached — because its content reflects the current candidate set and their reviews,
 * both of which change as candidates are reviewed, approved, or rejected.
 */
@Named(DomainModule.NAMESPACE + ".DomainModelDiagramsD3")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class DomainModelDiagramsD3 {

    private static final Logger log = LoggerFactory.getLogger(DomainModelDiagramsD3.class);

    @Programmatic
    public String renderDiagram(final DomainModel model) {
        if (model == null) {
            return null;
        }
        final long id = model.getId() != null ? model.getId() : -1L;
        log.debug("Rendering domain-model D3 diagram for analysis #{}", id);
        try {
            final List<Candidate> allCandidates = candidatesOf(model);
            if (allCandidates.isEmpty()) {
                log.warn("Domain model #{} has no candidates on its lists — nothing to diagram", id);
                return null;
            }
            log.debug("Domain model #{}: {} total candidates", id, allCandidates.size());
            final List<Candidate> approved = approvedCandidates(allCandidates);
            if (approved.isEmpty()) {
                log.warn("Domain model #{}: {} candidates found but NONE are approved (no Review with APPROVED status) — nothing to diagram",
                        id, allCandidates.size());
                return null;
            }
            log.debug("Domain model #{}: {} approved candidates", id, approved.size());
            final Set<String> candidateLemmas = candidateLemmas(allCandidates);
            final Map<String, List<Candidate>> candidatesByLemma = candidatesByLemma(allCandidates);
            final List<TypedDependency> dependencies = collectDependencies(
                    approved, candidateLemmas, candidatesByLemma, model.getHopDepth());

            // Every lemma that becomes a node: approved seeds plus endpoints of the collected
            // dependencies (the BFS expansion ensures both sides are candidate words).
            final Set<String> nodeLemmas = new HashSet<>();
            for (final Candidate a : approved) {
                nodeLemmas.add(lower(a.getCandidateName()));
            }
            for (final TypedDependency td : dependencies) {
                nodeLemmas.add(lower(td.getGovernorLemma()));
                nodeLemmas.add(lower(td.getDependentLemma()));
            }

            // Map each lemma back to the first candidate's original display name (proper casing).
            final Map<String, String> displayName = new HashMap<>();
            for (final String lemma : nodeLemmas) {
                final List<Candidate> cs = candidatesByLemma.get(lemma);
                displayName.put(lemma, cs != null && !cs.isEmpty() ? cs.getFirst().getCandidateName() : lemma);
            }

            // Build {"nodes": [{"id":"..."},...], "links":[{"source":"...","target":"..."},...]}
            final StringBuilder json = new StringBuilder();
            json.append('{');
            json.append("\"nodes\":[");
            boolean first = true;
            for (final String lemma : nodeLemmas) {
                if (!first) {
                    json.append(',');
                }
                json.append("{\"id\":\"");
                json.append(escapeJson(displayName.get(lemma)));
                json.append("\"}");
                first = false;
            }
            json.append("],");
            json.append("\"links\":[");
            first = true;
            for (final TypedDependency td : dependencies) {
                if (!first) {
                    json.append(',');
                }
                json.append("{\"source\":\"");
                json.append(escapeJson(displayName.get(lower(td.getGovernorLemma()))));
                json.append("\",\"target\":\"");
                json.append(escapeJson(displayName.get(lower(td.getDependentLemma()))));
                json.append("\"}");
                first = false;
            }
            json.append("]");
            json.append('}');

            final String result = json.toString();
            log.debug("D3 JSON for analysis #{}: {}", id, result);
            return result;
        } catch (Exception e) {
            log.warn("Failed to render domain-model D3 diagram for analysis #{}: {}", id, e.getMessage());
            log.debug("Failure rendering domain-model D3 diagram for analysis #{}", id, e);
            return null;
        }
    }

    /**
     * Every candidate owned by the model, in a single snapshot.  Mirrors the per-type candidate
     * aggregation performed by {@link domox.dom.rqm.Documents#candidates(DomainModel)}.
     */
    private static List<Candidate> candidatesOf(final DomainModel model) {
        final List<Candidate> all = new ArrayList<>();
        if (model.classList != null) {
            all.addAll(model.classList);
        }
        if (model.actionList != null) {
            all.addAll(model.actionList);
        }
        if (model.propertyList != null) {
            all.addAll(model.propertyList);
        }
        if (model.associationList != null) {
            all.addAll(model.associationList);
        }
        return all;
    }

    /**
     * The approved subset of the candidate set — those carrying at least one {@link Review} whose
     * status is {@link ReviewStatus#APPROVED}.  These are the seeds the diagram expands outward
     * from, kept in their original order.
     */
    private static List<Candidate> approvedCandidates(final List<Candidate> candidates) {
        return candidates.stream()
                .filter(DomainModelDiagramsD3::isApproved)
                .toList();
    }

    private static boolean isApproved(final Candidate candidate) {
        return candidate.getReviews() != null
                && candidate.getReviews().stream()
                        .anyMatch(review -> review != null && review.getStatus() == ReviewStatus.APPROVED);
    }

    /**
     * The lower-cased candidate names of the snapshot.  The whitelist spans <em>every</em>
     * candidate of the model (not just the approved seeds) so the expansion can travel through any
     * candidate word and draw approved candidates together with their un-approved neighbours.
     */
    private static Set<String> candidateLemmas(final List<Candidate> allCandidates) {
        final Set<String> lemmas = new HashSet<>();
        allCandidates.forEach(c -> lemmas.add(lower(c.getCandidateName())));
        return lemmas;
    }

    /**
     * Lower-case candidate name → every candidate carrying that name.  A single word can exist
     * more than once (e.g. the same name across candidate types), and each such candidate
     * contributes its own {@link RuleMatch}es to the expansion.
     */
    private static Map<String, List<Candidate>> candidatesByLemma(final List<Candidate> allCandidates) {
        final Map<String, List<Candidate>> byLemma = new HashMap<>();
        allCandidates.forEach(c ->
                byLemma.computeIfAbsent(lower(c.getCandidateName()), k -> new ArrayList<>()).add(c));
        return byLemma;
    }

    /**
     * Breadth-first traversal of the candidate-connection graph starting from the {@code seeds}
     * (the model's approved candidates), up to {@code maxDepth} hops, returning the <em>distinct</em>
     * candidate-restricted dependencies.
     * <p>
     * Round 0 expands the approved seeds themselves (their direct connections); round 1 expands
     * each directly-connected candidate (their connections); and so on.  Every dependency kept is
     * restricted to candidate words, and candidates already expanded in an earlier round are never
     * re-expanded, which bounds the traversal even when the candidate graph contains cycles.
     */
    private static List<TypedDependency> collectDependencies(final List<Candidate> seeds,
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
                for (final RuleMatch ruleMatch : candidate.getRuleMatches()) {
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

    /** Adds every candidate carrying {@code lemma} that has not yet been expanded to the next frontier. */
    private static void addCandidatesForLemma(final String lemma,
                                              final Map<String, List<Candidate>> candidatesByLemma,
                                              final Set<Candidate> visited,
                                              final Set<Candidate> nextFrontier) {
        for (final Candidate candidate : candidatesByLemma.getOrDefault(lower(lemma), List.of())) {
            if (!visited.contains(candidate)) {
                nextFrontier.add(candidate);
            }
        }
    }

    /**
     * Keeps only those typed dependencies whose governor <em>and</em> dependent lemma both
     * correspond to a candidate word — mirroring the candidate-word restriction applied by
     * {@link domox.dom.rqm.Documents#renderDiagram} and {@link CandidateDiagrams}.
     */
    private static boolean bothEndpointsAreCandidates(final TypedDependency td, final Set<String> candidateLemmas) {
        return candidateLemmas.contains(lower(td.getGovernorLemma()))
                && candidateLemmas.contains(lower(td.getDependentLemma()));
    }

    private static String lower(final String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /**
     * Minimal JSON string escaping: backslash, double-quote, and control characters.
     */
    private static String escapeJson(final String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        final StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' ->  out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}