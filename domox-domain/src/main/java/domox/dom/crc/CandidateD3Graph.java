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
 *      "r":12,"approved":true,"candidateId":123,"objectType":"domox.dom.crc.ClassCdd"}
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
    public CandidateD3Graph(
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
        final Set<String> candidateLemmas = candidateLemmas(allCandidates);
        final Map<String, List<Candidate>> candidatesByLemma = candidatesByLemma(allCandidates);

        // Breadth-first expansion of the candidate-connection graph.
        final List<TypedDependency> dependencies = collectDependencies(
                candidate, candidateLemmas, candidatesByLemma, candidate.getHopDepth());

        // Remove association-artifact edges.
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

        // Every lemma that becomes a node.
        final Set<String> nodeLemmas = new LinkedHashSet<>();
        nodeLemmas.add(lower(candidate.getCandidateName()));
        for (final TypedDependency td : dependencies) {
            if (td == null) continue;
            nodeLemmas.add(lower(td.getGovernorLemma()));
            nodeLemmas.add(lower(td.getDependentLemma()));
        }
        nodeLemmas.removeAll(artifactLemmas);
        if (nodeLemmas.isEmpty()) {
            nodeLemmas.add(lower(candidate.getCandidateName()));
        }

        final int maxCount = countByLemma.values().stream()
                .mapToInt(Integer::intValue).max().orElse(MIN_MATCH_COUNT);

        // Lemma → display name (original casing).
        final Map<String, String> displayName = new HashMap<>();
        final String seedName = candidate.getCandidateName();
        displayName.put(lower(seedName), seedName != null ? seedName : "");
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
                classSubdomainByLemma.put(lower(c.getCandidateName()), cc.getSubDomain().getName());
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
                objectTypeByLemma.put(lemma, DomainModule.NAMESPACE + "." + c.getClass().getSimpleName());
            }
        }

        // Lemma → whether it has at least one approved review.
        final Set<String> approvedLemmas = approvedLemmasSet(allCandidates);

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
        appendLegend(json, usedPosCodes);
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
            final String pos = firstPos(posByLemma.get(lemma));
            final int count = countByLemma.getOrDefault(lemma, MIN_MATCH_COUNT);
            final int radius = (int) Math.round(scaleFor(count, maxCount) * NODE_BASE_RADIUS);
            final boolean approved = approvedLemmas.contains(lemma);
            final boolean hasCandidateLink = candidateIdByLemma.containsKey(lemma)
                    && objectTypeByLemma.containsKey(lemma);
            json.append('{');
            json.append("\"id\":\"").append(escapeJson(lemma)).append('"');
            json.append(",\"name\":\"").append(escapeJson(displayName.getOrDefault(lemma, lemma))).append('"');
            final String sd = subdomainByLemma.getOrDefault(lemma, "");
            if (!sd.isEmpty()) {
                json.append(",\"subdomain\":\"").append(escapeJson(sd)).append('"');
            }
            json.append(",\"pos\":\"").append(escapeJson(pos)).append('"');
            json.append(",\"color\":\"").append(posColor(pos)).append('"');
            json.append(",\"r\":").append(radius);
            json.append(",\"approved\":").append(approved);
            json.append(",\"isCandidateColumn\":").append(hasCandidateLink);
            if (hasCandidateLink) {
                json.append(",\"candidateId\":").append(candidateIdByLemma.get(lemma));
                json.append(",\"objectType\":\"").append(escapeJson(objectTypeByLemma.get(lemma))).append('"');
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
            final String src = lower(td.getGovernorLemma());
            final String tgt = lower(td.getDependentLemma());
            json.append('{');
            json.append("\"source\":\"").append(escapeJson(src)).append('"');
            json.append(",\"target\":\"").append(escapeJson(tgt)).append('"');
            if (td.getType() != null) {
                json.append(",\"label\":\"").append(escapeJson(td.getType().getCode())).append('"');
            }
            json.append('}');
            first = false;
        }
        json.append(']');
    }

    /**
     * Appends the {@code "legend"} array: colour swatches for each POS-based fill colour that
     * actually appears in the diagram nodes, plus a catch-all "Other" entry.
     */
    private static void appendLegend(final StringBuilder json, final Set<String> usedPosCodes) {
        json.append("\"legend\":[");
        boolean first = true;
        for (final LegendEntry entry : LEGEND_ENTRIES) {
            if (entry.posCodes.isEmpty()) {
                if (hasOtherPos(usedPosCodes)) {
                    appendLegendEntry(json, entry, first);
                    first = false;
                }
                continue;
            }
            if (hasAny(usedPosCodes, entry.posCodes)) {
                appendLegendEntry(json, entry, first);
                first = false;
            }
        }
        json.append(']');
    }

    /** True when at least one used POS code is not covered by any specific legend entry. */
    private static boolean hasOtherPos(final Set<String> usedPosCodes) {
        final Set<String> covered = new HashSet<>();
        for (final LegendEntry e : LEGEND_ENTRIES) {
            if (!e.posCodes.isEmpty()) {
                covered.addAll(e.posCodes);
            }
        }
        for (final String code : usedPosCodes) {
            if (!covered.contains(code)) {
                return true;
            }
        }
        return false;
    }

    /** True when the intersection of {@code used} and {@code candidates} is non-empty. */
    private static boolean hasAny(final Set<String> used, final Set<String> candidates) {
        for (final String c : candidates) {
            if (used.contains(c)) {
                return true;
            }
        }
        return false;
    }

    /** Appends a single JSON legend entry object, preceded by a comma when not the first entry. */
    private static void appendLegendEntry(final StringBuilder json, final LegendEntry entry, final boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append("{\"color\":\"").append(entry.color).append('"')
                .append(",\"label\":\"").append(entry.label).append('"')
                .append(",\"description\":\"").append(entry.description).append("\"}");
    }

    /** A single legend row: fill colour, short label, description, and the POS codes that map to it. */
    private record LegendEntry(String color, String label, String description, Set<String> posCodes) {}

    /**
     * All possible legend entries, ordered by semantic category.  The first matching entry wins.
     * The POS codes here must stay in sync with {@link #posColor(String)}.
     */
    private static final List<LegendEntry> LEGEND_ENTRIES = List.of(
            new LegendEntry("#3498DB", "Noun", "Entities and objects (NN, NNS)", Set.of("NN", "NNS")),
            new LegendEntry("#85C1E9", "Proper noun", "Named entities (NNP, NNPS)", Set.of("NNP", "NNPS")),
            new LegendEntry("#E74C3C", "Verb", "Actions and predicates (VB, VBZ)", Set.of("VB", "VBZ", "VBD", "VBG", "VBN", "VBP")),
            new LegendEntry("#2ECC71", "Adjective", "Descriptors and modifiers (JJ, JJR, JJS)", Set.of("JJ", "JJR", "JJS")),
            new LegendEntry("#F39C12", "Determiner", "Articles and determiners (DT)", Set.of("DT")),
            new LegendEntry("#9B59B6", "Pronoun", "Pronouns (PRP, PRP$)", Set.of("PRP", "PRP$", "WDT", "WP", "WP$", "WRB")),
            new LegendEntry("#17A2B8", "Preposition", "Prepositions (IN)", Set.of("IN")),
            new LegendEntry("#FFBB28", "Adverb", "Adverbs (RB)", Set.of("RB")),
            new LegendEntry("#AAB7B8", "Other", "Other parts of speech", Set.of())
    );

    // ============ Candidate neighbourhood ============

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
     * Breadth-first traversal of the candidate-connection graph starting from {@code seed},
     * up to {@code maxDepth} hops, returning the <em>distinct</em> candidate-restricted
     * dependencies.
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

    /**
     * Lower-cased lemmas of synthetic {@code Association}-typed compound candidates ("X_Y").
     * Such candidates are products of candidate-extraction rules and are excluded from the D3
     * diagram because their derived lemma is uninformative.
     */
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

    // ============ Node metadata ============

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

    /**
     * Penn POS code → fill colour. Mirrors the colour mapping in
     * {@link DomainModelDiagramsD3#posColor(String)}.
     */
    private static String posColor(final String pos) {
        return switch (pos) {
            case "NN", "NNS" -> "#3498DB";
            case "VB", "VBZ", "VBD", "VBG", "VBN", "VBP" -> "#E74C3C";
            case "JJ", "JJR", "JJS" -> "#2ECC71";
            case "DT" -> "#F39C12";
            case "PRP", "PRP$" -> "#9B59B6";
            case "IN" -> "#17A2B8";
            case "RB" -> "#FFBB28";
            case "CD" -> "#F1C40F";
            case "CC" -> "#00C49F";
            case "WDT", "WP", "WP$", "WRB" -> "#9B59B6";
            case "NNP", "NNPS" -> "#85C1E9";
            case ",", ".", "!", "?", ";", ":" -> "#FFFFFF";
            default -> "#AAB7B8";
        };
    }

    // ============ misc helpers ============

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
