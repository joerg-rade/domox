package domox.dom.nlp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link LexicalGraphGenerator}: the PlantUML component graph is drawn
 * straight from persisted {@link TypedDependency} data (lemmas + POS + relation code),
 * filtering out non-content words and the ROOT pseudo-token.
 */
class LexicalGraphGeneratorTest {

    private final LexicalGraphGenerator generator = new LexicalGraphGenerator();

    @Test
    void producesPlantUmlComponentsAndEdgesFromTypedDependencies() {
        final String puml = generator.generatePlantUmlGraph(sampleDependencies());

        // document framing (each append ends with a newline)
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.trim().endsWith("@enduml"));
        assertTrue(puml.contains("skinparam componentStyle uml2"));

        // component nodes use the lowercased lemma as alias and POS code as stereotype
        assertTrue(puml.contains("component [artificial] as artificial <<JJ>>"));
        assertTrue(puml.contains("component [intelligence] as intelligence <<NN>>"));
        assertTrue(puml.contains("component [transform] as transform <<VBZ>>"));

        // directed relationships from the typed dependencies (governor -> dependent)
        assertTrue(puml.contains("intelligence --> artificial : amod"));
        assertTrue(puml.contains("transform --> intelligence : nsubj"));
        assertTrue(puml.contains("technology --> modern : amod"));
        assertTrue(puml.contains("transform --> technology : obj"));
    }

    @Test
    void filtersOutNonContentWordsAndRootPseudoToken() {
        final String puml = generator.generatePlantUmlGraph(sampleDependencies());

        // 'the' (DT) is not a content word -> no node and no edge referencing it
        assertFalse(puml.contains("as the <<DT>>"));
        assertFalse(puml.contains("--> the"));

        // the ROOT pseudo-token (index 0) has no governor lemma -> no root edge
        assertFalse(puml.contains(": root"));
    }

    @Test
    void emptyDependencyListYieldsBareDiagram() {
        final String puml = generator.generatePlantUmlGraph(List.of());

        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.trim().endsWith("@enduml"));
        assertFalse(puml.contains("[intelligence]"));
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
}
