package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.TypedDependency;

import static domox.dom.nlp.TypedDependencyPredicates.*;

/**
 * TDR38 — Copula-based generalization ("X is a Y").
 * <p>
 * Literature basis: Arora et al. (MODELS'16) pattern B5.
 * <p>
 * Matches when the current dependency is {@code nsubj(ParentHead, ChildNoun)} and
 * the same sentence contains both a {@code cop(ParentHead, beVerb)} and a
 * {@code det(ParentHead, a|an)} dependency.  This captures the classic
 * "is a" / "is an" generalization pattern such as "A Dog is an Animal",
 * producing {@code Dog --|> Animal}.
 * <p>
 * To avoid overlap with TDR39 (kind-of/type-of), this rule excludes the case
 * where the parent head noun is "kind", "type", or "sort".
 */
@RuleBean
@Rule(order = 38)
public class TDR38 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null || currentTd.getSentence() == null) {
            return false;
        }
        // Spec: nsubj(ParentHead, ChildNoun)
        // currentTd = nsubj(ParentHead, ChildNoun)
        //   where ParentHead is the predicate noun (governor A)
        //   and   ChildNoun is the subject noun (dependent B)
        if (!isNsubj(currentTd) || !isNounB(currentTd)) {
            return false;
        }
        final String parentHead = currentTd.getA();
        if (parentHead == null) {
            return false;
        }
        // The parent head must be a noun (governor POS)
        if (currentTd.getGovernorPos() == null ||
                !isNounType(currentTd.getGovernorPos())) {
            return false;
        }
        // Exclude "kind"/"type"/"sort" heads — those belong to TDR39
        if (containsKindTypeOrSort(parentHead)) {
            return false;
        }
        // Scan sentence for cop(ParentHead, beVerb) and det(ParentHead, a|an)
        boolean hasCop = false;
        boolean hasDet = false;
        for (TypedDependency td : currentTd.getSentence().getTypedDependencies()) {
            if (td == currentTd) continue; // skip self
            String gov = td.getA();
            if (gov == null || !gov.equals(parentHead)) continue;
            if (cop(td) && isBeVerbDependent(td)) {
                hasCop = true;
            }
            if (det(td) && isIndefiniteArticleDependent(td)) {
                hasDet = true;
            }
        }
        return hasCop && hasDet;
    }

    @Override
    @Then
    public void then() {
        // parentHead = currentTd.getA() (governor, predicate noun — the parent class)
        // childNoun = currentTd.getB() (dependent, subject noun — the subclass)
        String child = currentTd.getB();
        String parent = currentTd.getA();
        result = "generalization(" + child + " --|> " + parent + ")";

        // Phase 1: record the match
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "GeneralizationCdd",
                    capitalizeFirstLetter(child),
                    "GeneralizationParent",
                    capitalizeFirstLetter(parent),
                    result);
        }
    }

    private static boolean isNounType(domox.dom.nlp.PartOfSpeechType pos) {
        return pos == domox.dom.nlp.PartOfSpeechType.NN
                || pos == domox.dom.nlp.PartOfSpeechType.NNP
                || pos == domox.dom.nlp.PartOfSpeechType.NNS
                || pos == domox.dom.nlp.PartOfSpeechType.NFP;
    }
}