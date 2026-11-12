package domox.dom.nlp;

import domox.dom.crc.model.Candidate;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Generates a Graphviz (DOT) lexical dependency graph from the persisted
 * {@link TypedDependency}s of one or more sentences.
 * <p>
 * The graph is reconstructed lazily from persisted state (lemmas, POS tags and
 * relation codes on each {@link TypedDependency}) so a document-wide lexical
 * diagram can be built without re-running the NLP pipeline and without
 * reconstructing {@code SentenceTO}/{@code TokenTO} objects.
 * <p>
 * This class is only the <b>orchestrator</b>: it writes the graph header, then hands the graph to the
 * collaborators that own each concern — {@link LexicalGraphNodes} (which nodes and edges exist, and
 * how big each node is), {@link LexicalGraphBorders} (the candidate-derived border styling),
 * {@link LexicalGraphLegend} (the legend table and POS palette) and {@link DotLiterals} (DOT quoting).
 * It used to carry all of them itself.
 */
public class LexicalGraphGenerator {

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
     *                     lemma matches an {@link domox.dom.crc.model.ReviewStatus#APPROVED} candidate
     *                     gets a bold border, and a node whose lemma matches a candidate that is part
     *                     of a synonym (carries a {@code SynonymCdd} rule match) gets a dashed border.
     *                     The two styles combine when a candidate is both approved and a synonym member.
     *                     Every diagram also carries a legend explaining the node colours, node size
     *                     and border semantics.
     * @return Graphviz DOT source for a compact lexical dependency graph
     */
    public String generateGraphvizGraph(final Collection<TypedDependency> dependencies,
                                        final Collection<Candidate> candidates) {
        // Border emphasis is derived once per render from the given (snapshot) candidate set so the
        // graph is drawn against a consistent view of what is approved and what is part of a synonym.
        final Set<String> approvedLemmas = LexicalGraphBorders.approvedLemmas(candidates);
        final Set<String> synonymLemmas = LexicalGraphBorders.synonymLemmas(candidates);
        final LexicalGraphNodes nodes = LexicalGraphNodes.collect(dependencies);

        final StringBuilder dot = new StringBuilder();
        appendHeader(dot);
        appendNodes(dot, nodes, approvedLemmas, synonymLemmas);
        appendEdges(dot, nodes);
        LexicalGraphLegend.append(dot);

        dot.append("}\n");
        return dot.toString();
    }
    /** The graph-level layout and style settings — neato, deliberately (see the comment in the code). */
    private void appendHeader(final StringBuilder dot) {
        dot.append("digraph LexicalDependencyGraph {\n");
        dot.append("    // Layout and style settings for maximum compactness.\n");
        dot.append("    //    `layout = neato` (force-directed) is used deliberately: Kroki's\n");
        dot.append("    //    Graphviz build cannot run `sfdp` (missing triangulation), so any DOT\n");
        dot.append("    //    declaring `layout = sfdp` makes the /graphviz endpoint return HTTP 400\n");
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
    }

    /**
     * Declares one DOT node per distinct {@code (lemma, POS)}: filled with its POS colour and enlarged
     * according to its rule-match count (count 1 → scale 1, maximum count → scale 4), with the font
     * size scaled by that same factor.  A node whose lemma is backed by an approved candidate gets a
     * bold border, one that is part of a synonym pair a dashed border, and a node can carry both; only
     * such nodes deviate from the global defaults, so the plain graph keeps bare declarations.
     */
    private void appendNodes(final StringBuilder dot, final LexicalGraphNodes nodes,
                             final Set<String> approvedLemmas, final Set<String> synonymLemmas) {
        dot.append("    // Node Declarations\n");
        for (final String[] node : nodes.nodes()) {
            final String lemma = node[0];
            final String pos = node[1];
            // Border emphasis reflects the candidate state of the term.
            final boolean synonym = synonymLemmas.contains(LexicalGraphNodes.lower(lemma));
            final boolean approved = approvedLemmas.contains(LexicalGraphNodes.lower(lemma));
            // IDs are always double-quoted so lemmas containing hyphens, digits, dots or DOT
            // reserved keywords (e.g. 'node', 'edge', 'graph') can never produce unparseable DOT.
            // Previously an unquoted id such as 'data-center' or the keyword 'node' made Graphviz/Kroki
            // reject the graph with HTTP 400, which then tore down the whole list view.
            dot.append("    ").append(DotLiterals.quoted(nodes.id(node)))
                    .append(" [label=").append(DotLiterals.nodeLabel(lemma, pos))
                    .append(", fillcolor=").append(DotLiterals.quoted(LexicalGraphLegend.color(pos)))
                    .append(", width=").append(DotLiterals.formatDouble(nodes.width(node)))
                    .append(", height=").append(DotLiterals.formatDouble(nodes.height(node)))
                    .append(", fontsize=").append(nodes.fontSize(node));
            if (synonym) {
                dot.append(", style=").append(DotLiterals.quoted(LexicalGraphBorders.SYNONYM_BORDER_STYLE));
            }
            if (approved) {
                dot.append(", penwidth=")
                        .append(DotLiterals.formatDouble(LexicalGraphBorders.APPROVED_BORDER_PENWIDTH));
            }
            dot.append("];\n");
        }
    }

    /** Adds the directed relationships (governor → dependent), one per collected edge. */
    private void appendEdges(final StringBuilder dot, final LexicalGraphNodes nodes) {
        dot.append("\n    // Relationships (Edges)\n");
        for (final String[] edge : nodes.edges()) {
            dot.append("    ").append(DotLiterals.quoted(nodes.idForKey(edge[0])))
                    .append(" -> ").append(DotLiterals.quoted(nodes.idForKey(edge[1])))
                    .append(" [label=").append(DotLiterals.quoted(edge[2])).append("];\n");
        }
    }
}
