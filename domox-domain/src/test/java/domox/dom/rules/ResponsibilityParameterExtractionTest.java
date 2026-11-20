package domox.dom.rules;

import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit guardrail for {@link ResponsibilityParameterExtraction} — the operation signature's parameter
 * list.
 *
 * <p>Every rendered operation used to be a bare name (191 of them, and zero {@code ResponsibilityParameter}
 * rows), because the operation rules record the verb and nothing else. These cases pin what counts as
 * a parameter (the verb's own objects, nouns only) and what does not (the subject — that is the
 * operation's owner — and another verb's objects).</p>
 */
class ResponsibilityParameterExtractionTest {

    /** A dependency in {@code sentence}: {@code type(governorLemma, dependentLemma)}. */
    private static TypedDependency dependency(
            final Sentence sentence,
            final TdType type,
            final String governorLemma,
            final String dependentLemma,
            final PartOfSpeechType dependentPos) {
        final TypedDependency dependency = new TypedDependency();
        dependency.setType(type);
        dependency.setSentence(sentence);
        dependency.setGovernorLemma(governorLemma);
        dependency.setGovernorPos(PartOfSpeechType.VB);
        dependency.setDependentLemma(dependentLemma);
        dependency.setDependentPos(dependentPos);
        sentence.addTypedDependency(dependency);
        return dependency;
    }

    /** "the customer browses a pet" → browse(pet). */
    @Test
    void collectsTheVerbsDirectObject() {
        final Sentence sentence = new Sentence();
        final TypedDependency nsubj = dependency(sentence, TdType.NSUBJ, "browse", "customer", PartOfSpeechType.NN);
        dependency(sentence, TdType.OBJ, "browse", "pet", PartOfSpeechType.NN);

        assertThat(ResponsibilityParameterExtraction.objectNounsOf(nsubj, "Browse"))
                .as("the verb's object is the parameter; the subject is the owner")
                .containsExactly("pet");
    }

    @Test
    void ignoresObjectsOfAnotherVerb() {
        final Sentence sentence = new Sentence();
        final TypedDependency nsubj = dependency(sentence, TdType.NSUBJ, "browse", "customer", PartOfSpeechType.NN);
        dependency(sentence, TdType.OBJ, "select", "product", PartOfSpeechType.NN);

        assertThat(ResponsibilityParameterExtraction.objectNounsOf(nsubj, "Browse")).isEmpty();
    }

    @Test
    void ignoresANonNounObject() {
        final Sentence sentence = new Sentence();
        final TypedDependency nsubj = dependency(sentence, TdType.NSUBJ, "browse", "customer", PartOfSpeechType.NN);
        dependency(sentence, TdType.OBJ, "browse", "quickly", PartOfSpeechType.RB);

        assertThat(ResponsibilityParameterExtraction.objectNounsOf(nsubj, "browse")).isEmpty();
    }

    @Test
    void collectsIndirectObjectsOnceAndInOrder() {
        final Sentence sentence = new Sentence();
        final TypedDependency nsubj = dependency(sentence, TdType.NSUBJ, "notify", "system", PartOfSpeechType.NN);
        dependency(sentence, TdType.OBL, "notify", "customer", PartOfSpeechType.NN);
        dependency(sentence, TdType.OBJ, "notify", "message", PartOfSpeechType.NN);
        dependency(sentence, TdType.OBL_FOR, "notify", "customer", PartOfSpeechType.NN);

        assertThat(ResponsibilityParameterExtraction.objectNounsOf(nsubj, "notify"))
                .as("a repeated object is one parameter, and the parser's order is kept")
                .containsExactly("customer", "message");
    }

    @Test
    void returnsEmptyWithoutASentenceOrAVerb() {
        final Sentence sentence = new Sentence();
        final TypedDependency withoutSentence = new TypedDependency();
        final TypedDependency nsubj = dependency(sentence, TdType.NSUBJ, "browse", "customer", PartOfSpeechType.NN);

        assertThat(ResponsibilityParameterExtraction.objectNounsOf(null, "browse")).isEmpty();
        assertThat(ResponsibilityParameterExtraction.objectNounsOf(nsubj, null)).isEmpty();
        assertThat(ResponsibilityParameterExtraction.objectNounsOf(withoutSentence, "browse"))
                .as("a dependency with no sentence has no scope to read")
                .isEmpty();
    }
}
