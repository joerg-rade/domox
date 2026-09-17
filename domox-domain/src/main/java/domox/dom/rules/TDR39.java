package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.TypedDependency;

import static domox.dom.nlp.TypedDependencyPredicates.*;

/**
 * TDR39 — Kind-of/Type-of generalization ("X is a kind/type of Y").
 * <p>
 * Literature basis: Arora et al. (MODELS'16) pattern B5; also identified in
 * Yue et al.'s survey of NLP-based domain model extraction patterns.
 * <p>
 * Matches when the current dependency is {@code nmod:of(TypeKindNoun, ParentNoun)}
 * and the governor of the nmod:of is "kind", "type", or "sort".  The rule then
 * scans the sentence for an {@code nsubj(TypeKindNoun, ChildNoun)} dependency
 * to identify the subclass.  For example:
 * <pre>
 *   "A Premium service is a type of service."
 *   nsubj(type, service)     →  child = "Premium Service" … actually "service"
 *   nmod:of(type, service)   →  parent = "Service"
 * </pre>
 * <p>
 * The rule records: Child --|> Parent.
 */
@RuleBean
@Rule(order = 39)
public class TDR39 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null || currentTd.getSentence() == null) {
            return false;
        }
        // Spec: nmod:of(TypeKindNoun, ParentNoun)
        // currentTd = nmod:of(TypeKindNoun, ParentNoun)
        if (!nmodOf(currentTd)) {
            return false;
        }
        // The dependent B (ParentNoun) must be a noun
        if (!isNounB(currentTd)) {
            return false;
        }
        // The governor A (TypeKindNoun) must contain "kind"/"type"/"sort"
        return isKindTypeOrSortGovernor(currentTd);
    }

    @Override
    @Then
    public void then() {
        // parentNoun = currentTd.getB() (dependent of nmod:of — the parent class)
        // typeKindNoun = currentTd.getA() (governor — "kind"/"type"/"sort")
        String parent = currentTd.getB();
        String typeKindNoun = currentTd.getA();

        // Scan the sentence for nsubj(typeKindNoun, ChildNoun) to find the subclass.
        // The child is the noun that is the subject of the copula with "kind"/"type"/"sort"
        // as the predicate head.
        String child = null;
        for (TypedDependency td : currentTd.getSentence().getTypedDependencies()) {
            if (td == currentTd) continue;
            String gov = td.getA();
            if (gov == null || !gov.equals(typeKindNoun)) continue;
            if (isNsubj(td) && isNounB(td)) {
                child = td.getB();
                break;
            }
        }

        if (child == null) {
            result = "generalization(unknown --|> " + parent + ")";
            // Cannot determine child — skip persisting
            return;
        }

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
}