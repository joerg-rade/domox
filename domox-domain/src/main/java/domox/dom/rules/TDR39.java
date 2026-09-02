package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import static domox.dom.nlp.TypedDependencyPredicates.*;

/**
 * TDR39 — Service Concept Detection (NEW, not included in RULES_EXAMPLES.md)
 *
 * <p>Use cases such as "grooming services", "boarding", "daycare",
 * "adoption", "insurance" or "photography" are services rather than CRUD
 * actions. This rule records them as candidate type "Service" whenever the
 * service term appears as an object or as part of a compound noun.</p>
 */
@RuleBean
@Rule(order = 39)
public class TDR39 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        if (!(dobj(currentTd) || iobj(currentTd) || pobj(currentTd) ||
                isCompound(currentTd) || nmodOf(currentTd) || nmodIn(currentTd) ||
                nmodTo(currentTd) || nmodFor(currentTd) || nmodWith(currentTd))) {
            return false;
        }
        return isServiceNounA(currentTd) || isServiceNounB(currentTd);
    }

    @Override
    @Then
    public void then() {
        String service = isServiceNounB(currentTd) ? currentTd.getB() : currentTd.getA();
        result = "Services.add(" + service + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "Service",
                    capitalizeFirstLetter(service),
                    null,
                    null,
                    result);
        }
    }
}