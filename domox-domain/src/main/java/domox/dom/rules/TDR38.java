package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import java.util.Locale;
import java.util.Set;

import static domox.dom.nlp.TypedDependencyPredicates.*;

/**
 * TDR38 — Business Action Detection (NEW, not included in RULES_EXAMPLES.md)
 *
 * <p>Remedy for the "bottom line" gap of TDR27-TDR37: those rules only match
 * closed, IT-flavoured verb sets (input/output/validate/...), so business-level
 * actions such as "offer", "provide", "sell" or "train" are never detected.</p>
 *
 * <p>This rule fires on any dependency whose governor (A) is a verb from the
 * registered action vocabulary (see {@link ActionCatalog}, extendable via
 * {@code domox.nlp.action-verbs}). When the dependency type exposes the
 * actor (subject/agent) as B, it is recorded as the related candidate.</p>
 */
@RuleBean
@Rule(order = 38)
public class TDR38 extends TypedDependencyRule {

    /** Actors on the customer side; everything else is treated as business/system. */
    private static final Set<String> CUSTOMER_ACTORS = Set.of(
            "user", "customer", "owner", "person", "people", "shopper",
            "client", "buyer", "visitor");

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null || currentTd.getA() == null) {
            return false;
        }
        // Dependencies that expose a verb as the governor (A)
        if (!(isNsubj(currentTd) || isNsubjPass(currentTd) || dobj(currentTd) ||
                iobj(currentTd) || pobj(currentTd) || xcomp(currentTd) ||
                nmodWith(currentTd) || nmodTo(currentTd) || nmodFor(currentTd))) {
            return false;
        }
        return isVerbA(currentTd) && isActionVerbA(currentTd);
    }

    @Override
    @Then
    public void then() {
        String verb = currentTd.getA();
        String actor = currentTd.getB();

        boolean customerAction = actor != null &&
                CUSTOMER_ACTORS.contains(actor.toLowerCase(Locale.ROOT));
        String candidateType = customerAction ? "User_Action" : "System_Actions";
        result = candidateType + ".add(" + verb + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    candidateType,
                    capitalizeFirstLetter(verb),
                    // Actor (subject/agent) when the dependency exposes one
                    actor != null ? "Actor" : null,
                    actor != null ? capitalizeFirstLetter(actor) : null,
                    result);
        }
    }
}
