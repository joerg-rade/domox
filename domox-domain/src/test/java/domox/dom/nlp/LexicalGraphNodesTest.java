package domox.dom.nlp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level guardrail for the lexical graph's node/edge model: which dependencies become nodes,
 * how a node is keyed and named, and how its rule-match count becomes a size.
 */
class LexicalGraphNodesTest {

    @Test
    void skipsRootPseudoTokensAndNonContentWords() {
        final LexicalGraphNodes nodes = LexicalGraphNodes.collect(List.of(
                dep(TdType.NSUBJ, 2, "transform", PartOfSpeechType.VBZ, 1, "customer", PartOfSpeechType.NN),
                dep(TdType.DET, 2, "transform", PartOfSpeechType.VBZ, 3, "the", PartOfSpeechType.DT),
                dep(TdType.ROOT, 0, null, null, 2, "transform", PartOfSpeechType.VBZ)));

        assertThat(nodes.nodes()).extracting(n -> n[0]).containsExactly("transform", "customer");
    }

    @Test
    void skipsANullDependency() {
        final List<TypedDependency> deps = new ArrayList<>();
        deps.add(null);
        deps.add(dep(TdType.NSUBJ, 2, "transform", PartOfSpeechType.VBZ, 1, "customer", PartOfSpeechType.NN));

        assertThat(LexicalGraphNodes.collect(deps).nodes()).hasSize(2);
    }

    @Test
    void aLemmaWithOnePosKeepsABareIdAndOneWithSeveralGetsASuffix() {
        final LexicalGraphNodes nodes = LexicalGraphNodes.collect(List.of(
                dep(TdType.NSUBJ, 2, "shop", PartOfSpeechType.NN, 1, "customer", PartOfSpeechType.NN),
                dep(TdType.AMOD, 2, "shop", PartOfSpeechType.NN, 3, "shop", PartOfSpeechType.VB)));

        final List<String> ids = nodes.nodes().stream().map(nodes::id).toList();
        assertThat(ids).contains("shop_nn", "shop_vb", "customer");
    }

    @Test
    void collectsOneEdgePerDependencyWithItsRelationCode() {
        final LexicalGraphNodes nodes = LexicalGraphNodes.collect(List.of(
                dep(TdType.NSUBJ, 2, "transform", PartOfSpeechType.VBZ, 1, "customer", PartOfSpeechType.NN)));

        assertThat(nodes.edges()).hasSize(1);
        assertThat(nodes.edges().getFirst()[2]).isEqualTo(TdType.NSUBJ.getCode());
    }

    @Test
    void scalesANodeByItsRuleMatchCountUpToFour() {
        final TypedDependency low = dep(TdType.NSUBJ, 2, "transform", PartOfSpeechType.VBZ, 1, "customer", PartOfSpeechType.NN);
        final TypedDependency high = dep(TdType.OBJ, 2, "transform", PartOfSpeechType.VBZ, 3, "order", PartOfSpeechType.NN);
        addRuleMatches(high, 3);   // customer: 0 matches, order: 3

        final LexicalGraphNodes nodes = LexicalGraphNodes.collect(List.of(low, high));
        final String[] customer = nodeFor(nodes, "customer");
        final String[] order = nodeFor(nodes, "order");

        assertThat(nodes.scale(customer)).as("a count below the minimum is clamped to scale 1").isEqualTo(1.0);
        assertThat(nodes.scale(order)).as("the largest node maps to scale 4").isEqualTo(4.0);
        assertThat(nodes.width(order)).isEqualTo(3.6);
        assertThat(nodes.height(order)).isEqualTo(1.4);
        assertThat(nodes.fontSize(order)).isEqualTo(40);
    }

    @Test
    void everyNodeIsScaleOneWhenNoNodeHasMoreThanTheMinimumMatchCount() {
        final LexicalGraphNodes nodes = LexicalGraphNodes.collect(List.of(
                dep(TdType.NSUBJ, 2, "transform", PartOfSpeechType.VBZ, 1, "customer", PartOfSpeechType.NN)));

        final String[] customer = nodeFor(nodes, "customer");
        assertThat(nodes.scale(customer)).isEqualTo(1.0);
        assertThat(nodes.width(customer)).isEqualTo(0.9);
        assertThat(nodes.height(customer)).isEqualTo(0.35);
        assertThat(nodes.fontSize(customer)).isEqualTo(10);
    }

    @Test
    void lowerCasesALemmaAndMapsNullToEmpty() {
        assertThat(LexicalGraphNodes.lower("Customer")).isEqualTo("customer");
        assertThat(LexicalGraphNodes.lower(null)).isEmpty();
    }

    // --- helpers ---

    private static String[] nodeFor(final LexicalGraphNodes nodes, final String lemma) {
        return nodes.nodes().stream().filter(n -> n[0].equals(lemma)).findFirst().orElseThrow();
    }

    private static TypedDependency dep(TdType type, int governorIndex, String governorLemma,
                                       PartOfSpeechType governorPos, int dependentIndex,
                                       String dependentLemma, PartOfSpeechType dependentPos) {
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

    private static void addRuleMatches(final TypedDependency td, final int count) {
        for (int i = 0; i < count; i++) {
            td.getRuleMatches().add(new domox.dom.rules.RuleMatch());
        }
    }
}
