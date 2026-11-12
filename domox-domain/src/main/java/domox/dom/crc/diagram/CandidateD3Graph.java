package domox.dom.crc.diagram;
import domox.DomainModule;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.service.AttributeCandidates;
import domox.dom.crc.service.ClassCandidates;
import domox.dom.crc.service.CollaboratorCandidates;
import domox.dom.crc.service.ResponsibilityCandidates;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Renders a candidate-centric <em>dependency neighbourhood</em> as a <b>D3 force-directed
 * graph JSON string</b> for a single {@link Candidate}, analogous to
 * {@link DomainModelDiagramsD3#renderDiagram(DomainModel)} (which covers the whole
 * {@link DomainModel}) and parallel to {@link CandidateDiagrams#renderDiagram(Candidate)}
 * (which produces a Kroki → PDF rendering of the same lexical-dependency neighbourhood).
 * <p>
 * The graph is a <em>cyclic</em> node-link graph: the nodes are candidate words, and the links
 * are the typed dependencies of the candidates' {@link RuleMatch}es.  It is built by a
 * breadth-first expansion of the candidate-connection graph starting from the candidate,
 * {@link Candidate#getHopDepth()} hops deep (default {@code 1}): from each frontier candidate's
 * own {@link RuleMatch}es, only dependencies whose governor <em>and</em> dependent lemma both
 * correspond to a candidate word are kept, so the retained edges draw the candidate together
 * with every candidate directly (or, at higher depths, transitively) connected to it — the same
 * neighbourhood that {@link CandidateDiagrams} renders as PDF.
 * <p>
 * Synthetic association-artifact compounds of the form {@code "X_Y"} are excluded from the
 * diagram exactly as in {@link DomainModelDiagramsD3}: the participant lemmas and their typed
 * dependency link are kept; only the compound node is removed.  (Should the candidate itself be
 * such a compound, its own word is nevertheless kept so the graph never renders empty.)
 * <p>
 * The result is a {@link String} containing a JSON object with the following structure:
 * <pre>{@code {
 *   "nodes": [
 *     {"id":"store","name":"Store","subdomain":"Inventory","pos":"NN","color":"#3498DB",
 *      "r":12,"approved":true,"candidateId":123,"objectType":"domox.ClassCdd"}
 *   ],
 *   "links": [
 *     {"source":"store","target":"customer","label":"nsubj"}
 *   ],
 *   "legend": [
 *     {"color":"#3498DB","label":"Noun","description":"Entities and objects"},
 *     ...
 *   ]
 * }}</pre>
 * This format is consumed by the client-side D3 force-layout renderer in
 * {@code d3-integration.mjs}.
 * <p>
 * The graph is regenerated on <em>every</em> call — it is never cached — because its content
 * reflects the current candidate set, which changes as candidates are reviewed, approved, or
 * rejected.
 */
@Named(DomainModule.NAMESPACE + ".CandidateD3Graph")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class CandidateD3Graph {

    private static final Logger log = LoggerFactory.getLogger(CandidateD3Graph.class);

    private final ClassCandidates classCandidates;
    private final ResponsibilityCandidates responsibilityCandidates;
    private final AttributeCandidates attributeCandidates;
    private final CollaboratorCandidates collaboratorCandidates;

    @Inject
    public CandidateD3Graph(
            final ClassCandidates classCandidates,
            final ResponsibilityCandidates responsibilityCandidates,
            final AttributeCandidates attributeCandidates,
            final CollaboratorCandidates collaboratorCandidates) {
        this.classCandidates = classCandidates;
        this.responsibilityCandidates = responsibilityCandidates;
        this.attributeCandidates = attributeCandidates;
        this.collaboratorCandidates = collaboratorCandidates;
    }

    /**
     * Renders a freshly generated D3 force-directed graph JSON string of {@code candidate}'s
     * dependency neighbourhood (see class javadoc), or {@code null} when the candidate is
     * {@code null} or the render fails.
     */
    @Programmatic
    public String renderGraph(final Candidate candidate) {
        if (candidate == null) {
            return null;
        }
        final long id = candidate.getId() != null ? candidate.getId() : -1L;
        final String name = candidate.getCandidateName();
        log.debug("Rendering D3 graph for candidate #{} '{}'", id, name);
        try {
            final String json = buildD3Json(candidate);
            log.debug("D3 JSON ({} chars): {}", json.length(), json);
            return json;
        } catch (Exception e) {
            log.warn("Failed to render D3 graph for candidate #{} '{}'", id, name, e);
            return null;
        }
    }

    // ============ JSON assembly ============

    /**
     * Assembles the D3 JSON object with {@code "nodes"}, {@code "links"}, and {@code "legend"}
     * arrays.
     */
    private String buildD3Json(final Candidate candidate) {
        // Snapshot the candidate set once per render.
        final List<Candidate> allCandidates = allCandidates();
        final Set<String> candidateLemmas = CandidateNeighbourhood.candidateLemmas(allCandidates);
        final Map<String, List<Candidate>> candidatesByLemma = CandidateNeighbourhood.candidatesByLemma(allCandidates);

        // Breadth-first expansion of the candidate-connection graph.
        final List<TypedDependency> dependencies = CandidateNeighbourhood.collectDependencies(
                List.of(candidate), candidateLemmas, candidatesByLemma, candidate.getHopDepth());

        // Remove association-artifact edges.
        final Set<String> artifactLemmas = CandidateNeighbourhood.associationArtifactLemmas(allCandidates, candidateLemmas);
        CandidateNeighbourhood.removeArtifactEdges(dependencies, artifactLemmas);

        // Per-lemma metadata: POS codes and rule-match counts.
        final CandidateNeighbourhood.LemmaMetrics metrics = CandidateNeighbourhood.lemmaMetrics(dependencies);
        final Map<String, Set<String>> posByLemma = metrics.posByLemma();
        final Map<String, Integer> countByLemma = metrics.matchCountByLemma();

        // Every lemma that becomes a node.
        final Set<String> nodeLemmas = new LinkedHashSet<>();
        nodeLemmas.add(DiagramLemmas.lower(candidate.getCandidateName()));
        for (final TypedDependency td : dependencies) {
            if (td == null) continue;
            nodeLemmas.add(DiagramLemmas.lower(td.getGovernorLemma()));
            nodeLemmas.add(DiagramLemmas.lower(td.getDependentLemma()));
        }
        nodeLemmas.removeAll(artifactLemmas);
        if (nodeLemmas.isEmpty()) {
            nodeLemmas.add(DiagramLemmas.lower(candidate.getCandidateName()));
        }

        final int maxCount = countByLemma.values().stream()
                .mapToInt(Integer::intValue).max().orElse(DiagramNodeSizing.MIN_MATCH_COUNT);

        // Lemma → display name (original casing).
        final Map<String, String> displayName = new HashMap<>();
        final String seedName = candidate.getCandidateName();
        displayName.put(DiagramLemmas.lower(seedName), seedName != null ? seedName : "");
        for (final String lemma : nodeLemmas) {
            final List<Candidate> cs = candidatesByLemma.get(lemma);
            if (cs != null && !cs.isEmpty()) {
                displayName.put(lemma, cs.getFirst().getCandidateName());
            }
        }

        // Lemma → subdomain name (from ClassCdd).
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

        // Lemma → candidate ID and object type for click-through.
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

        // Lemma → whether it has at least one approved review.
        final Set<String> approvedLemmas = CandidateNeighbourhood.approvedLemmas(allCandidates);

        // Build the JSON document.
        final StringBuilder json = new StringBuilder();
        json.append('{');
        appendNodes(json, nodeLemmas, posByLemma, countByLemma, maxCount,
                approvedLemmas, displayName, subdomainByLemma,
                candidateIdByLemma, objectTypeByLemma);
        json.append(',');
        appendLinks(json, dependencies);
        json.append(',');
        final Set<String> usedPosCodes = new HashSet<>();
        for (final Set<String> posSet : posByLemma.values()) {
            if (posSet != null) usedPosCodes.addAll(posSet);
        }
        PosLegend.appendLegendJson(json, usedPosCodes);
        json.append('}');

        return json.toString();
    }

    // ============ JSON generation helpers ============

    /**
     * Appends the {@code "nodes"} array. Each node: id, name, subdomain, pos, color, r,
     * approved, candidateId, objectType, isCandidateColumn.
     */
    private static void appendNodes(final StringBuilder json,
                                    final Set<String> nodeLemmas,
                                    final Map<String, Set<String>> posByLemma,
                                    final Map<String, Integer> countByLemma,
                                    final int maxCount,
                                    final Set<String> approvedLemmas,
                                    final Map<String, String> displayName,
                                    final Map<String, String> subdomainByLemma,
                                    final Map<String, Long> candidateIdByLemma,
                                    final Map<String, String> objectTypeByLemma) {
        json.append("\"nodes\":[");
        boolean first = true;
        for (final String lemma : nodeLemmas) {
            if (!first) json.append(',');
            final String pos = DiagramLemmas.firstPos(posByLemma.get(lemma));
            final int count = countByLemma.getOrDefault(lemma, DiagramNodeSizing.MIN_MATCH_COUNT);
            final int radius = DiagramNodeSizing.radiusFor(count, maxCount);
            final boolean approved = approvedLemmas.contains(lemma);
            final boolean hasCandidateLink = candidateIdByLemma.containsKey(lemma)
                    && objectTypeByLemma.containsKey(lemma);
            json.append('{');
            json.append("\"id\":\"").append(DiagramJson.escape(lemma)).append('"');
            json.append(",\"name\":\"").append(DiagramJson.escape(displayName.getOrDefault(lemma, lemma))).append('"');
            final String sd = subdomainByLemma.getOrDefault(lemma, "");
            if (!sd.isEmpty()) {
                json.append(",\"subdomain\":\"").append(DiagramJson.escape(sd)).append('"');
            }
            json.append(",\"pos\":\"").append(DiagramJson.escape(pos)).append('"');
            json.append(",\"color\":\"").append(PosPalette.color(pos)).append('"');
            json.append(",\"r\":").append(radius);
            json.append(",\"approved\":").append(approved);
            json.append(",\"isCandidateColumn\":").append(hasCandidateLink);
            if (hasCandidateLink) {
                json.append(",\"candidateId\":").append(candidateIdByLemma.get(lemma));
                json.append(",\"objectType\":\"").append(DiagramJson.escape(objectTypeByLemma.get(lemma))).append('"');
            }
            json.append('}');
            first = false;
        }
        json.append(']');
    }

    /**
     * Appends the {@code "links"} array. Each link: source, target, optional label.
     */
    private static void appendLinks(final StringBuilder json,
                                    final List<TypedDependency> dependencies) {
        json.append("\"links\":[");
        boolean first = true;
        for (final TypedDependency td : dependencies) {
            if (td == null) continue;
            if (!first) json.append(',');
            final String src = DiagramLemmas.lower(td.getGovernorLemma());
            final String tgt = DiagramLemmas.lower(td.getDependentLemma());
            json.append('{');
            json.append("\"source\":\"").append(DiagramJson.escape(src)).append('"');
            json.append(",\"target\":\"").append(DiagramJson.escape(tgt)).append('"');
            if (td.getType() != null) {
                json.append(",\"label\":\"").append(DiagramJson.escape(td.getType().getCode())).append('"');
            }
            json.append('}');
            first = false;
        }
        json.append(']');
    }

    // ============ Candidate neighbourhood ============

    /** All candidates of every type currently present, in a single snapshot. */
    private List<Candidate> allCandidates() {
        final List<Candidate> all = new ArrayList<>();
        CandidateNeighbourhood.addAll(all, classCandidates.listAll());
        CandidateNeighbourhood.addAll(all, responsibilityCandidates.listAll());
        CandidateNeighbourhood.addAll(all, attributeCandidates.listAll());
        CandidateNeighbourhood.addAll(all, collaboratorCandidates.listAll());
        return all;
    }

}
