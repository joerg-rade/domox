package domox.dom.nlp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The <b>node/edge model of a lexical dependency graph</b>, and the sizing rule that turns a node's
 * rule-match count into a node size.
 * <p>
 * Extracted from {@code LexicalGraphGenerator}: collecting the distinct {@code (lemma, POS)} nodes and
 * the directed edges between them, deciding a node's id (a lemma that occurs with several POS tags
 * needs one node per tag, e.g. {@code item_nn} vs {@code item_nns}) and scaling a node by how many
 * rules point at it are all one concern — what the graph <em>contains</em> — as opposed to how it is
 * written out as DOT.
 */
final class LexicalGraphNodes {

    // Target POS codes to include (filtering noise); Penn tags as returned by
    // PartOfSpeechType#getCode().
    private static final Set<String> TARGET_POS = Set.of(
            "NN", "NNS", "NNP", "NNPS",
            "VB", "VBD", "VBG", "VBN", "VBP", "VBZ",
            "JJ", "JJR", "JJS");

    /** Base node dimensions (inches) that are enlarged proportionally to rule-match count. */
    private static final double NODE_BASE_WIDTH = 0.9;
    private static final double NODE_BASE_HEIGHT = 0.35;

    /** Base node font size (points), matched to the {@code fontsize = 10} default in the graph. */
    private static final double NODE_BASE_FONTSIZE = 10.0;

    /** Rule-match count that maps to scale 1 (no enlargement). */
    private static final int MIN_MATCH_COUNT = 1;
    /** Rule-match count of the largest node maps to scale 4. */
    private static final double MAX_SCALE = 4.0;

    private final Map<String, String[]> nodeByKey = new LinkedHashMap<>();      // key -> {lemma, pos}
    private final Map<String, LinkedHashSet<String>> posByLemma = new LinkedHashMap<>();
    private final Map<String, Integer> matchCountByNode = new HashMap<>();      // key -> rule-match count
    private final List<String[]> edges = new ArrayList<>();                     // {sourceKey, targetKey, relation}
    private int maxMatchCount = MIN_MATCH_COUNT;

    private LexicalGraphNodes() {
    }

    /**
     * Collects the distinct {@code (lemma, POS)} nodes and the directed edges between them up-front,
     * so each distinct node is declared exactly once (with a stable id) and all edges can reference
     * those ids.
     *
     * @param dependencies the typed dependencies to draw; ROOT pseudo-token dependencies (governor
     *                     index 0) are skipped, as is any dependency whose POS is a non-content word
     */
    static LexicalGraphNodes collect(final Collection<TypedDependency> dependencies) {
        final LexicalGraphNodes nodes = new LexicalGraphNodes();
        for (final TypedDependency dependency : dependencies) {
            if (dependency == null || isRootPseudoToken(dependency)) {
                continue;
            }

            final String sourcePos = posCode(dependency.getGovernorPos());
            final String targetPos = posCode(dependency.getDependentPos());

            // Filter for content words; a dependency whose POS is unknown/unset is left unfiltered
            if (isFilteredOut(sourcePos) || isFilteredOut(targetPos)) {
                continue;
            }

            final String sourceLemma = lower(dependency.getGovernorLemma());
            final String targetLemma = lower(dependency.getDependentLemma());
            final String relation = dependency.getType() != null ? dependency.getType().getCode() : "";
            final int matches = dependency.getRuleMatches() != null ? dependency.getRuleMatches().size() : 0;

            nodes.nodeByKey.putIfAbsent(key(sourceLemma, sourcePos), new String[]{sourceLemma, sourcePos});
            nodes.nodeByKey.putIfAbsent(key(targetLemma, targetPos), new String[]{targetLemma, targetPos});
            nodes.posByLemma.computeIfAbsent(sourceLemma, k -> new LinkedHashSet<>()).add(sourcePos);
            nodes.posByLemma.computeIfAbsent(targetLemma, k -> new LinkedHashSet<>()).add(targetPos);
            nodes.matchCountByNode.merge(key(sourceLemma, sourcePos), matches, Integer::sum);
            nodes.matchCountByNode.merge(key(targetLemma, targetPos), matches, Integer::sum);
            nodes.edges.add(new String[]{key(sourceLemma, sourcePos), key(targetLemma, targetPos), relation});
        }
        nodes.maxMatchCount = nodes.matchCountByNode.values().stream()
                .mapToInt(Integer::intValue).max().orElse(MIN_MATCH_COUNT);
        return nodes;
    }
    /** The distinct nodes, in declaration order; each entry is {@code {lemma, pos}}. */
    List<String[]> nodes() {
        return List.copyOf(nodeByKey.values());
    }

    /** The directed edges, in declaration order; each entry is {@code {sourceKey, targetKey, relation}}. */
    List<String[]> edges() {
        return List.copyOf(edges);
    }

    /** The DOT id of a node ({@code {lemma, pos}}), disambiguated when the lemma has several POS tags. */
    String id(final String[] node) {
        return nodeId(node[0], node[1], posByLemma.get(node[0]).size() > 1);
    }

    /** The DOT id of the node a {@link #key(String, String)} refers to. */
    String idForKey(final String key) {
        return id(nodeByKey.get(key));
    }

    /** Linear scale from the node's rule-match count into {@code [1, 4]}; see {@link #scaleFor}. */
    double scale(final String[] node) {
        final int count = Math.max(
                matchCountByNode.getOrDefault(key(node[0], node[1]), MIN_MATCH_COUNT), MIN_MATCH_COUNT);
        return scaleFor(count, maxMatchCount);
    }

    /** The DOT {@code width} of a node, in inches. */
    double width(final String[] node) {
        return scale(node) * NODE_BASE_WIDTH;
    }

    /** The DOT {@code height} of a node, in inches. */
    double height(final String[] node) {
        return scale(node) * NODE_BASE_HEIGHT;
    }

    /** The DOT {@code fontsize} of a node, in points. */
    long fontSize(final String[] node) {
        return Math.round(scale(node) * NODE_BASE_FONTSIZE);
    }

    /** Lower-cases a lemma for keying; {@code null} becomes the empty string. */
    static String lower(final String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /**
     * The ROOT pseudo-token lives at index 0 and has no real governor token, so a
     * ROOT-typed dependency (or any dependency whose governor is index 0) must be skipped.
     */
    private static boolean isRootPseudoToken(final TypedDependency dependency) {
        return dependency.getType() == TdType.ROOT || dependency.getGovernorIndex() == 0;
    }

    private static String posCode(final PartOfSpeechType pos) {
        return pos != null ? pos.getCode() : "";
    }

    private static boolean isFilteredOut(final String pos) {
        return !pos.isEmpty() && !TARGET_POS.contains(pos);
    }

    /** Internal key uniquely identifying a (lemma, POS) node. */
    private static String key(final String lemma, final String pos) {
        return lemma + '\u0001' + pos;
    }

    /**
     * A lemma that appears with a single POS keeps its bare id; one that appears with
     * several POS tags is disambiguated with a lower-cased POS suffix (e.g. shop_nn vs
     * shop_vb). An unset POS never gets a suffix.
     */
    private static String nodeId(final String lemma, final String pos, final boolean disambiguate) {
        return disambiguate && !pos.isEmpty() ? lemma + "_" + pos.toLowerCase(Locale.ROOT) : lemma;
    }

    /**
     * Linear scale from the node's rule-match count into {@code [1, 4]}: a count of
     * {@value #MIN_MATCH_COUNT} maps to 1 (no enlargement) and the maximum observed count maps
     * to {@value #MAX_SCALE}. Counts below 1 are clamped to 1.
     */
    private static double scaleFor(final int count, final int maxCount) {
        if (maxCount <= MIN_MATCH_COUNT) {
            return 1.0;
        }
        final int n = Math.max(count, MIN_MATCH_COUNT);
        return 1.0 + (MAX_SCALE - 1.0) * (n - MIN_MATCH_COUNT) / (maxCount - MIN_MATCH_COUNT);
    }
}
