package domox.dom.crc;

import domox.DomainModule;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.valuetypes.vega.applib.value.Vega;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Renders a candidate-centric <em>dependency neighbourhood</em> as an interactive
 * <b>full-Vega</b> force-directed <em>node-link</em> graph for a single {@link Candidate},
 * analogous to {@link DomainModelDiagramsD3#renderDiagram(DomainModel)} (which covers the whole
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
 * The emitted spec uses the <b>full Vega</b> schema
 * ({@code https://vega.github.io/schema/vega/v6.json}), which the Wicket viewer classifies as
 * {@link Vega.Schema#VEGA} — never {@link Vega.Schema#NONE}.  Full Vega is required because the
 * force layout needs the Vega <em>{@code force}</em> transform, which Vega-Lite does not
 * support.  (The Wicket viewer renders full-Vega specs with the canvas renderer, so hover
 * tooltips and hyperlinks are inert in this stack; the graph itself — nodes, links, labels and
 * a POS-colour legend — renders normally.)
 * <p>
 * The graph is regenerated on <em>every</em> call — it is never cached — because its content
 * reflects the current candidate set, which changes as candidates are reviewed, approved, or
 * rejected.
 */
@Named(DomainModule.NAMESPACE + ".CandidateVegaGraph")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class CandidateVegaGraph {

    private static final Logger log = LoggerFactory.getLogger(CandidateVegaGraph.class);

    /** Base node radius (px), enlarged proportionally to rule-match count. */
    private static final double NODE_BASE_RADIUS = 8.0;

    /** Rule-match count that maps to scale 1 (no enlargement). */
    private static final int MIN_MATCH_COUNT = 1;

    /** Rule-match count of the largest node maps to scale 4. */
    private static final double MAX_SCALE = 4.0;

    private final ClassCandidates classCandidates;
    private final ActionCandidates actionCandidates;
    private final PropertyCandidates propertyCandidates;
    private final AssociationCandidates associationCandidates;

    @Inject
    public CandidateVegaGraph(
            final ClassCandidates classCandidates,
            final ActionCandidates actionCandidates,
            final PropertyCandidates propertyCandidates,
            final AssociationCandidates associationCandidates) {
        this.classCandidates = classCandidates;
        this.actionCandidates = actionCandidates;
        this.propertyCandidates = propertyCandidates;
        this.associationCandidates = associationCandidates;
    }

    /**
     * Renders a freshly generated {@link Vega} value holding the full-Vega force-directed
     * node-link spec of {@code candidate}'s dependency neighbourhood (see class javadoc), or
     * {@code null} when the candidate is {@code null} or the render fails.
     */
    @Programmatic
    public Vega renderGraph(final Candidate candidate) {
        if (candidate == null) {
            return null;
        }
        final long id = candidate.getId() != null ? candidate.getId() : -1L;
        final String name = candidate.getCandidateName();
        log.debug("Rendering Vega graph for candidate #{} '{}'", id, name);
        try {
            final String vegaJsonSpec = buildVegaSpec(candidate);
            log.debug("Vega JSON spec ({} chars): {}", vegaJsonSpec.length(), vegaJsonSpec);
            return Vega.valueOf(vegaJsonSpec);
        } catch (Exception e) {
            log.warn("Failed to render Vega graph for candidate #{} '{}'", id, name, e);
            return null;
        }
    }

    // ============ spec assembly ============

    /**
     * Assembles the full-Vega spec: {@code node-data} (with a static {@code force} transform),
     * {@code link-data} (with {@code lookup} transforms joining both endpoints to their nodes),
     * a POS-category colour scale with auto-generated legend, and rule/symbol/text marks.
     */
    private String buildVegaSpec(final Candidate candidate) {
        // Snapshot the candidate set once per render: the lemma whitelist and the per-lemma
        // candidate lookups are derived from the same lists, so the graph is built against a
        // consistent candidate set.
        final List<Candidate> allCandidates = allCandidates();
        final Set<String> candidateLemmas = candidateLemmas(allCandidates);
        final Map<String, List<Candidate>> candidatesByLemma = candidatesByLemma(allCandidates);

        // Breadth-first expansion of the candidate-connection graph, hop-depth hops deep.
        final List<TypedDependency> dependencies = collectDependencies(
                candidate, candidateLemmas, candidatesByLemma, candidate.getHopDepth());

        // Remove association-artifact edges — synthetic "X_Y" compound nodes — exactly as
        // DomainModelDiagramsD3 does: participant lemmas and their links are kept; only the
        // compound node is dropped from the diagram.
        final Set<String> artifactLemmas = associationArtifactLemmas(allCandidates, candidateLemmas);
        dependencies.removeIf(td -> td != null && (
                artifactLemmas.contains(lower(td.getGovernorLemma())) ||
                artifactLemmas.contains(lower(td.getDependentLemma()))));

        // Per-lemma metadata: POS codes and rule-match counts.
        final Map<String, Set<String>> posByLemma = new LinkedHashMap<>();
        final Map<String, Integer> countByLemma = new HashMap<>();
        for (final TypedDependency td : dependencies) {
            if (td == null) continue;
            accumulatePos(posByLemma, lower(td.getGovernorLemma()), td.getGovernorPos());
            accumulatePos(posByLemma, lower(td.getDependentLemma()), td.getDependentPos());
            final int matches = td.getRuleMatches() != null ? td.getRuleMatches().size() : 0;
            countByLemma.merge(lower(td.getGovernorLemma()), matches, Integer::sum);
            countByLemma.merge(lower(td.getDependentLemma()), matches, Integer::sum);
        }

        // Every lemma that becomes a node: the candidate itself plus endpoints of dependencies.
        final Set<String> nodeLemmas = new LinkedHashSet<>();
        nodeLemmas.add(lower(candidate.getCandidateName()));
        for (final TypedDependency td : dependencies) {
            if (td == null) continue;
            nodeLemmas.add(lower(td.getGovernorLemma()));
            nodeLemmas.add(lower(td.getDependentLemma()));
        }
        nodeLemmas.removeAll(artifactLemmas);
        if (nodeLemmas.isEmpty()) {
            // The candidate's own word is an artifact compound and every edge touching it was
            // removed: fall back to the single seed node so the graph never renders empty.
            nodeLemmas.add(lower(candidate.getCandidateName()));
        }

        final int maxCount = countByLemma.values().stream()
                .mapToInt(Integer::intValue).max().orElse(MIN_MATCH_COUNT);

        // Lemma -> display name (original casing). The seed is primed from the candidate itself
        // (it may not be part of the repository snapshot in every caller), then overwritten for
        // lemmas that are found in the snapshot.
        final Map<String, String> displayName = new HashMap<>();
        final String seedName = candidate.getCandidateName();
        displayName.put(lower(seedName), seedName != null ? seedName : "");
        for (final String lemma : nodeLemmas) {
            final List<Candidate> cs = candidatesByLemma.get(lemma);
            if (cs != null && !cs.isEmpty()) {
                displayName.put(lemma, cs.getFirst().getCandidateName());
            }
        }

        final Map<String, String> classSubdomainByLemma = new HashMap<>();
        for (final Candidate c : allCandidates) {
            if (c instanceof ClassCdd cc && cc.getSubDomain() != null
                    && cc.getSubDomain().getName() != null) {
                classSubdomainByLemma.put(lower(c.getCandidateName()), cc.getSubDomain().getName());
            }
        }
        final Map<String, String> subdomainByLemma = new HashMap<>();
        for (final String lemma : nodeLemmas) {
            final String sdName = classSubdomainByLemma.get(lemma);
            subdomainByLemma.put(lemma, sdName != null ? sdName : "");
        }

        final Map<String, Long> candidateIdByLemma = new HashMap<>();
        final Map<String, String> objectTypeByLemma = new HashMap<>();
        for (final String lemma : nodeLemmas) {
            final List<Candidate> cs = candidatesByLemma.get(lemma);
            if (cs != null && !cs.isEmpty()) {
                final Candidate c = cs.getFirst();
                candidateIdByLemma.put(lemma, c.getId());
                objectTypeByLemma.put(lemma, DomainModule.NAMESPACE + "." + c.getClass().getSimpleName());
            }
        }

        final Set<String> approvedLemmas = approvedLemmasSet(allCandidates);

        // POS categories actually present in the diagram, in encounter order — the scale domain
        // and colour range are both derived from it so node fills and the legend always agree.
        final Set<String> usedGroups = new LinkedHashSet<>();
        for (final String lemma : nodeLemmas) {
            usedGroups.add(posGroup(firstPos(posByLemma.get(lemma))));
        }

        // Per-lemma POS-type label (e.g. "<< Noun >>") for the third text line inside each rect.
        final Map<String, String> posTypeLabelByLemma = new HashMap<>();
        for (final String lemma : nodeLemmas) {
            final String group = posGroup(firstPos(posByLemma.get(lemma)));
            posTypeLabelByLemma.put(lemma, "<< " + group + " >>");
        }

        final String nodesJson = nodeValuesJson(nodeLemmas, posByLemma, countByLemma, maxCount,
                approvedLemmas, displayName, subdomainByLemma, posTypeLabelByLemma,
                candidateIdByLemma, objectTypeByLemma);
        final String linksJson = linkValuesJson(dependencies);

        return TEMPLATE
                .replace("${title}", escapeJson(titleOf(candidate)))
                .replace("${nodes}", nodesJson)
                .replace("${links}", linksJson)
                .replace("${groupDomain}", jsonStringArray(usedGroups))
                .replace("${groupColors}", jsonStringArray(usedGroups.stream()
                        .map(CandidateVegaGraph::groupColor).toList()))
                // The text block ends with a newline after the closing brace; strip it so the
                // emitted JSON is exactly "{...}" with no trailing whitespace.
                .strip();
    }

    /** Title caption: {@code "<candidateType>: <candidateName>"}. */
    private static String titleOf(final Candidate candidate) {
        return candidate.getCandidateType() + ": " + candidate.getCandidateName();
    }

    // ============ JSON generation helpers ============

    /**
     * Node data values, one JSON object per node:
     * {@code {"id","name","subdomain"?,"pos","group","r","approved","rectWidth","rectHeight",
     * "candidateId"?,"objectType"?}}.
     */
    private static String nodeValuesJson(final Set<String> nodeLemmas,
                                         final Map<String, Set<String>> posByLemma,
                                         final Map<String, Integer> countByLemma,
                                         final int maxCount,
                                         final Set<String> approvedLemmas,
                                         final Map<String, String> displayName,
                                         final Map<String, String> subdomainByLemma,
                                         final Map<String, String> posTypeLabelByLemma,
                                         final Map<String, Long> candidateIdByLemma,
                                         final Map<String, String> objectTypeByLemma) {
        final StringBuilder json = new StringBuilder();
        boolean first = true;
        for (final String lemma : nodeLemmas) {
            if (!first) {
                json.append(',');
            }
            first = false;
            final String pos = firstPos(posByLemma.get(lemma));
            final int count = countByLemma.getOrDefault(lemma, MIN_MATCH_COUNT);
            final int radius = (int) Math.round(scaleFor(count, maxCount) * NODE_BASE_RADIUS);
            final boolean approved = approvedLemmas.contains(lemma);
            final String nameStr = displayName.getOrDefault(lemma, lemma);
            final String sdStr = subdomainByLemma.getOrDefault(lemma, "");
            final String posTypeStr = posTypeLabelByLemma.getOrDefault(lemma, "");
            // Compute rect dimensions based on the longest text line.
            final int maxTextLen = Math.max(nameStr.length(), Math.max(sdStr.length(), posTypeStr.length()));
            final int rectWidth = Math.max(maxTextLen * 7 + 24, 90);
            final int rectHeight = 48;
            json.append('{');
            json.append("\"id\":\"").append(escapeJson(lemma)).append('"');
            json.append(",\"name\":\"").append(escapeJson(nameStr)).append('"');
            if (!sdStr.isEmpty()) {
                json.append(",\"subdomain\":\"").append(escapeJson(sdStr)).append('"');
            }
            json.append(",\"pos\":\"").append(escapeJson(pos)).append('"');
            json.append(",\"group\":\"").append(escapeJson(posGroup(pos))).append('"');
            json.append(",\"r\":").append(radius);
            json.append(",\"approved\":").append(approved);
            json.append(",\"rectWidth\":").append(rectWidth);
            json.append(",\"rectHeight\":").append(rectHeight);
            final Long candId = candidateIdByLemma.get(lemma);
            final String objType = objectTypeByLemma.get(lemma);
            if (candId != null && objType != null) {
                json.append(",\"candidateId\":").append(candId);
                json.append(",\"objectType\":\"").append(escapeJson(objType)).append('"');
            }
            json.append('}');
        }
        return json.toString();
    }

    /**
     * Link data values, one JSON object per dependency:
     * {@code {"source","target","label"?}} — source is the governor lemma, target the dependent.
     */
    private static String linkValuesJson(final List<TypedDependency> dependencies) {
        final StringBuilder json = new StringBuilder();
        boolean first = true;
        for (final TypedDependency td : dependencies) {
            if (td == null) continue;
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            json.append("\"source\":\"").append(escapeJson(lower(td.getGovernorLemma()))).append('"');
            json.append(",\"target\":\"").append(escapeJson(lower(td.getDependentLemma()))).append('"');
            if (td.getType() != null) {
                json.append(",\"label\":\"").append(escapeJson(td.getType().getCode())).append('"');
            }
            json.append('}');
        }
        return json.toString();
    }

    /** {@code ["a","b"]} — a JSON string array of the given values, in order. */
    private static String jsonStringArray(final Collection<String> values) {
        final StringBuilder json = new StringBuilder();
        json.append('[');
        boolean first = true;
        for (final String value : values) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escapeJson(value)).append('"');
        }
        json.append(']');
        return json.toString();
    }

    // ============ spec template ============

    /**
     * Static skeleton of the force-directed spec.  Placeholders:
     * {@code ${title}}, {@code ${nodes}}, {@code ${links}}, {@code ${groupDomain}},
     * {@code ${groupColors}}.  Link source/target fields reference node {@code id} values (the
     * link force declares {@code "id": "id"}); after the force simulation resolves source/target
     * to node tuples, marks can access {@code source.x}, {@code source.y}, {@code target.x},
     * {@code target.y} directly — no explicit lookup transforms needed.
     */
    private static final String TEMPLATE = """
            {
              "$schema": "https://vega.github.io/schema/vega/v6.json",
              "description": "Candidate dependency neighbourhood - force-directed node-link graph",
              "title": "${title}",
              "width": 1200,
              "height": 800,
              "padding": 20,
              "autosize": "none",

              "data": [
                {
                  "name": "link-data",
                  "values": [${links}]
                },
                {
                  "name": "node-data",
                  "values": [${nodes}],
                  "transform": [
                    {
                      "type": "force",
                      "iterations": 300,
                      "static": false,
                      "forces": [
                        {"force": "center", "x": {"signal": "width / 2"}, "y": {"signal": "height / 2"}},
                        {"force": "link", "links": "link-data", "id": "id", "distance": 180},
                        {"force": "collide", "radius": 60},
                        {"force": "nbody", "strength": -90}
                      ]
                    }
                  ]
                }
              ],

              "scales": [
                {
                  "name": "color",
                  "type": "ordinal",
                  "domain": ${groupDomain},
                  "range": ${groupColors},
                  "legend": true
                }
              ],

              "legends": [
                {
                  "orient": "none",
                  "legendX": 0,
                  "legendY": 0,
                  "title": "Part of Speech",
                  "fill": "color"
                }
              ],

              "marks": [
                {
                  "type": "rule",
                  "from": {"data": "link-data"},
                  "encode": {
                    "update": {
                      "stroke": {"value": "#b9b9b9"},
                      "strokeWidth": {"value": 1},
                      "x": {"field": "source.x"},
                      "y": {"field": "source.y"},
                      "x2": {"field": "target.x"},
                      "y2": {"field": "target.y"}
                    }
                  }
                },
                {
                  "type": "text",
                  "from": {"data": "link-data"},
                  "encode": {
                    "update": {
                      "x": {"signal": "(datum.source.x + datum.target.x) / 2"},
                      "y": {"signal": "(datum.source.y + datum.target.y) / 2"},
                      "align": {"value": "center"},
                      "baseline": {"value": "middle"},
                      "fill": {"value": "#000000"},
                      "fontSize": {"value": 10},
                      "text": {"signal": "datum.label ? datum.label : ''"},
                      "opacity": {"signal": "datum.label ? 1 : 0"}
                    }
                  }
                },
                {
                  "name": "nodes",
                  "type": "rect",
                  "zindex": 1,
                  "from": {"data": "node-data"},
                  "encode": {
                    "enter": {
                      "fill": {"scale": "color", "field": "group"},
                      "stroke": {"value": "#ffffff"},
                      "fillOpacity": {"value": 0.9}
                    },
                    "update": {
                      "x": {"signal": "datum.x - datum.rectWidth / 2"},
                      "y": {"signal": "datum.y - datum.rectHeight / 2"},
                      "width": {"field": "rectWidth"},
                      "height": {"field": "rectHeight"},
                      "rx": {"value": 6},
                      "ry": {"value": 6},
                      "strokeWidth": {"signal": "datum.approved ? 2.5 : 0.6"},
                      "cursor": {"signal": "datum.candidateId ? 'pointer' : 'default'"},
                      "href": {"signal": "datum.candidateId ? '/wicket/object/' + datum.objectType + ':' + datum.candidateId : null"}
                    }
                  }
                },
                {
                  "type": "text",
                  "from": {"data": "node-data"},
                  "encode": {
                    "update": {
                      "x": {"field": "x"},
                      "y": {"signal": "datum.y - 14"},
                      "align": {"value": "center"},
                      "baseline": {"value": "middle"},
                      "fill": {"value": "#000000"},
                      "fontSize": {"value": 10},
                      "text": {"signal": "datum.subdomain ? datum.subdomain : ''"},
                      "opacity": {"signal": "datum.subdomain ? 1 : 0"}
                    }
                  }
                },
                {
                  "type": "text",
                  "from": {"data": "node-data"},
                  "encode": {
                    "update": {
                      "x": {"field": "x"},
                      "y": {"field": "y"},
                      "align": {"value": "center"},
                      "baseline": {"value": "middle"},
                      "fill": {"value": "#000000"},
                      "opacity": {"value": 1},
                      "fontWeight": {"value": "bold"},
                      "fontSize": {"value": 10},
                      "text": {"field": "name"}
                    }
                  }
                },
                {
                  "type": "text",
                  "from": {"data": "node-data"},
                  "encode": {
                    "update": {
                      "x": {"field": "x"},
                      "y": {"signal": "datum.y + 14"},
                      "align": {"value": "center"},
                      "baseline": {"value": "middle"},
                      "fill": {"value": "#000000"},
                      "opacity": {"value": 1},
                      "fontStyle": {"value": "italic"},
                      "fontSize": {"value": 10},
                      "text": {"signal": "'<< ' + datum.group + ' >>'"}
                    }
                  }
                }
              ]
            }
            """;

    // ============ candidate neighbourhood (mirrors CandidateDiagrams) ============

    /** All candidates of every type currently present, in a single snapshot. */
    private List<Candidate> allCandidates() {
        final List<Candidate> all = new ArrayList<>();
        addAll(all, classCandidates.listAll());
        addAll(all, actionCandidates.listAll());
        addAll(all, propertyCandidates.listAll());
        addAll(all, associationCandidates.listAll());
        return all;
    }

    /** Adds a {@code null}-safe candidate-list snapshot to {@code target}. */
    private static void addAll(final List<Candidate> target, final List<? extends Candidate> source) {
        if (source != null) {
            target.addAll(source);
        }
    }

    /** The lower-cased candidate names of the snapshot. */
    private static Set<String> candidateLemmas(final List<Candidate> allCandidates) {
        final Set<String> lemmas = new HashSet<>();
        allCandidates.forEach(c -> lemmas.add(lower(c.getCandidateName())));
        return lemmas;
    }

    /** Lower-case candidate name → every candidate carrying that name. */
    private static Map<String, List<Candidate>> candidatesByLemma(final List<Candidate> allCandidates) {
        final Map<String, List<Candidate>> byLemma = new HashMap<>();
        allCandidates.forEach(c ->
                byLemma.computeIfAbsent(lower(c.getCandidateName()), k -> new ArrayList<>()).add(c));
        return byLemma;
    }

    /**
     * Breadth-first traversal of the candidate-connection graph starting from {@code seed}, up to
     * {@code maxDepth} hops, returning the <em>distinct</em> candidate-restricted dependencies.
     */
    private static List<TypedDependency> collectDependencies(
            final Candidate seed,
            final Set<String> candidateLemmas,
            final Map<String, List<Candidate>> candidatesByLemma,
            final int maxDepth) {
        final List<TypedDependency> dependencies = new ArrayList<>();
        final Set<TypedDependency> seen = new HashSet<>();
        final Set<Candidate> visited = new HashSet<>();
        Set<Candidate> frontier = new LinkedHashSet<>();
        frontier.add(seed);
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
                    addCandidatesForLemma(td.getGovernorLemma(), candidatesByLemma, visited, nextFrontier);
                    addCandidatesForLemma(td.getDependentLemma(), candidatesByLemma, visited, nextFrontier);
                }
            }
            frontier = nextFrontier;
        }
        return dependencies;
    }

    /** Adds every candidate carrying {@code lemma} not yet expanded to the next frontier. */
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

    /** Keeps only dependencies whose governor and dependent lemma are both candidate words. */
    private static boolean bothEndpointsAreCandidates(final TypedDependency td,
                                                      final Set<String> candidateLemmas) {
        return candidateLemmas.contains(lower(td.getGovernorLemma()))
                && candidateLemmas.contains(lower(td.getDependentLemma()));
    }

    /** Lower-cased lemmas of approved candidates (bold border in the graph). */
    private static Set<String> approvedLemmasSet(final List<Candidate> candidates) {
        final Set<String> lemmas = new HashSet<>();
        for (final Candidate c : candidates) {
            if (c != null && c.getReviews() != null
                    && c.getReviews().stream().anyMatch(r -> r != null && r.getStatus() == ReviewStatus.APPROVED)) {
                lemmas.add(lower(c.getCandidateName()));
            }
        }
        return lemmas;
    }

    /** Lower-cased lemmas of synthetic {@code Association}-typed compound candidates ("X_Y"). */
    private static Set<String> associationArtifactLemmas(final List<Candidate> candidates,
                                                         final Set<String> candidateLemmas) {
        final Set<String> artifactLemmas = new HashSet<>();
        for (final Candidate c : candidates) {
            if (c == null || c.getCandidateName() == null) continue;
            if (!"Association".equals(typeLabel(c.getCandidateType()))) continue;
            final String lemma = lower(c.getCandidateName());
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

    /** Friendly label of a candidate type, e.g. {@code ClassCdd} → {@code Class}. */
    private static String typeLabel(final String candidateType) {
        if (candidateType == null || candidateType.isEmpty()) {
            return "Domain";
        }
        return candidateType.endsWith("Cdd")
                ? candidateType.substring(0, candidateType.length() - 3)
                : candidateType;
    }

    // ============ node metadata ============

    /** Records a POS code for a lemma, skipping null. */
    private static void accumulatePos(final Map<String, Set<String>> posByLemma,
                                      final String lemma,
                                      final PartOfSpeechType pos) {
        if (pos == null) return;
        final String code = pos.getCode();
        if (code != null && !code.isEmpty()) {
            posByLemma.computeIfAbsent(lemma, k -> new LinkedHashSet<>()).add(code);
        }
    }

    /** First POS code from the set, or empty string when null/empty. */
    private static String firstPos(final Set<String> posSet) {
        if (posSet == null || posSet.isEmpty()) return "";
        return posSet.iterator().next();
    }

    /** Linear scale: count 1 → 1.0, maxCount → MAX_SCALE. Clamped below 1. */
    private static double scaleFor(final int count, final int maxCount) {
        if (maxCount <= MIN_MATCH_COUNT) return 1.0;
        final int n = Math.max(count, MIN_MATCH_COUNT);
        return 1.0 + (MAX_SCALE - 1.0) * (n - MIN_MATCH_COUNT) / (maxCount - MIN_MATCH_COUNT);
    }

    /** Penn POS code → legend category label (first matching category wins). */
    private static String posGroup(final String pos) {
        for (final PosCategory category : CATEGORIES) {
            if (category.posCodes().contains(pos)) {
                return category.label();
            }
        }
        return CATEGORIES.getLast().label();
    }

    /** Legend category label → fill colour. */
    private static String groupColor(final String group) {
        for (final PosCategory category : CATEGORIES) {
            if (category.label().equals(group)) {
                return category.color();
            }
        }
        return CATEGORIES.getLast().color();
    }

    /** A single legend row: fill colour, short label, and the POS codes that map to it. */
    private record PosCategory(String label, String color, Set<String> posCodes) {}

    /**
     * All legend categories, ordered by semantic priority — the first matching entry wins.
     * Mirrors the {@link DomainModelDiagramsD3} legend (CD/CC codes fall through to "Other" so
     * the node fill and the legend always agree).
     */
    private static final List<PosCategory> CATEGORIES = List.of(
            new PosCategory("Noun", "#3498DB", Set.of("NN", "NNS")),
            new PosCategory("Proper noun", "#85C1E9", Set.of("NNP", "NNPS")),
            new PosCategory("Verb", "#E74C3C", Set.of("VB", "VBZ", "VBD", "VBG", "VBN", "VBP")),
            new PosCategory("Adjective", "#2ECC71", Set.of("JJ", "JJR", "JJS")),
            new PosCategory("Determiner", "#F39C12", Set.of("DT")),
            new PosCategory("Pronoun", "#9B59B6", Set.of("PRP", "PRP$", "WDT", "WP", "WP$", "WRB")),
            new PosCategory("Preposition", "#17A2B8", Set.of("IN")),
            new PosCategory("Adverb", "#FFBB28", Set.of("RB")),
            new PosCategory("Other", "#AAB7B8", Set.of()));

    // ============ misc helpers ============

    private static String lower(final String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /** Minimal JSON string escaping: backslash, double-quote, and control characters. */
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
