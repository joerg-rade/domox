package domox.dom.nlp;

import domox.dom.crc.Candidate;
import domox.dom.crc.Review;
import domox.dom.crc.ReviewStatus;
import domox.dom.rules.RuleMatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generates a Graphviz (DOT) lexical dependency graph from the persisted
 * {@link TypedDependency}s of one or more sentences.
 * <p>
 * The graph is reconstructed lazily from persisted state (lemmas, POS tags and
 * relation codes on each {@link TypedDependency}) so a document-wide lexical
 * diagram can be built without re-running the NLP pipeline and without
 * reconstructing {@code SentenceTO}/{@code TokenTO} objects.
 */
public class LexicalGraphGenerator {

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

    // --- Border styling derived from the candidate set ---

    /**
     * Candidate-type marker of the synonym rule match produced by {@code TDR41}.  A candidate that
     * carries a match of this type (either as its {@link RuleMatch#getCandidateName()} or as its
     * {@link RuleMatch#getRelatedCandidateName()}) is part of a synonym pair.
     */
    private static final String SYNONYM_CANDIDATE_TYPE = "SynonymCdd";

    /**
     * Node border style for a term that is part of a synonym: the global {@code rounded,filled}
     * style extended with a dashed border so it reads as a semantic variant of another term.
     */
    private static final String SYNONYM_BORDER_STYLE = "rounded,filled,dashed";

    /** Node border width (penwidth) for an approved candidate's emphasised (bold) border. */
    private static final double APPROVED_BORDER_PENWIDTH = 3.0;

    /**
     * @param dependencies the typed dependencies to draw; ROOT pseudo-token
     *                     dependencies (governor index 0) are skipped
     * @return Graphviz DOT source for a compact lexical dependency graph, drawn without any
     *         candidate-derived border styling
     */
    public String generateGraphvizGraph(final Collection<TypedDependency> dependencies) {
        return generateGraphvizGraph(dependencies, List.of());
    }

    /**
     * @param dependencies the typed dependencies to draw; ROOT pseudo-token
     *                     dependencies (governor index 0) are skipped
     * @param candidates   the candidate snapshot whose state styles the node borders: a node whose
     *                     lemma matches an {@link ReviewStatus#APPROVED} candidate gets a bold
     *                     border, and a node whose lemma matches a candidate that is part of a
     *                     synonym (carries a {@code SynonymCdd} rule match) gets a dashed border.
     *                     The two styles combine when a candidate is both approved and a synonym member.
     * @return Graphviz DOT source for a compact lexical dependency graph
     */
    public String generateGraphvizGraph(final Collection<TypedDependency> dependencies,
                                        final Collection<Candidate> candidates) {
        // Border emphasis is derived once per render from the given (snapshot) candidate set so the
        // graph is drawn against a consistent view of what is approved and what is part of a synonym.
        final Set<String> approvedLemmas = approvedLemmas(candidates);
        final Set<String> synonymLemmas = synonymLemmas(candidates);
        // Collect distinct (lemma, POS) nodes and the directed edges between them
        // up-front, so each distinct node is declared exactly once (with a stable
        // id) and all edges can reference those ids.
        final Map<String, String[]> nodeByKey = new LinkedHashMap<>();      // key -> {lemma, pos}
        final Map<String, LinkedHashSet<String>> posByLemma = new LinkedHashMap<>();
        final Map<String, Integer> matchCountByNode = new HashMap<>();       // key -> rule-match count
        final List<String[]> edges = new ArrayList<>();                      // {sourceKey, targetKey, relation}

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

            nodeByKey.putIfAbsent(key(sourceLemma, sourcePos), new String[]{sourceLemma, sourcePos});
            nodeByKey.putIfAbsent(key(targetLemma, targetPos), new String[]{targetLemma, targetPos});
            posByLemma.computeIfAbsent(sourceLemma, k -> new LinkedHashSet<>()).add(sourcePos);
            posByLemma.computeIfAbsent(targetLemma, k -> new LinkedHashSet<>()).add(targetPos);
            matchCountByNode.merge(key(sourceLemma, sourcePos), matches, Integer::sum);
            matchCountByNode.merge(key(targetLemma, targetPos), matches, Integer::sum);
            edges.add(new String[]{key(sourceLemma, sourcePos), key(targetLemma, targetPos), relation});
        }

        final int maxMatchCount = matchCountByNode.values().stream()
                .mapToInt(Integer::intValue).max().orElse(MIN_MATCH_COUNT);

        final StringBuilder dot = new StringBuilder();
        dot.append("digraph LexicalDependencyGraph {\n");
        dot.append("    // Layout and style settings for maximum compactness.\n");
        dot.append("    //    `layout = dot` is used deliberately: `sfdp` (force-directed) is not\n");
        dot.append("    //    supported by Kroki's Graphviz build (missing triangulation), so any\n");
        dot.append("    //    DOT with `layout = sfdp` makes the /graphviz endpoint return HTTP 400\n");
        dot.append("    //    and the whole diagram fails to render.\n");
        dot.append("    graph [\n");
        dot.append("        layout = neato\n");
        dot.append("        overlap = false\n");
        dot.append("        sep = \"+10\"\n");
        dot.append("    ];\n\n");
        dot.append("    node [\n");
        dot.append("        shape = box\n");
        dot.append("        style = \"rounded,filled\"\n");
        dot.append("        fillcolor = \"#F8F9FA\"\n");
        dot.append("        color = \"#333333\"\n");
        dot.append("        fontname = \"Helvetica\"\n");
        dot.append("        fontsize = 10\n");
        dot.append("        height = 0.25\n");
        dot.append("        margin = \"0.08,0.04\"\n");
        dot.append("    ];\n\n");
        dot.append("    edge [\n");
        dot.append("        fontname = \"Helvetica\"\n");
        dot.append("        fontsize = 8\n");
        dot.append("        color = \"#555555\"\n");
        dot.append("        arrowsize = 0.7\n");
        dot.append("    ];\n\n");

        // Define Graphviz nodes. A lemma that occurs with several POS tags needs one
        // node per tag (e.g. item_nn vs item_nns) so the tags don't merge into one node;
        // a lemma that occurs with a single POS keeps a bare, readable id (e.g. customer).
        // Each node is filled with its POS color and enlarged according to rule-match count
        // (count 1 -> scale 1, maximum count -> scale 4); the font size is scaled by that same
        // factor so large nodes carry proportionally larger labels.
        dot.append("    // Node Declarations\n");
        for (final String[] node : nodeByKey.values()) {
            final String lemma = node[0];
            final String pos = node[1];
            final String id = nodeId(lemma, pos, posByLemma.get(lemma).size() > 1);
            final int count = Math.max(matchCountByNode.getOrDefault(key(lemma, pos), MIN_MATCH_COUNT), MIN_MATCH_COUNT);
            final double scale = scaleFor(count, maxMatchCount);
            // Border emphasis reflects the candidate state of the term: approved candidates get a
            // bold border, synonym members get a dashed border, and a node can carry both.  Only
            // nodes whose lemma is backed by such a candidate deviate from the global defaults, so
            // the plain graph (no candidate snapshot) keeps emitting bare node declarations.
            final boolean synonym = synonymLemmas.contains(lower(lemma));
            final boolean approved = approvedLemmas.contains(lower(lemma));
            // IDs are always double-quoted so lemmas containing hyphens, digits, dots or DOT
            // reserved keywords (e.g. 'node', 'edge', 'graph') can never produce unparseable DOT.
            // Previously an unquoted id such as 'data-center' or the keyword 'node' made Graphviz/Kroki
            // reject the graph with HTTP 400, which then tore down the whole list view.
            dot.append("    ").append(dotQuoted(id))
                    .append(" [label=").append(nodeLabel(lemma, pos))
                    .append(", fillcolor=").append(dotQuoted(posColor(pos)))
                    .append(", width=").append(formatDouble(scale * NODE_BASE_WIDTH))
                    .append(", height=").append(formatDouble(scale * NODE_BASE_HEIGHT))
                    .append(", fontsize=").append(Math.round(scale * NODE_BASE_FONTSIZE));
            if (synonym) {
                dot.append(", style=").append(dotQuoted(SYNONYM_BORDER_STYLE));
            }
            if (approved) {
                dot.append(", penwidth=").append(formatDouble(APPROVED_BORDER_PENWIDTH));
            }
            dot.append("];\n");
        }

        // Add Directed Relationships (governor -> dependent)
        dot.append("\n    // Relationships (Edges)\n");
        for (final String[] edge : edges) {
            final String[] source = nodeByKey.get(edge[0]);
            final String[] target = nodeByKey.get(edge[1]);
            final String sourceId = nodeId(source[0], source[1], posByLemma.get(source[0]).size() > 1);
            final String targetId = nodeId(target[0], target[1], posByLemma.get(target[0]).size() > 1);
            dot.append("    ").append(dotQuoted(sourceId)).append(" -> ").append(dotQuoted(targetId))
                    .append(" [label=").append(dotQuoted(edge[2])).append("];\n");
        }

        dot.append("}\n");
        return dot.toString();
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

    /** DOT label literal for a node: the (escaped) lemma with its POS code as a second line in
     *  guillemets. The literal {@code \n} line-break is deliberately left unescaped so Graphviz
     *  renders a real line break, while the lemma/POS text itself is escaped for DOT string-literal
     *  safety. {@code null} POS tags produce a bare single-line label. */
    private static String nodeLabel(final String lemma, final String pos) {
        final String leaf = dotEscape(lemma);
        final String tag = dotEscape(pos);
        return "\"" + (tag.isEmpty() ? leaf : leaf + "\\n«" + tag + "»") + "\"";
    }

    /** A DOT double-quoted string/id literal with its content escaped for backslash and double-quote. */
    private static String dotQuoted(final String raw) {
        return "\"" + dotEscape(raw) + "\"";
    }

    /** Escapes backslashes and double-quotes so {@code raw} is safe inside a DOT quoted string/id. */
    private static String dotEscape(final String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String lower(final String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /**
     * Lower-cased lemmas of every candidate in the snapshot that is currently approved, i.e. that
     * carries at least one {@link Review} whose status is {@link ReviewStatus#APPROVED}.  These
     * lemmas are drawn with a bold border.  A {@code null}/{@code null}-entry free snapshot or one
     * with no approved candidate yields an empty set.
     */
    private static Set<String> approvedLemmas(final Collection<Candidate> candidates) {
        final Set<String> lemmas = new HashSet<>();
        for (final Candidate candidate : candidates) {
            if (candidate == null || candidate.getCandidateName() == null) {
                continue;
            }
            final boolean approved = candidate.getReviews() != null
                    && candidate.getReviews().stream()
                            .anyMatch(review -> review != null && review.getStatus() == ReviewStatus.APPROVED);
            if (approved) {
                lemmas.add(lower(candidate.getCandidateName()));
            }
        }
        return lemmas;
    }

    /**
     * Lower-cased lemmas of every term participating in a synonym pair, collected from the
     * {@code SynonymCdd} rule matches carried by the candidate snapshot.  Each such match names the
     * two equivalent terms ({@link RuleMatch#getCandidateName()} and
     * {@link RuleMatch#getRelatedCandidateName()}), both of which are returned.  These lemmas are
     * drawn with a dashed border.
     */
    private static Set<String> synonymLemmas(final Collection<Candidate> candidates) {
        final Set<String> lemmas = new HashSet<>();
        for (final Candidate candidate : candidates) {
            if (candidate == null || candidate.getRuleMatches() == null) {
                continue;
            }
            for (final RuleMatch match : candidate.getRuleMatches()) {
                if (match != null && SYNONYM_CANDIDATE_TYPE.equals(match.getCandidateType())) {
                    lemmas.add(lower(match.getCandidateName()));
                    lemmas.add(lower(match.getRelatedCandidateName()));
                }
            }
        }
        return lemmas;
    }

    /**
     * Linear scale from the node's rule-match count into [1, {@link #MAX_SCALE}]: a count of
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

    /** Formats a node dimension with three decimals (DOT width/height in inches). */
    private static String formatDouble(final double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /**
     * Maps a Penn POS tag to its fill color — mirroring
     * {@code ColoredPlantUmlMindmapGenerator.getPosColor} so the two generators stay consistent.
     */
    private static String posColor(final String pos) {
        return switch (pos) {
            case "NN", "NNS" -> "#3498DB";     // Blue for nouns
            case "VB", "VBZ", "VBD", "VBG", "VBN", "VBP" -> "#E74C3C";     // Red for verbs
            case "JJ", "JJR", "JJS" -> "#2ECC71";     // Green for adjectives
            case "DT" -> "#F39C12";     // Orange for determiners
            case "PRP", "PRP$" -> "#9B59B6";     // Purple for pronouns
            case "IN" -> "#17A2B8";     // Teal for prepositions
            case "RB" -> "#FFBB28";     // Amber for adverbs
            case "CD" -> "#F1C40F";     // Yellow for numerals
            case "CC" -> "#00C49F";     // Teal for coordinating conjunctions
            case "WDT", "WP", "WP$", "WRB" -> "#9B59B6";     // Soft purple for wh-words
            case "NNP", "NNPS" -> "#85C1E9";     // Light blue for proper nouns
            case ",", ".", "!", "?", ";", ":" -> "#FFFFFF";     // White for punctuation
            default -> "#AAB7B8";     // Neutral gray for others
        };
    }
}
