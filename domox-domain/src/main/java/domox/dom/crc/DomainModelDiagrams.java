package domox.dom.crc;

import domox.Constants;
import domox.DomainModule;
import domox.diagram.DiagramBuilder;
import domox.dom.nlp.LexicalGraphGenerator;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.value.Blob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders a {@link DomainModel}-wide <em>lexical dependency</em> summary diagram (Kroki → PDF)
 * seeded from the model's <em>approved</em> candidates, comparable to the per-candidate
 * {@link CandidateDiagrams#renderDiagram(Candidate)} but scoped to the whole analysis run.
 * <p>
 * The diagram is built by a breadth-first expansion of the candidate-connection graph starting
 * from every approved candidate of the model (a candidate is approved when it carries at least
 * one {@link Review} whose status is {@link ReviewStatus#APPROVED}), up to
 * {@link DomainModel#getHopDepth()} hops deep (default {@code 1}).  Seeding from the approved set
 * keeps the starting graph deliberately small so it renders reliably; the depth is increased by
 * the user when a wider view of the model is wanted.  From each frontier candidate's own
 * {@link RuleMatch}es, only dependencies whose governor <em>and</em> dependent lemma both
 * correspond to a candidate word are kept, so the retained edges draw the approved candidates
 * together with every candidate directly (or, at higher depths, transitively) connected to them.
 * <p>
 * The diagram is regenerated on <em>every</em> call — it is never cached — because its content
 * reflects the current candidate set and their reviews, both of which change as candidates are
 * reviewed, approved, or rejected.
 */
@Named(DomainModule.NAMESPACE + ".DomainModelDiagrams")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class DomainModelDiagrams {

    private static final Logger log = LoggerFactory.getLogger(DomainModelDiagrams.class);

    private final DiagramBuilder diagramBuilder;

    @Inject
    public DomainModelDiagrams(final DiagramBuilder diagramBuilder) {
        this.diagramBuilder = diagramBuilder;
    }

    /**
     * Renders a freshly generated PDF {@link Blob} for the lexical dependency graph seeded from
     * the {@link DomainModel}'s approved candidates, expanded {@link DomainModel#getHopDepth()}
     * hops deep, or {@code null} when the model is {@code null}, holds no candidates, holds no
     * approved candidates, or the render fails (e.g. Kroki unavailable).
     *
     * @param model the analysis run whose approved-candidate neighbourhood is to be diagrammed
     */
    @Programmatic
    public Blob renderDiagram(final DomainModel model) {
        if (model == null) {
            return null;
        }
        final long id = model.getId() != null ? model.getId() : -1L;
        log.debug("Rendering domain-model diagram for analysis #{}", id);
        String dotCode = null;
        try {
            // Snapshot the model's candidates once per render: the lemma whitelist, the per-lemma
            // candidate lookups and the approved-seed set are all derived from the same view, so
            // the graph is built against a consistent candidate set.
            final List<Candidate> allCandidates = candidatesOf(model);
            if (allCandidates.isEmpty()) {
                log.debug("Domain model #{} has no candidates — nothing to diagram", id);
                return null;
            }
            final List<Candidate> approved = approvedCandidates(allCandidates);
            if (approved.isEmpty()) {
                log.debug("Domain model #{} has no approved candidates — nothing to diagram", id);
                return null;
            }
            final Set<String> candidateLemmas = candidateLemmas(allCandidates);
            final Map<String, List<Candidate>> candidatesByLemma = candidatesByLemma(allCandidates);
            final List<TypedDependency> dependencies = collectDependencies(
                    approved, candidateLemmas, candidatesByLemma, model.getHopDepth());

            // Style the node borders from the approved subset: the approved seeds (and any
            // approved candidate reached during expansion) are drawn with a bold border.
            dotCode = new LexicalGraphGenerator().generateGraphvizGraph(dependencies, approved);
            final byte[] bytes = diagramBuilder.buildLexicalGraphDiagram(dotCode);
            final String fileName = "DomainModel-" + id + "-diagram.pdf";
            return new Blob(fileName, Constants.pdfMimeType, bytes);
        } catch (Exception e) {
            log.warn("Failed to render domain-model diagram for analysis #{}: {}", id, e.getMessage());
            if (dotCode != null) {
                log.debug("Domain-model diagram DOT for analysis #{}:\n{}", id, dotCode);
            }
            log.debug("Failure rendering domain-model diagram for analysis #{}", id, e);
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
                .filter(DomainModelDiagrams::isApproved)
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
}