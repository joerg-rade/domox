package domox.dom.crc.diagram;
import domox.DomainModule;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.Review;
import domox.dom.crc.model.ReviewStatus;
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
            final Set<String> candidateLemmas = CandidateNeighbourhood.candidateLemmas(allCandidates);
            final Map<String, List<Candidate>> candidatesByLemma = CandidateNeighbourhood.candidatesByLemma(allCandidates);
            final List<TypedDependency> dependencies = CandidateNeighbourhood.collectDependencies(
                    approved, candidateLemmas, candidatesByLemma, 1);

            // Remove association-artifact candidate nodes — synthetic Association-typed compounds
            // of the form "X_Y" derived from existing candidate lemmas (e.g. "Coverage_Age",
            // "OnlineStore_Store"), regardless of which rule created them (synonym TDR41,
            // generalization, association rule).  The participant lemmas and their typed
            // dependency link are kept; only the synthetic compound node (with its empty <<>> POS)
            // is removed from the lexical diagram.
            final Set<String> artifactLemmas = CandidateNeighbourhood.associationArtifactLemmas(allCandidates, candidateLemmas);
            CandidateNeighbourhood.removeArtifactEdges(dependencies, artifactLemmas);

            // Build per-lemma metadata: POS tags and rule-match counts.
            final CandidateNeighbourhood.LemmaMetrics metrics = CandidateNeighbourhood.lemmaMetrics(dependencies);
            final Map<String, Set<String>> posByLemma = metrics.posByLemma();
            final Map<String, Integer> countByLemma = metrics.matchCountByLemma();

            // Every lemma that becomes a node: approved seeds plus endpoints of dependencies.
            final Set<String> nodeLemmas = new LinkedHashSet<>();
            for (final Candidate a : approved) {
                nodeLemmas.add(DiagramLemmas.lower(a.getCandidateName()));
            }
            for (final TypedDependency td : dependencies) {
                if (td == null) continue;
                nodeLemmas.add(DiagramLemmas.lower(td.getGovernorLemma()));
                nodeLemmas.add(DiagramLemmas.lower(td.getDependentLemma()));
            }
            // Association-artifact nodes are excluded from the diagram entirely.
            nodeLemmas.removeAll(artifactLemmas);

            // Approved lemma set (bold border).
            final Set<String> approvedLemmas = CandidateNeighbourhood.approvedLemmas(allCandidates);

            // Maximum count for scaling.
            final int maxCount = countByLemma.values().stream()
                    .mapToInt(Integer::intValue).max().orElse(DiagramNodeSizing.MIN_MATCH_COUNT);

            // Map each lemma to its display name (original casing).
            final Map<String, String> displayName = new HashMap<>();
            for (final String lemma : nodeLemmas) {
                final List<Candidate> cs = candidatesByLemma.get(lemma);
                displayName.put(lemma, cs != null && !cs.isEmpty()
                        ? cs.getFirst().getCandidateName() : lemma);
            }

            // Map each lemma to its ClassCdd subDomain name (set during review), if any.
            final Map<String, String> classSubdomainByLemma = new HashMap<>();
            for (final Candidate c : allCandidates) {
                if (c instanceof ClassCdd cc && cc.getSubDomain() != null
                        && cc.getSubDomain().getName() != null) {
                    classSubdomainByLemma.put(DiagramLemmas.lower(c.getCandidateName()),
                            cc.getSubDomain().getName());
                }
            }

            // Map each lemma to its first label line (the "subdomain" line of the node):
            // ClassCdd candidates show their assigned subDomain name; all others show blank.
            final Map<String, String> subdomainByLemma = new HashMap<>();
            for (final String lemma : nodeLemmas) {
                final String sdName = classSubdomainByLemma.get(lemma);
                subdomainByLemma.put(lemma, sdName != null ? sdName : "");
            }

            // Map each lemma to the first candidate's database ID and object type
            // (e.g. "domox.ClassCdd", read from the pinned @Named) for click-through links.
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

            // Build enriched JSON: nodes, links, legend.
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
                if (posSet != null) {
                    usedPosCodes.addAll(posSet);
                }
            }
            PosLegend.appendLegendJson(json, usedPosCodes);
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