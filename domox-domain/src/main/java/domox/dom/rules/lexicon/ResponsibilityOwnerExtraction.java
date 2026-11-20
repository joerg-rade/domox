package domox.dom.rules.lexicon;

import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.CandidateFactory;

import java.util.Locale;

import static domox.dom.nlp.DependencyPosPredicates.isNounB;
import static domox.dom.nlp.DependencyTypePredicates.isNsubj;

/**
 * Derives the <b>owner</b> of an operation from the sentence the operation was extracted from.
 *
 * <p><b>Why it exists.</b> An operation belongs to the class that <em>performs</em> it, and the
 * performer is the sentence's subject: {@code "the customer selects products"} is
 * {@code Customer.select(products)}, not {@code Product.select(products)}. The action rules could not
 * state that — {@code TDR3} fires on the <em>object</em> dependency ({@code dobj}/{@code iobj}/{@code pobj})
 * and records that object as the operation's related candidate, so every operation the factory created
 * was owned by the thing it was done <em>to</em>. Measured on the PetShop model (2026-11-20): of the
 * 149 owned operations <em>every single one</em> was owned by its object noun and <em>none</em> by its
 * actor — systematic, not sporadic ({@code UML_GAP_FIX_PLAN.md} §13.8, FIX-3d).</p>
 *
 * <p><b>What counts as the actor.</b> The dependent of an {@code nsubj} relation whose governor
 * <em>is that verb</em> and whose dependent is a noun. Two deliberate exclusions:</p>
 * <ul>
 *   <li>{@code nsubj:pass} is <em>not</em> the actor — in a passive clause it is the patient
 *       ({@code "the payment is processed"}), so reading it would move the defect rather than fix it.
 *       A passive sentence therefore yields no actor here and the caller falls back to the object.</li>
 *   <li>a non-noun dependent ({@code "there is …"}) is not an actor.</li>
 * </ul>
 * <p>The blocked use-case noun guard ({@code System}, {@code Case}) is deliberately <em>not</em> applied
 * here: that is a candidate-creation policy owned by {@link CandidateFactory#isBlockedUseCaseNoun}, and
 * this utility answers only the grammatical question.</p>
 *
 * <p><b>Why it is a pure collaborator.</b> No dependencies beyond the domain objects handed to it, so it
 * is testable without Spring, a database or CoreNLP, and it keeps the branch out of
 * {@link CandidateFactory} (pitest floor 95 %). The mirror of {@link ResponsibilityParameterExtraction}:
 * that one reads the verb's <em>objects</em> (the parameters), this one reads its <em>subject</em> (the
 * owner), and between them they exclude exactly what the other claims.</p>
 */
public final class ResponsibilityOwnerExtraction {

    private ResponsibilityOwnerExtraction() {
    }

    /**
     * The actor noun of {@code verbLemma} in the sentence of {@code actionDependency} — the operation's
     * owner, as the sentence states it.
     *
     * @param actionDependency the dependency the operation rule fired on — its sentence is the scope
     * @param verbLemma        the operation's verb (any case; compared against the governor lemma)
     * @return the actor noun, or {@code null} when the sentence states no usable actor
     */
    public static String actorNounOf(final TypedDependency actionDependency, final String verbLemma) {
        if (actionDependency == null || verbLemma == null) {
            return null;
        }
        final Sentence sentence = actionDependency.getSentence();
        if (sentence == null || sentence.getTypedDependencies() == null) {
            return null;
        }
        final String verb = verbLemma.toLowerCase(Locale.ROOT);
        for (final TypedDependency dependency : sentence.getTypedDependencies()) {
            if (isActorOf(dependency, verb)) {
                return dependency.getB();
            }
        }
        return null;
    }

    /** Whether {@code dependency} is the subject relation of {@code verb} (lower-cased). */
    private static boolean isActorOf(final TypedDependency dependency, final String verb) {
        if (dependency == null || dependency.getGovernorLemma() == null) {
            return false;
        }
        return isNsubj(dependency)
                && isNounB(dependency)
                && verb.equals(dependency.getGovernorLemma().toLowerCase(Locale.ROOT));
    }
}
