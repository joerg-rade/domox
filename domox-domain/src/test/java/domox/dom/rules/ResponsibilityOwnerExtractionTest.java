package domox.dom.rules;

import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit guardrail for {@link ResponsibilityOwnerExtraction} — the operation's <b>owner</b>.
 *
 * <p>The action rules record the verb's <em>object</em> as the operation's related candidate, so every
 * operation was owned by the thing it was done to ("the customer selects products" →
 * {@code Product.select(...)}). These cases pin what counts as the owner (the verb's own {@code nsubj}
 * subject, nouns only) and what does not — another verb's subject, a non-noun, and a passive subject,
 * which is the patient rather than the actor.</p>
 */
class ResponsibilityOwnerExtractionTest {

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

    /** "the customer browses a pet" → the owner is {@code customer}. */
    @Test
    void collectsTheVerbsSubject() {
        final Sentence sentence = new Sentence();
        final TypedDependency nsubj = dependency(sentence, TdType.NSUBJ, "browse", "customer", PartOfSpeechType.NN);
        dependency(sentence, TdType.OBJ, "browse", "pet", PartOfSpeechType.NN);

        assertThat(ResponsibilityOwnerExtraction.actorNounOf(nsubj, "browse"))
                .as("the subject is the owner; the object is the parameter")
                .isEqualTo("customer");
    }

    /** The rule records the verb capitalised in some cases, lower-case in others. */
    @Test
    void matchesTheVerbCaseInsensitively() {
        final Sentence sentence = new Sentence();
        final TypedDependency nsubj = dependency(sentence, TdType.NSUBJ, "Browse", "customer", PartOfSpeechType.NN);

        assertThat(ResponsibilityOwnerExtraction.actorNounOf(nsubj, "browse")).isEqualTo("customer");
        assertThat(ResponsibilityOwnerExtraction.actorNounOf(nsubj, "BROWSE")).isEqualTo("customer");
    }

    @Test
    void ignoresASubjectOfAnotherVerb() {
        final Sentence sentence = new Sentence();
        final TypedDependency nsubj = dependency(sentence, TdType.NSUBJ, "select", "customer", PartOfSpeechType.NN);

        assertThat(ResponsibilityOwnerExtraction.actorNounOf(nsubj, "browse")).isNull();
    }

    @Test
    void ignoresANonNounSubject() {
        final Sentence sentence = new Sentence();
        final TypedDependency nsubj = dependency(sentence, TdType.NSUBJ, "browse", "it", PartOfSpeechType.PRP);

        assertThat(ResponsibilityOwnerExtraction.actorNounOf(nsubj, "browse")).isNull();
    }

    /**
     * A passive subject is the <em>patient</em>, not the actor: "the payment is processed" says nothing
     * about who processes it, so reading {@code nsubj:pass} as the owner would move the defect rather
     * than fix it.
     */
    @Test
    void ignoresAPassiveSubject() {
        final Sentence sentence = new Sentence();
        final TypedDependency nsubjPass =
                dependency(sentence, TdType.NSUBJ_PASS, "process", "payment", PartOfSpeechType.NN);

        assertThat(ResponsibilityOwnerExtraction.actorNounOf(nsubjPass, "process")).isNull();
    }

    @Test
    void returnsNullWithoutASentenceOrAVerb() {
        final Sentence sentence = new Sentence();
        final TypedDependency withoutSentence = new TypedDependency();
        final TypedDependency nsubj = dependency(sentence, TdType.NSUBJ, "browse", "customer", PartOfSpeechType.NN);

        assertThat(ResponsibilityOwnerExtraction.actorNounOf(null, "browse")).isNull();
        assertThat(ResponsibilityOwnerExtraction.actorNounOf(nsubj, null)).isNull();
        assertThat(ResponsibilityOwnerExtraction.actorNounOf(withoutSentence, "browse"))
                .as("a dependency with no sentence has no scope to read")
                .isNull();
    }
}
