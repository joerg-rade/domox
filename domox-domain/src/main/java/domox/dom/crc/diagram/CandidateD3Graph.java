package domox.dom.crc.diagram;
import domox.DomainModule;
import domox.dom.crc.model.Candidate;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;

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
@Slf4j
@Named(DomainModule.NAMESPACE + ".CandidateD3Graph")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class CandidateD3Graph {

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
        // The neighbourhood, the node set and every per-lemma decoration are assembled by the
        // shared candidate-graph model (see CandidateGraphModel).
        final CandidateGraphModel model = CandidateGraphModel.of(
                List.of(candidate), candidate.getHopDepth(), allCandidates());

        // Build the JSON document.
        final StringBuilder json = new StringBuilder();
        json.append('{');
        appendNodes(json, model.nodeLemmas(), model.posByLemma(), model.countByLemma(), model.maxCount(),
                model.approvedLemmas(), model.displayName(), model.subdomainByLemma(),
                model.candidateIdByLemma(), model.objectTypeByLemma());
        json.append(',');
        appendLinks(json, model.dependencies());
        json.append(',');
        PosLegend.appendLegendJson(json, model.usedPosCodes());
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
        return CandidateGraphModel.snapshot(
                classCandidates, responsibilityCandidates, attributeCandidates, collaboratorCandidates);
    }

}
