package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import static domox.dom.nlp.TypedDependencyPredicates.*;

/**
 * TDR39 — Service Concept Detection (generic, no whitelist needed)
 *
 * <p>Remedy for the "bottom line" gap of TDR27-TDR37: business nouns such as
 * grooming, boarding, daycare, adoption or training are services — they are
 * never captured by the entity/attribute rules (TDR1-TDR13) nor by the
 * action rules (TDR27-TDR37), because there is no "Service" category there.</p>
 *
 * <p>This rule fires on dependencies where B (or A in a compound) matches a
 * configured service noun (see {@code domox.nlp.service-nouns}) appearing as an
 * object, compound or prepositional object — optionally in the context of a
 * domain action verb. Matches are recorded with candidate type {@code Service}.</p>
 */
@RuleBean
@Rule(order = 39)
public class TDR39 extends TypedDependencyRule {

    private final ActionCatalog actionCatalog;

    public TDR39(ActionCatalog actionCatalog) {
        this.actionCatalog = actionCatalog;
    }

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }

        // Dependencies that expose an object / compound / prepositional object:
        // objects (obj/obl/pobj), compounds and prepositional noun modifiers
        boolean structural = dobj(currentTd) || iobj(currentTd) || pobj(currentTd) ||
                isCompound(currentTd) || nmodWith(currentTd) || nmodTo(currentTd) ||
                nmodFor(currentTd) || nmodBy(currentTd) || nmodOf(currentTd);
        if (!structural) {
            return false;
        }

        // One side must be a configured service noun (B, or A for compounds)
        boolean serviceNoun = isServiceNounB(currentTd) || isServiceNounA(currentTd);
        if (!serviceNoun) {
            return false;
        }

        // When the governor exposes a verb, it must be a domain action
        // (i.e. not a technical verb from the domox.nlp.* configuration)
        if (isVerbA(currentTd)) {
            return actionCatalog.isDomainAction(currentTd.getA());
        }
        return true;
    }

    @Override
    @Then
    public void then() {
        // Prefer the dependent (B) as the service noun; fall back to A
        // (compounds: "grooming facility" → compound(facility, grooming) → B=grooming)
        String service = isServiceNounB(currentTd) ? currentTd.getB() : currentTd.getA();
        result = "Service.add(" + service + ")";

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
