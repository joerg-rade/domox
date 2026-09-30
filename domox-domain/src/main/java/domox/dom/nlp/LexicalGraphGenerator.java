package domox.dom.nlp;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Generates a PlantUML "component" dependency graph from the persisted
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

    /**
     * @param dependencies the typed dependencies to draw; ROOT pseudo-token
     *                     dependencies (governor index 0) are skipped
     * @return PlantUML component-graph source, framed by {@code @startuml}/{@code @enduml}
     */
    public String generatePlantUmlGraph(final Collection<TypedDependency> dependencies) {
        final StringBuilder plantUml = new StringBuilder();
        plantUml.append("@startuml\n");
        plantUml.append("' PlantUML Lexical Dependency Graph\n");
        plantUml.append("skinparam componentStyle uml2\n");

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

            // Define PlantUML Component Nodes with POS stereotype
            plantUml.append(component(sourceLemma, sourcePos)).append("\n");
            plantUml.append(component(targetLemma, targetPos)).append("\n");

            // Add Directed Relationship
            plantUml.append(sourceLemma).append(" --> ").append(targetLemma)
                    .append(" : ").append(relation).append("\n");
        }

        plantUml.append("@enduml\n");
        return plantUml.toString();
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

    private static String component(final String lemma, final String pos) {
        final String stereotype = pos.isEmpty() ? "" : " <<" + pos + ">>";
        return "component [" + lemma + "] as " + lemma + stereotype;
    }

    private static String lower(final String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
