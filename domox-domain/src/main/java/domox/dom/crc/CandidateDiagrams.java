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

import java.util.*;

/**
 * Renders a candidate-centric <em>lexical dependency</em> diagram (Kroki → PDF) for a single
 * {@link Candidate}, comparable to {@link domox.dom.rqm.Documents#renderDiagram} but scoped to
 * one candidate.
 * <p>
 * The diagram is built by a breadth-first expansion of the candidate-connection graph starting
 * from the candidate, {@link Candidate#getHopDepth()} hops deep (default {@code 1}): from the
 * candidate's own {@link RuleMatch}es, only dependencies whose governor <em>and</em> dependent
 * lemma both correspond to a candidate word are kept.  The retained edges therefore draw the
 * candidate together with every candidate directly connected to it.
 * <p>
 * The diagram is regenerated on <em>every</em> call — it is never cached — because its content
 * reflects the current candidate set, which changes as candidates are reviewed, approved, or
 * rejected.
 */
@Named(DomainModule.NAMESPACE + ".CandidateDiagrams")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class CandidateDiagrams {

    private static final Logger log = LoggerFactory.getLogger(CandidateDiagrams.class);

    private final DiagramBuilder diagramBuilder;
    private final ClassCandidates classCandidates;
    private final ActionCandidates actionCandidates;
    private final PropertyCandidates propertyCandidates;
    private final AssociationCandidates associationCandidates;

    @Inject
    public CandidateDiagrams(
            DiagramBuilder diagramBuilder,
            ClassCandidates classCandidates,
            ActionCandidates actionCandidates,
            PropertyCandidates propertyCandidates,
            AssociationCandidates associationCandidates) {
        this.diagramBuilder = diagramBuilder;
        this.classCandidates = classCandidates;
        this.actionCandidates = actionCandidates;
        this.propertyCandidates = propertyCandidates;
        this.associationCandidates = associationCandidates;
    }

    /**
     * Renders a freshly generated PDF {@link Blob} for the candidate-centric lexical dependency
     * graph of {@code candidate} — the candidate and every candidate directly connected to it
     * (see {@link Candidate#getHopDepth()}) — or {@code null} when the candidate is
     * {@code null} or the render fails (e.g. Kroki unavailable).
     */
    @Programmatic
    public Blob renderDiagram(final Candidate candidate) {
        if (candidate == null) {
            return null;
        }
        final long id = candidate.getId() != null ? candidate.getId() : -1L;
        final String name = candidate.getCandidateName();
        log.debug("Rendering candidate diagram for candidate #{} '{}'", id, name);
        String dotCode = null;
        try {
            // Snapshot the current candidate set once per render: the lemma whitelist and the
            // per-lemma candidate lookups are derived from the same lists, so the graph is built
            // against a consistent candidate set.
            final List<Candidate> allCandidates = allCandidates();
            final Set<String> candidateLemmas = candidateLemmas(allCandidates);
            final Map<String, List<Candidate>> candidatesByLemma = candidatesByLemma(allCandidates);
            final List<TypedDependency> dependencies = collectDependencies(
                    candidate, candidateLemmas, candidatesByLemma, candidate.getHopDepth());
            dotCode = new LexicalGraphGenerator().generateGraphvizGraph(dependencies, allCandidates);
            final byte[] bytes = diagramBuilder.buildLexicalGraphDiagram(dotCode);
            final String fileName = candidate.getCandidateName() + "-diagram.pdf";
            return new Blob(fileName, Constants.pdfMimeType, bytes);
        } catch (Exception e) {
            log.warn("Failed to render candidate diagram for candidate #{} '{}': {}",
                    id, name, e.getMessage());
            if (dotCode != null) {
                log.debug("Candidate diagram DOT for candidate #{} '{}':\n{}", id, name, dotCode);
            }
            log.debug("Failure rendering candidate diagram for candidate #{} '{}'", id, name, e);
            return null;
        }
    }

    /**
     * All candidates of every type currently present, in a single snapshot.  The per-render
     * lemma whitelist and the per-lemma candidate lookups used by the breadth-first expansion
     * are both derived from this one snapshot.
     */
    private List<Candidate> allCandidates() {
        final List<Candidate> all = new ArrayList<>();
        all.addAll(classCandidates.listAll());
        all.addAll(actionCandidates.listAll());
        all.addAll(propertyCandidates.listAll());
        all.addAll(associationCandidates.listAll());
        return all;
    }

    /**
     * The lower-cased candidate names of the snapshot.  The candidate whose diagram is being
     * rendered is itself a member of the snapshot, so its own word is always an allowed lemma.
     */
    private static Set<String> candidateLemmas(final List<Candidate> allCandidates) {
        final Set<String> lemmas = new HashSet<>();
        allCandidates.forEach(c -> lemmas.add(lower(c.getCandidateName())));
        return lemmas;
    }

    /**
     * Lower-case candidate name → every candidate carrying that name.  A single word can exist
     * more than once (e.g. the same name across candidate types or domain models), and each such
     * candidate contributes its own {@link RuleMatch}es to the expansion.
     */
    private static Map<String, List<Candidate>> candidatesByLemma(final List<Candidate> allCandidates) {
        final Map<String, List<Candidate>> byLemma = new HashMap<>();
        allCandidates.forEach(c ->
                byLemma.computeIfAbsent(lower(c.getCandidateName()), k -> new ArrayList<>()).add(c));
        return byLemma;
    }

    /**
     * Breadth-first traversal of the candidate-connection graph starting from {@code seed}, up to
     * {@code maxDepth} hops, returning the <em>distinct</em> candidate-restricted dependencies.
     * <p>
     * Round 0 expands the candidate itself (its direct connections); round 1 expands each
     * directly-connected candidate (their connections); and so on.  Every dependency kept is
     * restricted to candidate words, and candidates already expanded in an earlier round are
     * never re-expanded, which bounds the traversal even when the candidate graph contains
     * cycles.
     */
    private static List<TypedDependency> collectDependencies(
            final Candidate seed,
            final Set<String> candidateLemmas,
            final Map<String, List<Candidate>> candidatesByLemma,
            final int maxDepth) {
        final List<TypedDependency> dependencies = new ArrayList<>();
        final Set<TypedDependency> seen = new HashSet<>();
        final Set<Candidate> visited = new HashSet<>();
        Set<Candidate> frontier = Set.of(seed);
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
    private static void addCandidatesForLemma(
            final String lemma,
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
     * correspond to a candidate word — the candidate itself plus every related candidate looked
     * up from the candidate lists.  Mirrors the candidate-word restriction applied per document
     * by {@link domox.dom.rqm.Documents#renderDiagram}.
     */
    private static boolean bothEndpointsAreCandidates(final TypedDependency td,
                                                      final Set<String> candidateLemmas) {
        return candidateLemmas.contains(lower(td.getGovernorLemma()))
                && candidateLemmas.contains(lower(td.getDependentLemma()));
    }

    private static String lower(final String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}