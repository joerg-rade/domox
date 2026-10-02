package domox.dom.nlp;

import domox.dom.rules.RuleMatch;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link LexicalGraphGenerator}: the Graphviz (DOT) lexical dependency graph
 * is drawn straight from persisted {@link TypedDependency} data (lemmas + POS + relation
 * code), filtering out non-content words and the ROOT pseudo-token.
 */
class LexicalGraphGeneratorTest {

    private final LexicalGraphGenerator generator = new LexicalGraphGenerator();

    @Test
    void producesGraphvizNodesAndEdgesFromTypedDependencies() {
        final String dot = generator.generateGraphvizGraph(sampleDependencies());

        // document framing
        assertTrue(dot.startsWith("digraph LexicalDependencyGraph {"));
        assertTrue(dot.trim().endsWith("}"));

        // node declarations use the lowercased lemma and the POS code as a second label line,
        // coloured by POS and unscaled (no rule matches -> width/height and font at base size)
        assertTrue(dot.contains("\"intelligence\" [label=\"intelligence\\n«NN»\", fillcolor=\"#3498DB\", width=0.900, height=0.350, fontsize=10];"));
        assertTrue(dot.contains("\"artificial\" [label=\"artificial\\n«JJ»\", fillcolor=\"#2ECC71\", width=0.900, height=0.350, fontsize=10];"));
        assertTrue(dot.contains("\"transform\" [label=\"transform\\n«VBZ»\", fillcolor=\"#E74C3C\", width=0.900, height=0.350, fontsize=10];"));

        // directed relationships from the typed dependencies (governor -> dependent)
        assertTrue(dot.contains("\"intelligence\" -> \"artificial\" [label=\"amod\"];"));
        assertTrue(dot.contains("\"transform\" -> \"intelligence\" [label=\"nsubj\"];"));
        assertTrue(dot.contains("\"technology\" -> \"modern\" [label=\"amod\"];"));
        assertTrue(dot.contains("\"transform\" -> \"technology\" [label=\"obj\"];"));
    }

    @Test
    void usesDotLayoutThatKrokiCanRender() {
        final String dot = generator.generateGraphvizGraph(sampleDependencies());

        // `layout = dot` (hierarchical) is required: Kroki's Graphviz build cannot run
        // force-directed `sfdp` (missing triangulation), so `layout = sfdp` would make the
        // /graphviz endpoint return HTTP 400 and the whole PDF (Document.getDiagram()) fail.
        // Assert on the graph-attribute block only (not the whole DOT), since explanatory
        // comments legitimately mention `sfdp`.
        final int graphStart = dot.indexOf("graph [");
        final int graphEnd = dot.indexOf("];", graphStart);
        final String graphBlock = dot.substring(graphStart, graphEnd);
        assertTrue(graphBlock.contains("layout = dot"),
                "generator's graph block must use the 'dot' engine for Kroki compatibility:\n" + graphBlock);
    }

    @Test
    void disambiguatesNodesThatShareALemmaAcrossPosTags() {
        // 'shop' appears both as a noun (NN) and a verb (VB) -> two distinct nodes
        final List<TypedDependency> deps = new ArrayList<>();
        deps.add(dep(TdType.OBL_AT, 1, "arrive", PartOfSpeechType.VBZ,
                2, "shop", PartOfSpeechType.NN));
        deps.add(dep(TdType.NSUBJ, 3, "shop", PartOfSpeechType.VB,
                4, "customer", PartOfSpeechType.NN));

        final String dot = generator.generateGraphvizGraph(deps);

        assertTrue(dot.contains("\"shop_nn\" [label=\"shop\\n«NN»\", fillcolor=\"#3498DB\", width=0.900, height=0.350, fontsize=10];"));
        assertTrue(dot.contains("\"shop_vb\" [label=\"shop\\n«VB»\", fillcolor=\"#E74C3C\", width=0.900, height=0.350, fontsize=10];"));
        assertTrue(dot.contains("\"arrive\" -> \"shop_nn\" [label=\"obl:at\"];"));
        assertTrue(dot.contains("\"shop_vb\" -> \"customer\" [label=\"nsubj\"];"));
    }

    @Test
    void coloursNodesByPosAndScalesByRuleMatchCount() {
        // nsubj(transform, intelligence) carries 3 rule-matches -> both endpoints get a count of 3
        final List<TypedDependency> deps = new ArrayList<>();
        final TypedDependency nsubj = dep(TdType.NSUBJ, 3, "transform", PartOfSpeechType.VBZ,
                2, "intelligence", PartOfSpeechType.NN);
        addRuleMatches(nsubj, 3);
        deps.add(nsubj);
        deps.add(dep(TdType.AMOD, 2, "intelligence", PartOfSpeechType.NN,
                1, "artificial", PartOfSpeechType.JJ));
        deps.add(dep(TdType.OBJ, 3, "transform", PartOfSpeechType.VBZ,
                4, "technology", PartOfSpeechType.NN));
        deps.add(dep(TdType.AMOD, 4, "technology", PartOfSpeechType.NN,
                5, "modern", PartOfSpeechType.JJ));

        final String dot = generator.generateGraphvizGraph(deps);

        // maximum count (3, for transform and intelligence) maps to scale 4, scaling both
        // dimensions and the font size by the same factor
        assertTrue(dot.contains("\"transform\" [label=\"transform\\n«VBZ»\", fillcolor=\"#E74C3C\", width=3.600, height=1.400, fontsize=40];"));
        assertTrue(dot.contains("\"intelligence\" [label=\"intelligence\\n«NN»\", fillcolor=\"#3498DB\", width=3.600, height=1.400, fontsize=40];"));
        // a count of 1 leaves the node at its base size (no enlargement)
        assertTrue(dot.contains("\"modern\" [label=\"modern\\n«JJ»\", fillcolor=\"#2ECC71\", width=0.900, height=0.350, fontsize=10];"));
    }


    @Test
    void quotesAndEscapesNodeAndEdgeIdsForSpecialCharactersAndKeywords() {
        // Hyphenated lemmas, a DOT keyword ('node') and a digit-leading id must survive as valid,
        // quoted DOT ids. Previously these were emitted unquoted, so such a document's graph was
        // rejected by Graphviz/Kroki with HTTP 400 and took the whole list view down.
        final List<TypedDependency> deps = new ArrayList<>();
        deps.add(dep(TdType.AMOD, 1, "data-center", PartOfSpeechType.NN,
                2, "node", PartOfSpeechType.JJ));
        deps.add(dep(TdType.NSUBJ, 3, "123abc", PartOfSpeechType.NNP,
                4, "data-center", PartOfSpeechType.NN));

        final String dot = generator.generateGraphvizGraph(deps);

        assertTrue(dot.contains("\"data-center\" [label=\"data-center\\n«NN»\", fillcolor=\"#3498DB\""), dot);
        assertTrue(dot.contains("\"data-center\" -> \"node\" [label=\"amod\"];"), dot);
        assertTrue(dot.contains("\"123abc\" [label=\"123abc\\n«NNP»\", fillcolor=\"#85C1E9\""), dot);
        assertTrue(dot.contains("\"123abc\" -> \"data-center\" [label=\"nsubj\"];"), dot);
    }

    @Test
    void filtersOutNonContentWordsAndRootPseudoToken() {
        final String dot = generator.generateGraphvizGraph(sampleDependencies());

        // 'the' (DT) is not a content word -> no node and no edge referencing it
        assertFalse(dot.contains("the\\n«DT»"));
        assertFalse(dot.contains("-> the"));

        // the ROOT pseudo-token (index 0) has no governor lemma -> no root edge
        assertFalse(dot.contains("ROOT"));
    }

    @Test
    void emptyDependencyListYieldsBareDiagram() {
        final String dot = generator.generateGraphvizGraph(List.of());

        assertTrue(dot.startsWith("digraph LexicalDependencyGraph {"));
        assertTrue(dot.trim().endsWith("}"));
        assertFalse(dot.contains("[label="), "empty list -> no node or edge labels");
    }

    /**
     * Fixture for "Artificial intelligence transforms modern technology."
     * Each dependency carries the persisted lemma/POS data the generator needs.
     */
    private List<TypedDependency> sampleDependencies() {
        final List<TypedDependency> deps = new ArrayList<>();
        // amod(intelligence, artificial): governor=2 (NN), dependent=1 (JJ)
        deps.add(dep(TdType.AMOD, 2, "intelligence", PartOfSpeechType.NN,
                1, "artificial", PartOfSpeechType.JJ));
        // nsubj(transform, intelligence): governor=3 (VBZ), dependent=2 (NN)
        deps.add(dep(TdType.NSUBJ, 3, "transform", PartOfSpeechType.VBZ,
                2, "intelligence", PartOfSpeechType.NN));
        // amod(technology, modern): governor=5 (NN), dependent=4 (JJ)
        deps.add(dep(TdType.AMOD, 5, "technology", PartOfSpeechType.NN,
                4, "modern", PartOfSpeechType.JJ));
        // obj(transform, technology): governor=3 (VBZ), dependent=5 (NN)
        deps.add(dep(TdType.OBJ, 3, "transform", PartOfSpeechType.VBZ,
                5, "technology", PartOfSpeechType.NN));
        // det(technology, the) — non-content dependent -> filtered out
        deps.add(dep(TdType.DET, 5, "technology", PartOfSpeechType.NN,
                6, "the", PartOfSpeechType.DT));
        // ROOT pseudo-token (governor index 0) -> skipped
        deps.add(dep(TdType.ROOT, 0, null, null,
                3, "transform", PartOfSpeechType.VBZ));
        return deps;
    }

    private TypedDependency dep(final TdType type,
                                final int governorIndex,
                                final String governorLemma,
                                final PartOfSpeechType governorPos,
                                final int dependentIndex,
                                final String dependentLemma,
                                final PartOfSpeechType dependentPos) {
        final TypedDependency td = new TypedDependency();
        td.setType(type);
        td.setGovernorIndex(governorIndex);
        td.setGovernorLemma(governorLemma);
        td.setGovernorPos(governorPos);
        td.setDependentIndex(dependentIndex);
        td.setDependentLemma(dependentLemma);
        td.setDependentPos(dependentPos);
        return td;
    }

    /** Attaches {@code count} bare rule-matches to {@code td} so scaling can be exercised. */
    private static void addRuleMatches(final TypedDependency td, final int count) {
        for (int i = 0; i < count; i++) {
            td.getRuleMatches().add(new RuleMatch());
        }
    }
}
