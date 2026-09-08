package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import java.util.List;

import static domox.dom.nlp.TypedDependencyPredicates.*;

/**
 * TDR38 — Domain Action Detection (generic, no whitelist needed)
 *
 * <p>Remedy for the "bottom line" gap of TDR27-TDR37: those rules only match
 * closed, IT-flavoured verb sets (input/output/validate/...), so business-level
 * actions such as "offer", "provide", "sell" or "train" are never detected.</p>
 *
 * <p>This rule fires on any dependency whose governor (A) is a verb that is
 * <em>not</em> in any of the technical verb lists configured in
 * {@code domox.nlp.*} (see {@link ActionCatalog#isDomainAction(String)}).
 * When the dependency type exposes the actor (subject/agent) as B, the actor
 * is checked against the configurable {@code domox.nlp.customer-actors} list
 * to determine whether the action is user-initiated or system-initiated.</p>
 */
@RuleBean
@Rule(order = 38)
public class TDR38 extends TypedDependencyRule {

    private final ActionCatalog actionCatalog;
    private final List<String> customerActors;

    public TDR38(ActionCatalog actionCatalog, NlpProperties nlpProperties) {
        this.actionCatalog = actionCatalog;
        this.customerActors = nlpProperties.getCustomerActors() != null
                ? nlpProperties.getCustomerActors()
                : List.of();
    }

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
        // A must be a verb and must NOT be a technical (non-domain) verb
        return isVerbA(currentTd) && actionCatalog.isDomainAction(currentTd.getA());
    }

    @Override
    @Then
    public void then() {
        String verb = currentTd.getA();
        String actor = currentTd.getB();

        boolean customerAction = actor != null &&
                customerActors.stream().anyMatch(a -> a.equalsIgnoreCase(actor));
        String candidateType = customerAction ? "User_Action" : "System_Actions";
        result = candidateType + ".add(" + verb + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    candidateType,
                    verb,
                    // Actor (subject/agent) when the dependency exposes one
                    actor != null ? "Actor" : null,
                    actor != null ? capitalizeFirstLetter(actor) : null,
                    result);
        }
    }
}