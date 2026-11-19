package domox.dom.crc.diagram;
import domox.DomainModule;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.Review;
import domox.dom.crc.model.ReviewStatus;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import jakarta.annotation.Priority;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.*;

/**
 * Renders a {@link DomainModel}-wide <em>lexical dependency</em> summary as a D3 force-directed
 * graph JSON seeded from the model's <em>approved</em> candidates, comparable to the per-candidate
 * {@link CandidateDiagrams#renderDiagram(Candidate)} but scoped to the whole analysis run.
 * <p>
 * The graph is built by a breadth-first expansion of the candidate-connection graph starting
 * from every approved candidate of the model (a candidate is approved when it carries at least
 * one {@link Review} whose status is {@link ReviewStatus#APPROVED}), one hop deep.  Seeding from the
 * approved set keeps the starting graph deliberately small so it renders reliably; a wider view of
 * the model is obtained by approving more candidates.  From each frontier candidate's own
 * {@link RuleMatch}es, only dependencies whose governor <em>and</em> dependent lemma both
 * correspond to a candidate word are kept, so the retained edges draw the approved candidates
 * together with every candidate directly (or, at higher depths, transitively) connected to them.
 * <p>
 * The result is a {@link String} containing a JSON object with the following structure:
 * <pre>{@code {
 *   "nodes": [
 *     {"id":"store","name":"Store","subdomain":"Inventory","pos":"NN","color":"#3498DB",
 *      "r":12,"approved":true}
 *   ],
 *   "links": [
 *     {"source":"store","target":"customer","label":"nsubj"}
 *   ],
 *   "legend": [
 *     {"color":"#3498DB","label":"Noun","description":"Entities and objects"},
 *     ...
 *   ]
 * }}</pre>
 * The diagram is regenerated on <em>every</em> call — it is never cached — because its content
 * reflects the current candidate set and their reviews, both of which change as candidates are
 * reviewed, approved, or rejected.
 */
@Slf4j
@Named(DomainModule.NAMESPACE + ".DomainModelDiagramsD3")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class DomainModelDiagramsD3 {

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
            // The neighbourhood, the node set and every per-lemma decoration are assembled by the
            // shared candidate-graph model (see CandidateGraphModel) — the same model the two
            // per-candidate renderers use, so all three diagrams agree on node identity.
            final CandidateGraphModel graph = CandidateGraphModel.of(approved, 1, allCandidates);

            // Build enriched JSON: nodes, links, legend.
            final StringBuilder json = new StringBuilder();
            json.append('{');
            appendNodes(json, graph.nodeLemmas(), graph.posByLemma(), graph.countByLemma(), graph.maxCount(),
                    graph.approvedLemmas(), graph.displayName(), graph.subdomainByLemma(),
                    graph.candidateIdByLemma(), graph.objectTypeByLemma());
            json.append(',');
            appendLinks(json, graph.dependencies());
            json.append(',');
            PosLegend.appendLegendJson(json, graph.usedPosCodes());
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

    // ============ JSON generation helpers ============

    /**
     * Appends the {@code "nodes"} array. Each node: id, name, subdomain, pos, color, r, approved.
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
            final Long candId = candidateIdByLemma.get(lemma);
            final String objType = objectTypeByLemma.get(lemma);
            if (candId != null && objType != null) {
                json.append(",\"candidateId\":").append(candId);
                json.append(",\"objectType\":\"").append(DiagramJson.escape(objType)).append('"');
            }
            json.append('}');
            first = false;
        }
        json.append(']');
    }

    /**
     * Appends the {@code "links"} array. Each link: source, target, optional label (TdType code).
     */
    private static void appendLinks(final StringBuilder json,
                                    final List<TypedDependency> dependencies) {
        json.append("\"links\":[");
        boolean first = true;
        for (final TypedDependency td : dependencies) {
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

    /**
     * Every candidate owned by the model, in a single snapshot.  Mirrors the per-type candidate.
     */
    private static List<Candidate> candidatesOf(final DomainModel model) {
        final List<Candidate> all = new ArrayList<>();
        if (model.classList != null) {
            all.addAll(model.classList);
        }
        if (model.responsibilityList != null) {
            all.addAll(model.responsibilityList);
        }
        if (model.attributeList != null) {
            all.addAll(model.attributeList);
        }
        if (model.collaboratorList != null) {
            all.addAll(model.collaboratorList);
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
}