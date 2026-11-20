package domox.dom.rules;

import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static domox.dom.nlp.DependencyPosPredicates.isNounB;
import static domox.dom.nlp.DependencyTypePredicates.dobj;
import static domox.dom.nlp.DependencyTypePredicates.iobj;
import static domox.dom.nlp.DependencyTypePredicates.pobj;

/**
 * Derives the <b>parameters</b> of an operation from the sentence the operation was extracted from.
 *
 * <p><b>Why it exists.</b> The operation rules (TDR27–TDR37) record the verb and nothing else, so every
 * rendered operation was a bare name — 191 operations in the PetShop model and <em>zero</em>
 * {@code ResponsibilityParameter} rows.  A UML operation without its parameters is not a signature, so the
 * object nouns the verb governs are read back out of the sentence's own typed dependencies.</p>
 *
 * <p><b>What counts as a parameter.</b> The direct objects of the verb ({@code dobj}, {@code iobj},
 * {@code pobj}) whose dependent is a noun and whose governor <em>is that verb</em>.  The subject is
 * deliberately excluded: the subject is the operation's <em>owner</em> ("the customer browses …" is
 * {@code Customer.browse()}, not {@code browse(customer)}), and including it would put the owning
 * class on the parameter list of every operation.</p>
 *
 * <p><b>Why it is a pure collaborator.</b> It has no dependencies beyond the domain objects handed to
 * it, so it is testable without Spring, a database or CoreNLP, and it keeps the branch out of
 * {@link CandidateFactory} (pitest floor 95 %).  The <em>type</em> of each parameter is a separate
 * concern — it needs the model — and is resolved by the caller.</p>
 *
 * <p>A utility rather than a {@code @Service} — same shape as {@link MultiplicityEvidence}.</p>
 */
public final class ResponsibilityParameterExtraction {

    private ResponsibilityParameterExtraction() {
    }

    /**
     * The object nouns {@code verbLemma} governs in the sentence of {@code actionDependency}, in the
     * order the parser lists them, without duplicates.
     *
     * @param actionDependency the dependency the operation rule fired on — its sentence is the scope
     * @param verbLemma        the operation's verb (any case; compared against the governor lemma)
     * @return the parameter names, or an empty list when the sentence states none
     */
    public static List<String> objectNounsOf(final TypedDependency actionDependency, final String verbLemma) {
        if (actionDependency == null || verbLemma == null) {
            return List.of();
        }
        final Sentence sentence = actionDependency.getSentence();
        if (sentence == null || sentence.getTypedDependencies() == null) {
            return List.of();
        }
        final String verb = verbLemma.toLowerCase(Locale.ROOT);
        final List<String> parameters = new ArrayList<>();
        for (final TypedDependency dependency : sentence.getTypedDependencies()) {
            if (!isObjectOf(dependency, verb) || !isNounB(dependency)) {
                continue;
            }
            final String noun = dependency.getB();
            if (noun != null && !parameters.contains(noun)) {
                parameters.add(noun);
            }
        }
        return parameters;
    }

    /** Whether {@code dependency} is an object relation governed by {@code verb} (lower-cased). */
    private static boolean isObjectOf(final TypedDependency dependency, final String verb) {
        if (dependency == null || dependency.getGovernorLemma() == null) {
            return false;
        }
        return (dobj(dependency) || iobj(dependency) || pobj(dependency))
                && verb.equals(dependency.getGovernorLemma().toLowerCase(Locale.ROOT));
    }
}
