package domox.dom.nlp;

import domox.dom.crc.ClassCdd;
import domox.dom.crc.Review;
import domox.dom.crc.ReviewStatus;
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
    void usesNeatoLayoutThatKrokiCanRender() {
        final String dot = generator.generateGraphvizGraph(sampleDependencies());

        // `layout = neato` (force-directed) is used, and Kroki honours it over the renderer's own
        // request options. `sfdp` is not usable: Kroki's Graphviz build cannot run it (missing
        // triangulation), so `layout = sfdp` would make the /graphviz endpoint return HTTP 400 and
        // the whole PDF (Document.getDiagram()) fail.
        // Assert on the graph-attribute block only (not the whole DOT), since explanatory
        // comments legitimately mention `sfdp`.
        final int graphStart = dot.indexOf("graph [");
        final int graphEnd = dot.indexOf("];", graphStart);
        final String graphBlock = dot.substring(graphStart, graphEnd);
        assertTrue(graphBlock.contains("layout = neato"),
                "generator's graph block must use the 'neato' engine for Kroki compatibility:\n" + graphBlock);
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
        // no content-node declarations are emitted for an empty list
        final int nodesStart = dot.indexOf("// Node Declarations");
        final int nodesEnd = dot.indexOf("// Relationships (Edges)");
        assertFalse(dot.substring(nodesStart, nodesEnd).contains("[label="),
                "empty list -> no content node declarations");
        // ... but the explanatory legend is still rendered
        assertTrue(dot.contains("Legend ["), "empty list -> legend still emitted");
    }

    @Test
    void approvedCandidateGetsBoldBorder() {
        final String dot = generator.generateGraphvizGraph(
                List.of(dep(TdType.NSUBJ, 1, "store", PartOfSpeechType.NN,
                        2, "customer", PartOfSpeechType.NN)),
                List.of(approvedClassCandidate("Store")));

        // the approved term ('Store', matched case-insensitively) gets a bold border ...
        assertTrue(dot.contains("\"store\" [label=\"store\\n«NN»\", fillcolor=\"#3498DB\", width=0.900, height=0.350, fontsize=10, penwidth=3.000];"), dot);
        // ... while a node without an approved candidate keeps the plain declaration
        assertTrue(dot.contains("\"customer\" [label=\"customer\\n«NN»\", fillcolor=\"#3498DB\", width=0.900, height=0.350, fontsize=10];"), dot);
        assertFalse(dataSection(dot).contains("dashed"), "approved alone must not produce a dashed border:\n" + dot);
    }

    @Test
    void rejectedCandidateDoesNotGetBoldBorder() {
        final ClassCdd store = new ClassCdd();
        store.setCandidateName("Store");
        final Review review = new Review();
        review.setStatus(ReviewStatus.REJECTED);
        store.getReviews().add(review);

        final String dot = generator.generateGraphvizGraph(
                List.of(dep(TdType.NSUBJ, 1, "store", PartOfSpeechType.NN,
                        2, "customer", PartOfSpeechType.NN)),
                List.of(store));

        assertTrue(dot.contains("\"store\" [label=\"store\\n«NN»\", fillcolor=\"#3498DB\", width=0.900, height=0.350, fontsize=10];"), dot);
        assertFalse(dataSection(dot).contains("penwidth"), "a REJECTED review must not emphasise the border:\n" + dot);
    }

    @Test
    void synonymCandidateGetsDashedBorder() {
        final String dot = generator.generateGraphvizGraph(
                List.of(dep(TdType.NSUBJ, 1, "store", PartOfSpeechType.NN,
                        2, "shop", PartOfSpeechType.NN)),
                List.of(synonymClassCandidate("Shop", "Store")));

        // both members of the synonym pair ('Shop' == 'Store') get a dashed border
        assertTrue(dot.contains("\"store\" [label=\"store\\n«NN»\", fillcolor=\"#3498DB\", width=0.900, height=0.350, fontsize=10, style=\"rounded,filled,dashed\"];"), dot);
        assertTrue(dot.contains("\"shop\" [label=\"shop\\n«NN»\", fillcolor=\"#3498DB\", width=0.900, height=0.350, fontsize=10, style=\"rounded,filled,dashed\"];"), dot);
        assertFalse(dataSection(dot).contains("penwidth"), "synonym alone must not produce a bold border:\n" + dot);
    }

    @Test
    void approvedSynonymCandidateCombinesBoldAndDashedBorders() {
        final ClassCdd store = approvedClassCandidate("Store");
        store.getRuleMatches().add(synonymMatch("Store", "Shop"));

        final String dot = generator.generateGraphvizGraph(
                List.of(dep(TdType.NSUBJ, 1, "store", PartOfSpeechType.NN,
                        2, "shop", PartOfSpeechType.NN)),
                List.of(store));

        // an approved synonym member carries both the dashed style and the bold penwidth
        assertTrue(dot.contains("\"store\" [label=\"store\\n«NN»\", fillcolor=\"#3498DB\", width=0.900, height=0.350, fontsize=10, style=\"rounded,filled,dashed\", penwidth=3.000];"), dot);
    }

    @Test
    void noCandidateSnapshotLeavesNodesUnstyled() {
        final String plain = generator.generateGraphvizGraph(sampleDependencies());
        final String plainData = dataSection(plain);
        assertFalse(plainData.contains("penwidth"), "plain graph must not emit border emphasis:\n" + plain);
        assertFalse(plainData.contains("dashed"), "plain graph must not emit a dashed border style:\n" + plain);
        // the legend still explains the border semantics even without a candidate snapshot
        assertTrue(plain.contains("Legend ["), plain);
        assertTrue(plain.contains("Part of a synonym pair"), plain);

        final String withSnapshot = generator.generateGraphvizGraph(
                sampleDependencies(), List.of(approvedClassCandidate("Intelligence")));
        assertTrue(dataSection(withSnapshot).contains("penwidth=3.000"), withSnapshot);
    }

    @Test
    void alwaysRendersLegendExplainingColoursSizeAndBorders() {
        final String dot = generator.generateGraphvizGraph(sampleDependencies());

        // the legend is a single HTML-table node labelled "Node Type Legend"
        assertTrue(dot.contains("Legend ["), dot);
        assertTrue(dot.contains("shape = plain"), dot);
        assertTrue(dot.contains("Node Type Legend"), dot);

        // size semantics
        assertTrue(dot.contains("Node size"), dot);

        // border semantics: dashed (synonym), bold (approved) and the combined case
        assertTrue(dot.contains("Dashed border"), dot);
        assertTrue(dot.contains("Part of a synonym pair"), dot);
        assertTrue(dot.contains("Bold border"), dot);
        assertTrue(dot.contains("Approved candidate"), dot);
        assertTrue(dot.contains("Bold + dashed"), dot);
        assertTrue(dot.contains("Approved synonym member"), dot);

        // colour swatches: a representative sample with their POS tags
        assertTrue(dot.contains("Entities and objects («NN», «NNS»)"), dot);
        assertTrue(dot.contains("Named entities («NNP», «NNPS»)"), dot);
        assertTrue(dot.contains("Actions and predicates («VB», «VBZ»)"), dot);
        assertTrue(dot.contains("Descriptors and modifiers («JJ», «JJR», «JJS»)"), dot);
        assertTrue(dot.contains("bgcolor=\"#3498DB\""), dot);
        assertTrue(dot.contains("bgcolor=\"#85C1E9\""), dot);
        assertTrue(dot.contains("bgcolor=\"#E74C3C\""), dot);
        assertTrue(dot.contains("bgcolor=\"#2ECC71\""), dot);
        assertFalse(dot.contains("Pronoun / wh-word"), "colour rows reduced to the four POS families:\n" + dot);
        assertFalse(dot.contains("Punctuation"), "colour rows reduced to the four POS families:\n" + dot);
        assertFalse(dot.contains("Adverb"), "colour rows reduced to the four POS families:\n" + dot);

        // The corner placement is handled by the renderer's SVG post-processing (not by DOT):
        // neato can't place a node in a known corner, so no pinned `pos` and no dot-only
        // `rank = sink` idiom are emitted.
        assertFalse(dot.contains("pos ="), "no pinned `pos` — corner placement is the renderer's job:\n" + dot);
        assertFalse(dot.contains("rank = sink"), dot);
    }

    /** The data region of the DOT: everything up to (but excluding) the legend node. */
    private static String dataSection(final String dot) {
        final int legendStart = dot.indexOf("Legend [");
        return legendStart < 0 ? dot : dot.substring(0, legendStart);
    }

    /** A class candidate whose reviews contain one APPROVED review. */
    private static ClassCdd approvedClassCandidate(final String name) {
        final ClassCdd candidate = new ClassCdd();
        candidate.setCandidateName(name);
        final Review review = new Review();
        review.setStatus(ReviewStatus.APPROVED);
        candidate.getReviews().add(review);
        return candidate;
    }

    /**
     * A class candidate carrying a {@code SynonymCdd} rule match naming {@code name} and its
     * synonym partner {@code partner} (mirroring the TDR41 match attached to the synonym association).
     */
    private static ClassCdd synonymClassCandidate(final String name, final String partner) {
        final ClassCdd candidate = new ClassCdd();
        candidate.setCandidateName(name);
        candidate.getRuleMatches().add(synonymMatch(name, partner));
        return candidate;
    }

    private static RuleMatch synonymMatch(final String name, final String partner) {
        final RuleMatch match = new RuleMatch();
        match.setCandidateType("SynonymCdd");
        match.setCandidateName(name);
        match.setRelatedCandidateName(partner);
        return match;
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
