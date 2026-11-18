package domox.dom.crc.diagram;

import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.service.AttributeCandidates;
import domox.dom.crc.service.ClassCandidates;
import domox.dom.crc.service.CollaboratorCandidates;
import domox.dom.crc.service.ResponsibilityCandidates;
import domox.dom.nlp.TypedDependency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The <em>candidate graph model</em>: everything a lexical diagram needs to know about one
 * candidate neighbourhood <em>before</em> it is written out.
 * <p>
 * A lexical diagram is a node-link graph whose nodes are candidate words and whose links are the
 * typed dependencies of the candidates' {@code RuleMatch}es.  Three renderers draw one
 * ({@link CandidateD3Graph} and {@link CandidateVegaGraph} per candidate,
 * {@link DomainModelDiagramsD3} for a whole model), and each of them used to carry its own copy of
 * the same ~60 lines of assembly — the breadth-first expansion, the artifact-compound removal, the
 * node set, the display names, the sub-domain names, the click-through ids and the approval flags.
 * A change to any of those had to be made three times, and a fix applied to one copy (the
 * "never render empty" fallback, say) could silently not reach the others.
 * <p>
 * This record is the single source of that assembly.  {@link #of} is pure given its arguments —
 * it touches no repository and no framework — so it is unit-testable with plain
 * {@link Candidate}s, while {@link #snapshot} is the one place that knows how to collect the
 * candidate set from the four candidate repositories.
 */
public record CandidateGraphModel(
        List<TypedDependency> dependencies,
        Set<String> nodeLemmas,
        Map<String, Set<String>> posByLemma,
        Map<String, Integer> countByLemma,
        int maxCount,
        Map<String, String> displayName,
        Map<String, String> subdomainByLemma,
        Map<String, Long> candidateIdByLemma,
        Map<String, String> objectTypeByLemma,
        Set<String> approvedLemmas) {

    /**
     * The snapshot of every candidate of every type currently present, collected from the four
     * candidate repositories in one pass.
     * <p>
     * The per-render lemma whitelist and the per-lemma candidate look-ups are all derived from this
     * one list, so the graph is always built against a consistent candidate set.
     */
    public static List<Candidate> snapshot(
            final ClassCandidates classCandidates,
            final ResponsibilityCandidates responsibilityCandidates,
            final AttributeCandidates attributeCandidates,
            final CollaboratorCandidates collaboratorCandidates) {
        final List<Candidate> all = new ArrayList<>();
        CandidateNeighbourhood.addAll(all, classCandidates.listAll());
        CandidateNeighbourhood.addAll(all, responsibilityCandidates.listAll());
        CandidateNeighbourhood.addAll(all, attributeCandidates.listAll());
        CandidateNeighbourhood.addAll(all, collaboratorCandidates.listAll());
        return all;
    }

    /**
     * Builds the model of the {@code maxDepth}-hop neighbourhood of {@code seeds} against
     * {@code allCandidates}.
     *
     * @param seeds         the candidates the breadth-first expansion starts from — one for a
     *                      per-candidate diagram, the model's approved candidates for the model-wide one
     * @param maxDepth      the hop depth the expansion reaches ({@code Candidate#getHopDepth()} for a
     *                      per-candidate diagram)
     * @param allCandidates the full candidate snapshot the seeds belong to
     */
    public static CandidateGraphModel of(final List<Candidate> seeds,
                                         final int maxDepth,
                                         final List<Candidate> allCandidates) {
        final Set<String> candidateLemmas = CandidateNeighbourhood.candidateLemmas(allCandidates);
        final Map<String, List<Candidate>> candidatesByLemma = CandidateNeighbourhood.candidatesByLemma(allCandidates);

        // Breadth-first expansion of the candidate-connection graph, restricted to candidate words.
        final List<TypedDependency> dependencies = CandidateNeighbourhood.collectDependencies(
                seeds, candidateLemmas, candidatesByLemma, maxDepth);

        // Remove association-artifact edges — synthetic "X_Y" compound nodes: the participant
        // lemmas and their typed dependency link are kept; only the compound is dropped.
        final Set<String> artifactLemmas = CandidateNeighbourhood.associationArtifactLemmas(allCandidates, candidateLemmas);
        CandidateNeighbourhood.removeArtifactEdges(dependencies, artifactLemmas);

        // Per-lemma metadata: POS codes and rule-match counts.
        final CandidateNeighbourhood.LemmaMetrics metrics = CandidateNeighbourhood.lemmaMetrics(dependencies);
        final Map<String, Set<String>> posByLemma = metrics.posByLemma();
        final Map<String, Integer> countByLemma = metrics.matchCountByLemma();

        // Every lemma that becomes a node: the seeds plus the endpoints of the retained dependencies.
        final Set<String> seedLemmas = new LinkedHashSet<>();
        for (final Candidate seed : seeds) {
            seedLemmas.add(DiagramLemmas.lower(seed.getCandidateName()));
        }
        final Set<String> nodeLemmas = new LinkedHashSet<>(seedLemmas);
        for (final TypedDependency td : dependencies) {
            if (td == null) continue;
            nodeLemmas.add(DiagramLemmas.lower(td.getGovernorLemma()));
            nodeLemmas.add(DiagramLemmas.lower(td.getDependentLemma()));
        }
        nodeLemmas.removeAll(artifactLemmas);
        if (nodeLemmas.isEmpty()) {
            // Every seed word was an artifact compound and every edge touching it was removed: fall
            // back to the seeds themselves so the graph never renders empty.
            nodeLemmas.addAll(seedLemmas);
        }

        final int maxCount = countByLemma.values().stream()
                .mapToInt(Integer::intValue).max().orElse(DiagramNodeSizing.MIN_MATCH_COUNT);

        // Lemma -> display name (original casing).  Primed from the seeds themselves — a seed need
        // not be part of the repository snapshot in every caller — then overwritten from the snapshot.
        final Map<String, String> displayName = new HashMap<>();
        for (final Candidate seed : seeds) {
            displayName.put(DiagramLemmas.lower(seed.getCandidateName()),
                    seed.getCandidateName() != null ? seed.getCandidateName() : "");
        }
        for (final String lemma : nodeLemmas) {
            final List<Candidate> cs = candidatesByLemma.get(lemma);
            displayName.put(lemma, cs != null && !cs.isEmpty()
                    ? cs.getFirst().getCandidateName()
                    : displayName.getOrDefault(lemma, lemma));
        }

        // Lemma -> sub-domain name (from ClassCdd, set during review).
        final Map<String, String> classSubdomainByLemma = new HashMap<>();
        for (final Candidate c : allCandidates) {
            if (c instanceof ClassCdd cc && cc.getSubDomain() != null
                    && cc.getSubDomain().getName() != null) {
                classSubdomainByLemma.put(DiagramLemmas.lower(c.getCandidateName()), cc.getSubDomain().getName());
            }
        }
        final Map<String, String> subdomainByLemma = new HashMap<>();
        for (final String lemma : nodeLemmas) {
            final String sdName = classSubdomainByLemma.get(lemma);
            subdomainByLemma.put(lemma, sdName != null ? sdName : "");
        }

        // Lemma -> candidate ID and object type, for click-through links.
        final Map<String, Long> candidateIdByLemma = new HashMap<>();
        final Map<String, String> objectTypeByLemma = new HashMap<>();
        for (final String lemma : nodeLemmas) {
            final List<Candidate> cs = candidatesByLemma.get(lemma);
            if (cs != null && !cs.isEmpty()) {
                final Candidate c = cs.getFirst();
                candidateIdByLemma.put(lemma, c.getId());
                objectTypeByLemma.put(lemma, c.getObjectType());
            }
        }

        // Lemma -> whether it has at least one approved review (bold border).
        final Set<String> approvedLemmas = CandidateNeighbourhood.approvedLemmas(allCandidates);

        return new CandidateGraphModel(dependencies, nodeLemmas, posByLemma, countByLemma, maxCount,
                displayName, subdomainByLemma, candidateIdByLemma, objectTypeByLemma, approvedLemmas);
    }

    /**
     * The POS codes that actually appear in the diagram, for the legend — collected in the order
     * {@code posByLemma} encountered them, so node fills and the legend always agree.
     */
    public Set<String> usedPosCodes() {
        final Set<String> usedPosCodes = new HashSet<>();
        for (final Set<String> posSet : posByLemma.values()) {
            if (posSet != null) usedPosCodes.addAll(posSet);
        }
        return usedPosCodes;
    }
}
