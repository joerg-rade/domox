package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import static domox.dom.nlp.TypedDependencyPredicates.*;

/**
 * TDR40 — Adjectival classifier generalization ("Adjective Noun" → subclass).
 * <p>
 * Literature basis: Arora et al. (MODELS'16) pattern D3 — adjectival modifiers
 * that function as classifiers rather than simple property descriptors.
 * <p>
 * Matches when the current dependency is {@code amod(Noun, Adjective)} where
 * the adjective is a classifier term (not an evaluative or generic descriptive
 * adjective).  The rule records the noun (without the adjective) as the parent
 * (general concept) and the full "Adjective Noun" pair as the subclass (specialized
 * concept).  For example:
 * <pre>
 *   "linked device"  →  amod(Device, Linked)  →  Device (parent), LinkedDevice (child)
 *   "premium account" → amod(Account, Premium) → Account (parent), PremiumAccount (child)
 * </pre>
 * <p>
 * This rule only fires when:
 * <ul>
 *   <li>The adjective is NOT one of the generic / evaluative stop adjectives</li>
 *   <li>The noun is NOT already involved in a copula pattern (to avoid overlap with TDR38/39)</li>
 *   <li>The noun is NOT part of the "kind"/"type"/"sort" pattern (to avoid overlap with TDR39)</li>
 * </ul>
 */
@RuleBean
@Rule(order = 40)
public class TDR40 extends TypedDependencyRule {

    /**
     * Evaluative/generic adjectives that should NOT trigger
     * adjectival generalization are now loaded from configuration
     * via {@link GeneralizationCatalog} into {@code TypedDependencyPredicates.STOP_ADJECTIVES}.
     */

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null || currentTd.getSentence() == null) {
            return false;
        }
        // Spec: amod(Noun, Adjective)
        // currentTd = amod(Noun, Adjective)
        if (!amod(currentTd)) {
            return false;
        }
        // Adjective must be in dependent B position (getB())
        String adjective = currentTd.getB();
        if (adjective == null || isStopAdjective(adjective)) {
            return false;
        }
        // The governor A (Noun) must be a noun
        if (currentTd.getGovernorPos() == null || !isNounType(currentTd.getGovernorPos())) {
            return false;
        }
        // The dependent (adjective) should be JJ (adjective) or VBG (participle used as adjective)
        if (currentTd.getDependentPos() != null) {
            var depPos = currentTd.getDependentPos();
            if (depPos != domox.dom.nlp.PartOfSpeechType.JJ
                    && depPos != domox.dom.nlp.PartOfSpeechType.VBG) {
                return false;
            }
        }
        // Exclude if the noun is the governor of a cop dependency (already handled by TDR38/39)
        String noun = currentTd.getA();
        for (var td : currentTd.getSentence().getTypedDependencies()) {
            if (td == currentTd) continue;
            String gov = td.getA();
            if (gov == null || !gov.equals(noun)) continue;
            if (cop(td)) {
                return false; // noun is in a copula relation — handled by TDR38/39
            }
        }
        return true;
    }

    @Override
    @Then
    public void then() {
        // noun = currentTd.getA() (governor — the parent concept)
        // adjective = currentTd.getB() (dependent — the classifier)
        String noun = currentTd.getA();
        String adjective = currentTd.getB();
        // child = adjective + noun (e.g. "Linked" + "Device" → "LinkedDevice")
        String child = capitalizeFirstLetter(adjective) + capitalizeFirstLetter(noun);
        String parent = capitalizeFirstLetter(noun);

        result = "generalization(" + child + " --|> " + parent + ")";

        // Phase 1: record the match
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "GeneralizationCdd",
                    child,
                    "GeneralizationParent",
                    parent,
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